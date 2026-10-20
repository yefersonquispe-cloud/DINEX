package com.example.dinex

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class AlertTestStore : DinexStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

private class RecordingAlertSink : DinexAlertSink {
    val budgets = mutableListOf<Pair<Double, Double>>()
    var scheduled: Pair<Boolean, Int>? = null
    var cancelled = 0

    override fun showBudgetExceeded(spent: Double, limit: Double) {
        budgets += spent to limit
    }

    override fun scheduleSaveReminder(enabled: Boolean, hour: Int) {
        scheduled = enabled to hour
    }

    override fun cancelSaveReminder() {
        cancelled++
    }
}

class DinexAlertsTest {
    private val alerts = RecordingAlertSink()

    /** Installs the fake sink before the ViewModel reads its initial preferences. */
    private fun model(): DinexViewModel {
        DinexAlerts.sink = alerts
        return DinexViewModel(DinexRepository(AlertTestStore(), migrateLegacy = false))
    }

    private fun todayExpense(id: Int, amount: Double) =
        Movement(id, "Consumo $id", "Comida", amount, false, currentDayOfMonth(), "Efectivo")

    private fun account() = UserAccount("Persona de prueba", "alertas@dinex.test", "digest")

    @AfterTest
    fun removeSink() {
        DinexAlerts.sink = null
    }

    @Test
    fun budgetNotificationFiresOnlyWhenTheLimitIsCrossed() {
        val model = model()
        model.createAccount(account())
        model.setInitialBalance(300.0)
        model.setDailyLimit(50f)

        model.saveMovement(todayExpense(10, 20.0))
        assertTrue(alerts.budgets.isEmpty(), "Bajo el límite no debe notificar")

        model.saveMovement(todayExpense(11, 40.0))
        assertEquals(listOf(60.0 to 50.0), alerts.budgets)
        assertNotNull(model.state.value.budgetWarning)

        model.saveMovement(todayExpense(12, 10.0), confirmed = true)
        assertEquals(1, alerts.budgets.size, "Ya sobrepasado el límite, no debe repetir la alerta")
    }

    @Test
    fun budgetNotificationRespectsThePreference() {
        val model = model()
        model.createAccount(account())
        model.setInitialBalance(300.0)
        model.setDailyLimit(50f)
        model.setBudgetAlerts(false)

        model.saveMovement(todayExpense(20, 80.0))

        assertTrue(alerts.budgets.isEmpty())
        assertFalse(model.state.value.budgetAlerts)
        assertNotNull(model.state.value.budgetWarning, "El aviso dentro de la app sigue apareciendo")
    }

    @Test
    fun savingTodayCancelsAPendingReminder() {
        val model = model()
        model.createAccount(account())
        model.setInitialBalance(100.0)

        assertTrue(model.saveSaving(SavingEntry(10.0, "Celular", 200.0), currentDayIndex()))
        assertTrue(alerts.cancelled > 0)
    }

    @Test
    fun reminderPreferencesPersistAndKeepTheHour() {
        val store = AlertTestStore()
        val repository = DinexRepository(store, migrateLegacy = false)
        assertTrue(repository.budgetAlertsEnabled())
        assertTrue(repository.saveReminderEnabled())
        assertEquals(20, repository.saveReminderHour())

        repository.setBudgetAlertsEnabled(false)
        repository.setSaveReminderEnabled(false)
        repository.setSaveReminderHour(21)

        val reopened = DinexRepository(store, migrateLegacy = false)
        assertFalse(reopened.budgetAlertsEnabled())
        assertFalse(reopened.saveReminderEnabled())
        assertEquals(21, reopened.saveReminderHour())

        val model = DinexViewModel(reopened)
        assertFalse(model.state.value.budgetAlerts)
        assertFalse(model.state.value.saveReminderEnabled)
        assertEquals(21, model.state.value.saveReminderHour)
    }

    @Test
    fun budgetCopyExplainsTheOverrun() {
        val text = dinexBudgetAlert(70.0, 50.0)
        assertEquals("Superaste tu presupuesto diario", text.title)
        assertTrue(text.body.contains("S/ 70.00"))
        assertTrue(text.body.contains("S/ 20.00"))
    }

    @Test
    fun saveReminderAlwaysAsksToSaveToday() {
        val fresh = dinexSaveReminderAlert(0, "", 0.0, 0.0)
        assertEquals("Ahorra hoy 💚", fresh.title)
        assertTrue(fresh.body.contains("Ahorra aunque sea S/ 1"))
    }

    @Test
    fun streakReminderWarnsAboutLosingTheStreak() {
        val text = dinexSaveReminderAlert(7, "", 0.0, 0.0)
        assertTrue(text.title.contains("racha de 7 días"))
        assertTrue(text.body.contains("Ahorra hoy"))
    }

    @Test
    fun goalReminderShowsHowMuchIsLeft() {
        val text = dinexSaveReminderAlert(0, "Celular", 40.0, 100.0)
        assertEquals("Ahorra hoy para «Celular»", text.title)
        assertTrue(text.body.contains("S/ 60.00"))
    }

    @Test
    fun reminderHourLabelUsesTwelveHourClock() {
        assertEquals("6:00 pm", reminderHourLabel(18))
        assertEquals("8:00 pm", reminderHourLabel(20))
        assertEquals("12:00 am", reminderHourLabel(0))
        assertEquals("12:00 pm", reminderHourLabel(12))
    }
}
