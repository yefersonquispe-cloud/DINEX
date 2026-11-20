package com.example.dinex

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cálculos que alimentan los gráficos de la web. */
class WebInsightsTest {
    private fun item(id: Int, category: String, amount: Double, day: Int, month: Int, income: Boolean = false) =
        Movement(id, category, if (income) "Ingreso" else category, amount, income, day, "Yape", month = month, year = 2026)

    private val movements = listOf(
        item(1, "Ingreso", 2000.0, 1, 9, income = true),
        item(2, "Comida", 300.0, 5, 9),
        item(3, "Ingreso", 2000.0, 1, 10, income = true),
        item(4, "Comida", 600.0, 3, 10),
        item(5, "Transporte", 100.0, 4, 10),
        item(6, "Ingreso", 2000.0, 1, 11, income = true),
        item(7, "Comida", 150.0, 2, 11),
        item(8, "Comida", 50.0, 2, 11),
        item(9, "Transporte", 40.0, 4, 11),
    )
    private val november = 2026 * 12 + 10

    @Test
    fun monthKeysRoundTrip() {
        assertEquals(november, monthKeyOf(DinexDate(2026, 11, 20)))
        assertEquals(2026, monthKeyYear(november))
        assertEquals(11, monthKeyMonth(november))
        assertEquals(DinexDate(2027, 2, 28), addMonths(DinexDate(2026, 11, 30), 3))
    }

    @Test
    fun weekdaysStartOnMonday() {
        assertEquals(0, weekdayIndex(epochDaysOf(2026, 11, 16))) // lunes
        assertEquals(3, weekdayIndex(epochDaysOf(2026, 11, 19))) // jueves
        assertEquals(6, weekdayIndex(epochDaysOf(2026, 11, 22))) // domingo
    }

    @Test
    fun monthlyTotalsAndSavingRate() {
        val totals = monthlyTotals(movements, november, 3)
        assertEquals(listOf(november - 2, november - 1, november), totals.map { it.key })
        assertEquals(2000.0, totals[1].income)
        assertEquals(700.0, totals[1].expense)
        assertEquals(.65, totals[1].savingRate, 1e-9)
        assertEquals(240.0, totals[2].expense)
    }

    @Test
    fun cumulativeFlowAddsUpDayByDay() {
        val flow = cumulativeFlow(movements, 2026, 11, 5)
        assertEquals(5, flow.size)
        assertEquals(FlowPoint(1, 2000.0, 0.0), flow[0])
        assertEquals(200.0, flow[1].expense)
        assertEquals(240.0, flow[4].expense)
    }

    @Test
    fun calendarHasWholeWeeksEndingToday() {
        val today = epochDaysOf(2026, 11, 4) // miércoles
        val weeks = spendingCalendar(movements, today, 4)
        assertEquals(4, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(0, weekdayIndex(weeks.first().first().epochDay))
        val cell = weeks.flatten().first { it.epochDay == epochDaysOf(2026, 11, 2) }
        assertEquals(200.0, cell.amount)
        assertTrue(weeks.flatten().first { it.epochDay == today + 1 }.future)
    }

    @Test
    fun goalTimeAndProjection() {
        assertEquals(4, monthsToGoal(1000.0, 250.0))
        assertEquals(5, monthsToGoal(1001.0, 250.0))
        assertEquals(0, monthsToGoal(0.0, 100.0))
        assertNull(monthsToGoal(500.0, 0.0))
        val points = goalProjection(100.0, 400.0, 150.0)
        assertEquals(listOf(100.0, 250.0, 400.0), points)
    }

    @Test
    fun simulatorUsesCategoryAverage() {
        // Comida: octubre 600, noviembre 200 → promedio 400; recortar 25 % ahorra 100 al mes.
        val simulation = simulateCut(movements, "Comida", 25.0, november, months = 2)
        assertEquals(400.0, simulation.monthlyAverage, 1e-9)
        assertEquals(100.0, simulation.monthlySaving, 1e-9)
        assertEquals(1200.0, simulation.yearlySaving, 1e-9)
        assertTrue(simulation.fiveYears > 6000.0)
    }

    @Test
    fun percentChangeNeedsAPreviousValue() {
        assertEquals(50.0, percentChange(150.0, 100.0))
        assertNull(percentChange(10.0, 0.0))
    }

    @Test
    fun treemapFillsTheAreaWithoutOverflow() {
        val values = listOf(500.0, 300.0, 120.0, 60.0, 20.0)
        val rects = treemapLayout(values, 400f, 300f)
        assertEquals(values.size, rects.size)
        val area = rects.sumOf { (it.width * it.height).toDouble() }
        assertTrue(abs(area - 400.0 * 300.0) < 1.0, "área total $area")
        rects.forEach {
            assertTrue(it.x >= -0.01f && it.y >= -0.01f && it.x + it.width <= 400.01f && it.y + it.height <= 300.01f, "fuera del área: $it")
            val share = it.width * it.height / (400f * 300f)
            assertEquals(values[it.index] / values.sum(), share.toDouble(), 1e-3)
        }
    }
}
