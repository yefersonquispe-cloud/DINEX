package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryDinexStore : DinexStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
}

class DinexViewModelTest {
    private fun repository(store: DinexStore = MemoryDinexStore()) = DinexRepository(store, migrateLegacy = false)
    private fun account(email: String = "qa@dinex.test") = UserAccount("Persona de prueba", email, "digest")
    private fun expense(id: Int = 1) = Movement(id, "Almuerzo", "Comida", 20.0, false, 2, "Efectivo")

    @Test fun newAccountStartsAtZeroAndOnboardsOnce() {
        val model = DinexViewModel(repository())
        model.createAccount(account())
        assertTrue(model.state.value.initialBalanceRequired)
        assertTrue(model.state.value.data.movements.isEmpty())
        assertEquals(0.0, model.state.value.data.savings)
        model.setInitialBalance(150.0)
        model.setInitialBalance(150.0)
        assertFalse(model.state.value.initialBalanceRequired)
        assertEquals(1, model.state.value.data.movements.size)
        assertEquals(150.0, model.state.value.data.movements.single().amount)
    }

    @Test fun profilesStayIsolatedAndSurviveRecreation() {
        val store = MemoryDinexStore()
        val model = DinexViewModel(repository(store))
        model.createAccount(account())
        model.setInitialBalance(100.0)
        model.saveMovement(expense(model.nextMovementId()))
        model.logout()
        model.createAccount(account("other@dinex.test"))
        assertTrue(model.state.value.data.movements.isEmpty())
        model.logout()
        model.authenticate("qa@dinex.test")
        assertEquals(2, model.state.value.data.movements.size)
        assertEquals(expense(2), DinexViewModel(repository(store)).state.value.data.movements.first())
        model.logout()
        assertNull(DinexViewModel(repository(store)).state.value.email)
    }

    @Test fun importUsesUniqueIdsAndRejectsInvalidAmounts() {
        val model = DinexViewModel(repository())
        model.createAccount(account())
        model.saveMovement(expense(12))
        model.importMovements(listOf(expense(), expense(), expense().copy(amount = Double.NaN)))
        assertEquals(listOf(13, 14, 12), model.state.value.data.movements.map { it.id })
        model.saveMovement(expense().copy(amount = -2.0))
        assertEquals(3, model.state.value.data.movements.size)
    }

    @Test fun savingsCountConsecutiveDaysNotDeposits() {
        val model = DinexViewModel(repository())
        model.createAccount(account())
        model.setInitialBalance(200.0)
        val saving = SavingEntry(10.0, "Celular", 100.0)
        model.saveSaving(saving, today = 100)
        model.saveSaving(saving, today = 100)
        assertEquals(1, model.state.value.data.streak)
        model.saveSaving(saving, today = 101)
        assertEquals(2, model.state.value.data.streak)
        model.saveSaving(saving, today = 104)
        assertEquals(1, model.state.value.data.streak)
        assertEquals(40.0, model.state.value.data.savings)
    }

    @Test fun reachingGoalCelebratesOnceAndInvalidSavingsDoNotPersist() {
        val model = DinexViewModel(repository())
        model.createAccount(account())
        model.setInitialBalance(200.0)
        model.saveSaving(SavingEntry(100.0, "Celular", 100.0), 100)
        assertTrue(model.state.value.goalCelebration)
        model.dismissCelebrations()
        model.saveSaving(SavingEntry(5.0, "Celular", 100.0), 100)
        assertFalse(model.state.value.goalCelebration)
        model.saveSaving(SavingEntry(Double.POSITIVE_INFINITY, "Celular", 100.0), 101)
        assertEquals(105.0, model.state.value.data.savings)
    }

    @Test fun reminderToExpenseIsAtomicAndCannotBeDuplicated() {
        val model = DinexViewModel(repository())
        model.createAccount(account())
        val reminder = PaymentReminder(1, "Internet", "Mañana", 50.0)
        model.saveReminder(reminder)
        model.resolveReminder(reminder, true)
        model.resolveReminder(reminder, true)
        assertTrue(model.state.value.data.reminders.isEmpty())
        assertEquals(1, model.state.value.data.movements.size)
        assertEquals(50.0, model.state.value.data.movements.single().amount)
    }

    @Test fun preferencesAndComplexGoalTextSurviveRestart() {
        val store = MemoryDinexStore()
        val repo = repository(store)
        val data = FinancialData(goalName = "Meta: Perú | viaje\n2027 🎉", savings = 12.5, dailyLimit = 90f)
        repo.saveProfile("qa@dinex.test", data)
        assertEquals(data, repository(store).profile("qa@dinex.test"))
        repo.setReduceMotion(true)
        repo.setLargeText(true)
        assertTrue(repository(store).reduceMotion())
        assertTrue(repository(store).largeText())
    }

    @Test fun corruptedProfileFallsBackSafely() {
        val store = MemoryDinexStore()
        store.put("profile:qa@dinex.test", "999999:incomplete")
        assertEquals(FinancialData(), repository(store).profile("qa@dinex.test"))
    }

    @Test fun savingReservesAvailableMoneyWithoutInflatingExpenses() {
        val store = MemoryDinexStore()
        val model = DinexViewModel(repository(store))
        model.createAccount(account()); model.setInitialBalance(100.0)
        assertTrue(model.saveSaving(SavingEntry(30.0, "Viaje", 100.0)))
        assertEquals(70.0, model.state.value.data.availableBalance())
        assertEquals(0, model.state.value.data.movements.count { !it.income })
        assertFalse(model.saveSaving(SavingEntry(71.0, "Viaje", 100.0)))
        assertEquals(30.0, model.state.value.data.savings)
        assertEquals(70.0, DinexViewModel(repository(store)).state.value.data.availableBalance())
    }

    @Test fun budgetCrossingAlertsAndLaterExpensesNeedConfirmation() {
        val model = DinexViewModel(repository())
        model.createAccount(account())
        val date = civilDateFromEpochDays()
        fun today(id: Int, amount: Double) = expense(id).copy(amount = amount, day = date.day, month = date.month, year = date.year)
        model.saveMovement(today(1, 50.0))
        assertTrue(model.state.value.budgetWarning != null)
        model.dismissBudgetWarning()
        model.saveMovement(today(2, 10.0))
        assertEquals(1, model.state.value.data.movements.size)
        assertTrue(model.state.value.pendingExpense != null)
        model.cancelPendingExpense()
        assertEquals(1, model.state.value.data.movements.size)
        model.saveMovement(today(2, 10.0)); model.confirmPendingExpense()
        assertEquals(2, model.state.value.data.movements.size)
        model.saveMovement(today(3, 100.0).copy(income = true))
        assertNull(model.state.value.pendingExpense)
        assertEquals(3, model.state.value.data.movements.size)
    }

    @Test fun categoryBudgetsAndArtworkStayWithTheirProfile() {
        val store = MemoryDinexStore()
        val model = DinexViewModel(repository(store))
        model.createAccount(account()); model.setInitialBalance(100.0)
        model.saveSaving(SavingEntry(10.0, "Celular", 500.0))
        assertTrue(model.setCategoryBudget("Comida", 150.0))
        model.saveGoalArtwork("someoneelse@dinex.test", "Celular", "wrong")
        assertEquals("", model.state.value.data.goalArtwork)
        model.saveGoalArtwork("qa@dinex.test", "Celular", "image-example")
        val restored = DinexViewModel(repository(store)).state.value.data
        assertEquals(150.0, restored.categoryBudgets["Comida"])
        assertEquals("image-example", restored.goalArtwork)
    }

    @Test fun multipleGoalsAndDeletionPreserveMoneyAndOtherGoals() {
        val store = MemoryDinexStore()
        val model = DinexViewModel(repository(store))
        model.createAccount(account()); model.setInitialBalance(300.0)
        assertTrue(model.saveSaving(SavingEntry(30.0, "Viaje", 100.0)))
        val travel = model.state.value.data.goals.single()
        assertTrue(model.saveSaving(SavingEntry(50.0, "Celular", 50.0)))
        val phone = model.state.value.data.goals.last()
        assertTrue(model.state.value.goalCelebration)
        model.dismissCelebrations()
        assertTrue(model.saveSaving(SavingEntry(20.0, "Viaje", 100.0, travel.id)))
        assertFalse(model.state.value.goalCelebration)
        model.saveGoalArtwork("qa@dinex.test", phone.id, "Celular", "phone-image")
        assertEquals(2, DinexViewModel(repository(store)).state.value.data.goals.size)
        assertEquals(200.0, model.state.value.data.availableBalance())
        assertTrue(model.deleteSavingsGoal(travel.id))
        assertEquals(phone.id, model.state.value.data.goals.single().id)
        assertEquals("phone-image", model.state.value.data.goals.single().artwork)
        assertEquals(250.0, model.state.value.data.availableBalance())
        assertEquals(1, model.state.value.data.movements.size)
        assertFalse(model.deleteSavingsGoal(travel.id))
        assertTrue(model.deleteSavingsGoal(phone.id))
        assertTrue(model.state.value.data.savingGoals().isEmpty())
        assertEquals(300.0, DinexViewModel(repository(store)).state.value.data.availableBalance())
        model.saveSaving(SavingEntry(5.0, "Celular", 50.0))
        model.saveGoalArtwork("qa@dinex.test", phone.id, "Celular", "stale-image")
        assertEquals("", model.state.value.data.goals.single().artwork)
    }

    @Test fun legacyGoalCanBeDeletedWithoutLosingOtherMoney() {
        val store = MemoryDinexStore(); val repo = repository(store)
        val model = DinexViewModel(repo); model.createAccount(account()); model.setInitialBalance(100.0)
        repo.saveProfile("qa@dinex.test", model.state.value.data.copy(savings = 25.0, goalName = "Anterior", goalTarget = 100.0))
        model.refreshProfile()
        assertEquals(75.0, model.state.value.data.availableBalance())
        model.deleteSavingsGoal(1)
        assertEquals(100.0, model.state.value.data.availableBalance())
        assertTrue(model.state.value.data.savingGoals().isEmpty())
    }

    @Test fun notificationAmountsKeepAllDigitsAndSupportPeruvianFormats() {
        assertEquals(2500.0, parseDinexTransactionNotification("BCP", "Pagaste S/ 2500 en una tienda")?.amount)
        assertEquals(2500.50, parseDinexTransactionNotification("Yape", "Recibiste S/ 2,500.50")?.amount)
        assertEquals(2500.50, parseDinexTransactionNotification("Interbank", "Compraste por 2.500,50 soles")?.amount)
        assertEquals(20.50, parseDinexTransactionNotification("Lemon", "Pago exitoso PEN 20,50")?.amount)
        assertTrue(parseDinexTransactionNotification("Yape", "Recibiste S/ 20")?.income == true)
        assertFalse(parseDinexTransactionNotification("BCP", "Pagaste S/ 20")?.income ?: true)
    }

    @Test fun notificationPromotionsAndFailedPaymentsAreNotExpenses() {
        assertNull(parseDinexTransactionNotification("BCP", "Pago rechazado S/ 100"))
        assertNull(parseDinexTransactionNotification("Tienda", "Compra y gana por S/ 50"))
        assertNull(parseDinexTransactionNotification("Banco", "Código de verificación 1234, pagaste S/ 20"))
        assertNull(parseDinexTransactionNotification("Yape", "Hola, ¿cómo estás?"))
        assertNull(parseDinexTransactionNotification("BCP", "Pagaste S/ 0"))
    }
}
