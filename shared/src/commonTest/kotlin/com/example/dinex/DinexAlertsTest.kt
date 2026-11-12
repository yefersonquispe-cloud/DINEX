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
    fun saveReminderAlwaysAsksToSaveTodayAndAddsATip() {
        for (day in 20_400..20_420) {
            val text = dinexSaveReminderAlert(0, "", 0.0, 0.0, day)
            assertTrue((text.title + text.body).contains("S/"), "día $day: $text")
            assertTrue(text.body.contains("Consejo: "), "día $day: ${text.body}")
        }
    }

    @Test
    fun saveReminderChangesFromOneDayToTheNext() {
        for (day in 20_400..20_430) {
            val today = dinexSaveReminderAlert(3, "Celular", 40.0, 100.0, day)
            val tomorrow = dinexSaveReminderAlert(3, "Celular", 40.0, 100.0, day + 1)
            assertTrue(today != tomorrow, "el día $day y el siguiente tienen el mismo texto")
        }
        val week = (20_400 until 20_407).map { dinexSaveReminderAlert(0, "", 0.0, 0.0, it).title }.toSet()
        assertTrue(week.size >= 4, "en una semana deberían verse varios títulos: $week")
    }

    @Test
    fun streakReminderAlwaysTalksAboutTheStreak() {
        for (day in 20_400..20_410) {
            val text = dinexSaveReminderAlert(7, "", 0.0, 0.0, day)
            assertTrue(text.body.contains("racha") || text.title.contains("racha") || text.title.contains("medalla"), "día $day: $text")
        }
        val medal = (20_400..20_410).map { dinexSaveReminderAlert(7, "", 0.0, 0.0, it) }.first { it.title.contains("medalla") }
        assertTrue(medal.title.contains("1 día para la medalla Plata"), medal.title)
    }

    @Test
    fun goalReminderShowsHowMuchIsLeft() {
        val texts = (20_400..20_403).map { dinexSaveReminderAlert(0, "Celular", 40.0, 100.0, it) }
        assertTrue(texts.all { it.title.contains("«Celular»") })
        assertTrue(texts.any { it.body.contains("S/ 60.00") })
        assertTrue(texts.any { it.title.contains("40%") })
        assertTrue(texts.any { it.title.contains("12 días") })
    }

    @Test
    fun reminderHourLabelUsesTwelveHourClock() {
        assertEquals("6:00 pm", reminderHourLabel(18))
        assertEquals("8:00 pm", reminderHourLabel(20))
        assertEquals("12:00 am", reminderHourLabel(0))
        assertEquals("12:00 pm", reminderHourLabel(12))
    }
}
