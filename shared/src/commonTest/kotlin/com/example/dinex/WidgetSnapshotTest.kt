package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetSnapshotTest {
    // 18 de noviembre de 2026, miércoles.
    private val today = epochDaysOf(2026, 11, 18)

    private fun movement(id: Int, amount: Double, day: Int, income: Boolean = false, title: String = "Compra") =
        Movement(id, title, if (income) "Ingreso" else "Comida", amount, income, day, "Yape", month = 11, year = 2026)

    @Test fun showsWhatIsLeftOfTodaysBudget() {
        val snapshot = FinancialData(movements = listOf(movement(1, 18.0, 18)), dailyLimit = 45f).widgetSnapshot(today = today)
        assertEquals("Te quedan S/ 27.00", snapshot.todayStatus)
        assertEquals(40, snapshot.todayPercent)
        assertFalse(snapshot.overBudget)
    }

    @Test fun warnsWhenTheDailyBudgetIsExceeded() {
        val snapshot = FinancialData(movements = listOf(movement(1, 50.0, 18)), dailyLimit = 45f).widgetSnapshot(today = today)
        assertEquals("Te pasaste por S/ 5.00", snapshot.todayStatus)
        assertTrue(snapshot.overBudget)
    }

    @Test fun balanceDiscountsSavingsAndMonthIgnoresOldMovements() {
        val old = movement(1, 500.0, 2, income = true).copy(month = 10)
        val data = FinancialData(movements = listOf(old, movement(2, 100.0, 3, income = true), movement(3, 30.0, 4)), savings = 70.0)
        val snapshot = data.widgetSnapshot(today = today)
        assertEquals("S/ 500.00", snapshot.balance)
        assertEquals("S/ 100.00", snapshot.monthIncome)
        assertEquals("S/ 30.00", snapshot.monthExpense)
    }

    @Test fun privateModeHidesAmountsAndPurchases() {
        val data = FinancialData(movements = listOf(movement(1, 18.0, 18, title = "Farmacia")), goals = listOf(SavingsGoal(1, "Viaje", 100.0, 40.0)))
        val snapshot = data.widgetSnapshot(privateMode = true, today = today)
        assertEquals("S/ ••••", snapshot.balance)
        assertEquals("S/ ••••", snapshot.goals.single().saved)
        assertEquals("Te quedan S/ ••••", snapshot.todayStatus)
        assertTrue(snapshot.recent.isEmpty())
    }

    @Test fun weekEndsTodayWithItsBarsRelativeToTheHighestDay() {
        val data = FinancialData(movements = listOf(movement(1, 40.0, 16), movement(2, 10.0, 18)))
        val week = data.widgetSnapshot(today = today).week
        assertEquals(listOf("J", "V", "S", "D", "L", "M", "M"), week.map { it.letter })
        assertEquals(100, week[4].percent)
        assertEquals(25, week[6].percent)
        assertTrue(week.last().today)
    }

    @Test fun goalsInProgressComeBeforeCompletedOnes() {
        val goals = listOf(SavingsGoal(1, "Lista", 10.0, 10.0), SavingsGoal(2, "Celular", 100.0, 20.0), SavingsGoal(3, "Viaje", 100.0, 80.0), SavingsGoal(4, "Libre", 0.0, 5.0))
        val shown = FinancialData(goals = goals).widgetSnapshot(today = today).goals
        assertEquals(listOf("Viaje", "Celular", "Libre"), shown.map { it.name })
        assertNull(shown.last().percent)
        assertEquals("S/ 80.00 de S/ 100.00", shown.first().saved)
    }

    @Test fun recentMovementsNameTodayAndYesterday() {
        val data = FinancialData(movements = listOf(movement(1, 5.0, 17), movement(2, 9.0, 18, income = true)))
        val recent = data.widgetSnapshot(today = today).recent
        assertEquals("Hoy · Ingreso", recent.first().detail)
        assertEquals("+ S/ 9.00", recent.first().amount)
        assertEquals("Ayer · Comida", recent.last().detail)
    }

    @Test fun unknownWidgetActionsAreIgnored() {
        DinexQuickActions.pending = null
        DinexQuickActions.request("BORRAR_TODO")
        assertNull(DinexQuickActions.pending)
        DinexQuickActions.request("EXPENSE")
        assertEquals(DinexQuickAction.EXPENSE, DinexQuickActions.consume())
        assertNull(DinexQuickActions.consume())
    }
}
