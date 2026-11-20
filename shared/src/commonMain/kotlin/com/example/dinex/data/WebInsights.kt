package com.example.dinex

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// ═══════════════════════════════════════════════════════════════════════════
// Cálculos de la web: series para los gráficos, calendario de gastos, simulador
// de ahorro y proyección de metas. Son funciones puras (sin pantalla) y tienen
// pruebas en WebInsightsTest.
// ═══════════════════════════════════════════════════════════════════════════

/** Mes como número correlativo: año × 12 + (mes − 1). Sirve para ordenar y restar meses. */
internal fun Movement.webMonthKey(): Int = year * 12 + month - 1
internal fun Movement.webEpochDay(): Int = epochDaysOf(year, month, day)
internal fun monthKeyOf(date: DinexDate): Int = date.year * 12 + date.month - 1
internal fun monthKeyYear(key: Int): Int = key / 12
internal fun monthKeyMonth(key: Int): Int = key % 12 + 1

internal fun daysInMonthOf(year: Int, month: Int): Int = (28..31).last { validDate(year, month, it) }

/** 0 = lunes … 6 = domingo (el día 0 de la época, 1/1/1970, fue jueves). */
internal fun weekdayIndex(epochDay: Int): Int = ((epochDay % 7) + 7 + 3) % 7

internal val webWeekdaysLong = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
internal val webWeekdaysShort = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

internal data class MonthTotals(val key: Int, val income: Double, val expense: Double) {
    val net get() = income - expense
    /** Parte de los ingresos que no se gastó (0 si no hubo ingresos). */
    val savingRate get() = if (income > 0) ((income - expense) / income).coerceIn(-1.0, 1.0) else 0.0
}

/** Ingresos y gastos de los [count] meses que terminan en [lastKey], del más antiguo al más nuevo. */
internal fun monthlyTotals(movements: List<Movement>, lastKey: Int, count: Int): List<MonthTotals> {
    val byMonth = movements.filter { it.amount.isFinite() && it.amount > 0 }.groupBy { it.webMonthKey() }
    return (lastKey - count + 1..lastKey).map { key ->
        val list = byMonth[key].orEmpty()
        MonthTotals(key, list.filter { it.income }.sumOf { it.amount }, list.filterNot { it.income }.sumOf { it.amount })
    }
}

internal data class FlowPoint(val day: Int, val income: Double, val expense: Double)

/** Ingresos y gastos acumulados día a día en un mes, del día 1 al [upToDay]. */
internal fun cumulativeFlow(movements: List<Movement>, year: Int, month: Int, upToDay: Int): List<FlowPoint> {
    val inMonth = movements.filter { it.year == year && it.month == month && it.amount.isFinite() && it.amount > 0 }
    var income = 0.0
    var expense = 0.0
    return (1..upToDay.coerceAtLeast(1)).map { day ->
        inMonth.filter { it.day == day }.forEach { if (it.income) income += it.amount else expense += it.amount }
        FlowPoint(day, income, expense)
    }
}

internal data class HeatDay(val epochDay: Int, val amount: Double, val future: Boolean)

/**
 * Calendario de gastos de las últimas [weeks] semanas que terminan en la semana de [today].
 * Cada lista interna es una semana de lunes a domingo; los días después de hoy van con future = true.
 */
internal fun spendingCalendar(movements: List<Movement>, today: Int, weeks: Int): List<List<HeatDay>> {
    val firstMonday = today - weekdayIndex(today) - (weeks - 1) * 7
    val totals = movements.filter { !it.income && it.amount.isFinite() && it.amount > 0 }
        .groupBy { it.webEpochDay() }.mapValues { (_, list) -> list.sumOf { it.amount } }
    return (0 until weeks).map { week ->
        (0..6).map { weekday ->
            val day = firstMonday + week * 7 + weekday
            HeatDay(day, totals[day] ?: 0.0, day > today)
        }
    }
}

/** Gasto total por día de la semana (0 = lunes). */
internal fun weekdayTotals(expenses: List<Movement>): DoubleArray {
    val totals = DoubleArray(7)
    expenses.filter { !it.income && it.amount.isFinite() && it.amount > 0 }.forEach { totals[weekdayIndex(it.webEpochDay())] += it.amount }
    return totals
}

/** Cambio en porcentaje frente al valor anterior; null si antes no había nada con qué comparar. */
internal fun percentChange(current: Double, previous: Double): Double? =
    if (previous > 0 && previous.isFinite() && current.isFinite()) (current - previous) / previous * 100 else null

/** Meses que faltan para juntar [remaining] ahorrando [monthly] al mes; null si no se ahorra nada. */
internal fun monthsToGoal(remaining: Double, monthly: Double): Int? = when {
    remaining <= 0 -> 0
    monthly <= 0 || !monthly.isFinite() -> null
    else -> ceil(remaining / monthly - 1e-9).toInt()
}

internal fun addMonths(date: DinexDate, months: Int): DinexDate {
    val key = monthKeyOf(date) + months
    val year = monthKeyYear(key)
    val month = monthKeyMonth(key)
    return DinexDate(year, month, min(date.day, daysInMonthOf(year, month)))
}

/** Ahorro neto promedio por mes (ingresos − gastos) de los meses con movimientos entre los últimos [months]. */
internal fun averageMonthlyNet(movements: List<Movement>, lastKey: Int, months: Int = 3): Double {
    val active = monthlyTotals(movements, lastKey, months).filter { it.income > 0 || it.expense > 0 }
    return if (active.isEmpty()) 0.0 else active.sumOf { it.net } / active.size
}

internal data class CutSimulation(
    /** Lo que se gasta al mes en la categoría (promedio de los meses con gastos en ella). */
    val monthlyAverage: Double,
    val monthlySaving: Double,
    val yearlySaving: Double,
    /** Ahorrando lo mismo cada mes durante 5 años con 5 % anual. */
    val fiveYears: Double,
)

/** "¿Y si gasto [percent] % menos en [category]?" con el promedio de los últimos [months] meses. */
internal fun simulateCut(movements: List<Movement>, category: String, percent: Double, lastKey: Int, months: Int = 3): CutSimulation {
    val keys = (lastKey - months + 1)..lastKey
    val inCategory = movements.filter {
        !it.income && it.amount.isFinite() && it.amount > 0 && it.webMonthKey() in keys && it.budgetCategory().equals(category, ignoreCase = true)
    }
    val activeMonths = inCategory.map { it.webMonthKey() }.distinct().size
    val average = if (activeMonths == 0) 0.0 else inCategory.sumOf { it.amount } / activeMonths
    val monthly = average * percent.coerceIn(0.0, 100.0) / 100
    return CutSimulation(average, monthly, monthly * 12, futureValue(monthly, 5, .05))
}

/** Saldo de una meta mes a mes si se ahorra [monthly] cada mes, hasta llegar al objetivo (máximo [maxMonths]). */
internal fun goalProjection(saved: Double, target: Double, monthly: Double, maxMonths: Int = 120): List<Double> {
    val points = mutableListOf(saved.coerceAtLeast(0.0))
    if (monthly <= 0 || target <= saved) return points
    while (points.last() < target && points.size <= maxMonths) points += min(target, points.last() + monthly)
    return points
}

internal data class TreeRect(val index: Int, val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Mapa de rectángulos "squarified": cada valor ocupa un área proporcional y los
 * rectángulos quedan lo más cuadrados posible. Devuelve un rectángulo por valor positivo.
 */
internal fun treemapLayout(values: List<Double>, width: Float, height: Float): List<TreeRect> {
    val total = values.filter { it > 0 && it.isFinite() }.sum()
    if (total <= 0 || width <= 0f || height <= 0f) return emptyList()
    val area = width.toDouble() * height
    val items = values.withIndex().filter { it.value > 0 && it.value.isFinite() }.sortedByDescending { it.value }
        .map { it.index to it.value / total * area }
    val out = mutableListOf<TreeRect>()
    var x = 0.0
    var y = 0.0
    var w = width.toDouble()
    var h = height.toDouble()
    var i = 0
    while (i < items.size) {
        val side = min(w, h)
        fun worst(row: List<Pair<Int, Double>>): Double {
            val sum = row.sumOf { it.second }
            return max(side * side * row.maxOf { it.second } / (sum * sum), sum * sum / (side * side * row.minOf { it.second }))
        }
        val row = mutableListOf(items[i++])
        while (i < items.size && worst(row + items[i]) <= worst(row)) row += items[i++]
        val sum = row.sumOf { it.second }
        if (w >= h) {
            val columnWidth = if (i == items.size) w else sum / h
            var cy = y
            row.forEach { (index, a) ->
                val ch = a / columnWidth
                out += TreeRect(index, x.toFloat(), cy.toFloat(), columnWidth.toFloat(), ch.toFloat())
                cy += ch
            }
            x += columnWidth; w -= columnWidth
        } else {
            val rowHeight = if (i == items.size) h else sum / w
            var cx = x
            row.forEach { (index, a) ->
                val rw = a / rowHeight
                out += TreeRect(index, cx.toFloat(), y.toFloat(), rw.toFloat(), rowHeight.toFloat())
                cx += rw
            }
            y += rowHeight; h -= rowHeight
        }
    }
    return out
}
