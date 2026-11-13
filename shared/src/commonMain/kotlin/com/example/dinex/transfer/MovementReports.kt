package com.example.dinex

import kotlin.math.abs
import kotlin.math.roundToLong

internal enum class ReportFormat(val label: String, val extension: String, val mime: String) {
    XLSX("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("CSV", "csv", "text/csv"),
    PDF("PDF", "pdf", "application/pdf")
}

internal fun createMovementReport(items: List<Movement>, format: ReportFormat, period: String, owner: String): TransferDocument {
    require(items.size <= MAX_IMPORT_ROWS) { "Exporta como máximo $MAX_IMPORT_ROWS movimientos por reporte." }
    val bytes = when (format) {
        ReportFormat.CSV -> ("﻿" + exportMovementsCsv(items)).encodeToByteArray()
        ReportFormat.XLSX -> exportWorkbook(items, period, owner)
        ReportFormat.PDF -> exportPdfReport(items, period, owner)
    }
    val today = civilDateFromEpochDays()
    val date = "${today.year}-${today.month.toString().padStart(2, '0')}-${today.day.toString().padStart(2, '0')}"
    return TransferDocument("DINEX_movimientos_${date}.${format.extension}", format.mime, bytes)
}

/** Total de gastos agrupado por una clave (categoría o medio de pago). */
internal data class ReportTotal(val label: String, val amount: Double, val count: Int) {
    val average get() = if (count > 0) amount / count else 0.0
}

internal data class ReportMonth(val year: Int, val month: Int, val income: Double, val expense: Double) {
    val balance get() = income - expense
    val label get() = "${reportMonthShort(month).replaceFirstChar { it.uppercase() }} $year"
}

/** Cifras que comparten el PDF y el Excel, para que ambos reportes digan exactamente lo mismo. */
internal class ReportStats(val items: List<Movement>) {
    val income = items.filter { it.income }.sumOf { it.amount }
    val expense = items.filterNot { it.income }.sumOf { it.amount }
    val balance = income - expense
    val incomeCount = items.count { it.income }
    val expenseCount = items.size - incomeCount
    val savingsRate = if (income > 0) balance / income else 0.0
    val averageExpense = if (expenseCount > 0) expense / expenseCount else 0.0
    val largestExpense = items.filterNot { it.income }.maxByOrNull { it.amount }
    val categories = expenseTotals { it.category }
    val methods = expenseTotals { it.method }
    val months = items.groupBy { it.year * 12 + it.month - 1 }.entries.sortedBy { it.key }.map { (_, list) ->
        ReportMonth(list.first().year, list.first().month, list.filter { it.income }.sumOf { it.amount }, list.filterNot { it.income }.sumOf { it.amount })
    }

    // Excel compara textos sin distinguir mayúsculas, así que agrupamos igual para que las fórmulas cuadren.
    private fun expenseTotals(key: (Movement) -> String) = items.filterNot { it.income }
        .groupBy { key(it).lowercase() }
        .map { (_, list) -> ReportTotal(key(list.first()), list.sumOf { it.amount }, list.size) }
        .sortedWith(compareByDescending<ReportTotal> { it.amount }.thenBy { it.label.lowercase() })
}

internal fun reportMonthShort(month: Int): String =
    listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")[(month - 1).coerceIn(0, 11)]

internal fun Movement.reportDate(): String = "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"

/** Número decimal sin notación científica, válido para PDF y para Excel. */
internal fun fixed(value: Double, decimals: Int): String {
    if (!value.isFinite()) return "0"
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return "$sign$scaled"
    return "$sign${scaled / factor}.${(scaled % factor).toString().padStart(decimals, '0')}"
}

/** S/ 1,234.50 con separador de miles. */
internal fun moneyGrouped(value: Double): String {
    val (whole, cents) = fixed(abs(value), 2).split('.')
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    return (if (value <= -0.005) "-" else "") + "S/ $grouped.$cents"
}

/** Días desde 1970-01-01 (inverso de civilDateFromEpochDays). */
internal fun epochDaysOf(year: Int, month: Int, day: Int): Int {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * ((month + 9) % 12) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}
