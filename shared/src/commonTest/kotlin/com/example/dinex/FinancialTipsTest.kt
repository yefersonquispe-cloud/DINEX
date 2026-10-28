package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinancialTipsTest {
    private val today = currentDayOfMonth()
    private fun income(amount: Double) = Movement(1, "Sueldo", "Ingreso", amount, true, today, "Yape")
    private fun expense(id: Int, category: String, amount: Double) = Movement(id, "Gasto $id", category, amount, false, today, "Yape")

    @Test
    fun noIncomeAsksToRegisterIt() {
        val tips = personalTips(FinancialData(movements = listOf(expense(2, "Comida", 20.0))))
        assertTrue(tips.any { it.id == "no-income" && it.action == TipAction.AddMovement })
    }

    @Test
    fun spendingMoreThanIncomeIsTheMostUrgentTip() {
        val data = FinancialData(movements = listOf(income(100.0), expense(2, "Comida", 90.0), expense(3, "Transporte", 40.0)))
        val tips = personalTips(data)
        assertEquals(3, tips.first().priority)
        assertTrue(tips.any { it.id == "overspent" && "S/ 30.00" in it.body })
    }

    @Test
    fun manySmallExpensesAreCalledOut() {
        val small = (2..7).map { expense(it, "Comida", 4.0) }
        val tips = personalTips(FinancialData(movements = listOf(income(2000.0)) + small))
        assertTrue(tips.any { it.id == "small-expenses" && "6 compras" in it.title })
    }

    @Test
    fun healthScoreStaysWithinRangeAndRewardsSaving() {
        val careless = financialHealth(FinancialData(movements = listOf(income(100.0), expense(2, "Comida", 150.0))))
        val careful = financialHealth(
            FinancialData(movements = listOf(income(1000.0), expense(2, "Comida", 20.0)), savings = 200.0, streak = 7, streakDay = currentDayIndex()),
        )
        assertTrue(careless.score in 0..100 && careful.score in 0..100)
        assertTrue(careful.score > careless.score)
        assertEquals(100, careful.factors.sumOf { it.max })
    }

    @Test
    fun futureValueAddsCompoundInterest() {
        assertEquals(1200.0, futureValue(100.0, 1, 0.0), 1e-9)
        val withInterest = futureValue(100.0, 1, 0.12)
        assertEquals(1268.25, withInterest, 0.01)
    }

    @Test
    fun tipOfTheDayRotatesAndWrapsAround() {
        assertEquals(dailyTips[0], tipOfTheDay(dayIndex = 0))
        assertEquals(dailyTips[1], tipOfTheDay(dayIndex = 0, offset = 1))
        assertEquals(dailyTips[0], tipOfTheDay(dayIndex = dailyTips.size))
        assertEquals(dailyTips.last(), tipOfTheDay(dayIndex = 0, offset = -1))
    }
}
