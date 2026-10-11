package com.example.dinex

import androidx.compose.runtime.Composable

internal const val MAX_TRANSFER_BYTES = 20 * 1024 * 1024
internal const val MAX_IMPORT_ROWS = 20_000

internal data class TransferFile(val name: String, val bytes: ByteArray)
internal data class TransferDocument(val name: String, val mimeType: String, val bytes: ByteArray)
internal interface FileTransfer {
    fun chooseImport()
    fun save(document: TransferDocument)
}

@Composable
internal expect fun rememberFileTransfer(
    onImport: (TransferFile?) -> Unit,
    onSaved: (Boolean) -> Unit,
    onError: (String) -> Unit,
): FileTransfer

internal data class ImportReview(
    val movements: List<Movement>,
    val rejectedRows: Int = 0,
    val warnings: List<String> = emptyList(),
)

internal fun Movement.importIdentity(): String =
    listOf(dateIso(), format2(amount), income.toString(), title.trim().lowercase(), method.trim().lowercase()).joinToString("\u001f")

internal fun Movement.dateIso(): String =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

internal fun Movement.isInCurrentMonth(): Boolean = civilDateFromEpochDays().let { month == it.month && year == it.year }
internal fun Movement.isToday(): Boolean = isInCurrentMonth() && day == currentDayOfMonth()
internal fun Movement.dateLabel(): String = "$day ${currentMonthShort(month).lowercase()} $year"

internal fun movementChartCategories(items: List<Movement>): List<CategoryInfo> = categories +
    items.filterNot { it.income }.map { it.category }.distinct().filter { name -> categories.none { it.name == name } }
        .map { name -> CategoryInfo(name, "OT", Gray, 0.0) }

internal fun validDate(year: Int, month: Int, day: Int): Boolean {
    if (year !in 1900..2200 || month !in 1..12) return false
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    return day in 1..when (month) { 2 -> if (leap) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
}

internal enum class ReportPeriod(val label: String) {
    WEEK("Últimos 7 días"), MONTH("Este mes"), QUARTER("Últimos 3 meses"), YEAR("Este año"), CUSTOM("Elegir fechas"), ALL("Todo el historial")
}

internal fun reportMovements(items: List<Movement>, period: ReportPeriod, from: String = "", to: String = ""): List<Movement> {
    val today = civilDateFromEpochDays()
    val currentMonth = today.year * 12 + today.month - 1
    return items.filter {
        when (period) {
            ReportPeriod.WEEK -> it.dateIso() in dateIso(civilDateFromEpochDays(currentDayIndex() - 6))..dateIso(today)
            ReportPeriod.MONTH -> it.year == today.year && it.month == today.month
            ReportPeriod.QUARTER -> it.year * 12 + it.month - 1 in currentMonth - 2..currentMonth
            ReportPeriod.YEAR -> it.year == today.year
            ReportPeriod.CUSTOM -> it.dateIso() in from..to
            ReportPeriod.ALL -> true
        }
    }.sortedWith(compareBy<Movement> { it.dateIso() }.thenBy { it.id })
}

internal fun dateIso(date: DinexDate): String = "${date.year}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
