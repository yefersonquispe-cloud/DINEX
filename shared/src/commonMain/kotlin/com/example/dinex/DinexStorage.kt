package com.example.dinex

internal expect fun loadSavedMovements(): String?

internal expect fun saveSavedMovements(data: String)

internal expect fun loadSavedStreak(): Int?

internal expect fun saveSavedStreak(days: Int)

internal expect fun loadSavedSavings(): Double?

internal expect fun saveSavedSavings(amount: Double)

internal expect fun loadSavedStreakDay(): Int?

internal expect fun saveSavedStreakDay(day: Int)

internal expect fun currentDayIndex(): Int

internal expect fun loadSavedReminders(): String?

internal expect fun saveSavedReminders(data: String)

internal expect fun loadSavedEmail(): String?

internal expect fun saveSavedEmail(email: String?)

internal expect fun loadSavedAccounts(): String?

internal expect fun saveSavedAccounts(data: String)

internal expect fun loadSavedOnboardingCompleted(): Boolean

internal expect fun saveSavedOnboardingCompleted(completed: Boolean)

internal expect fun loadSavedGoalName(): String?
internal expect fun saveSavedGoalName(name: String)
internal expect fun loadSavedGoalTarget(): Double?
internal expect fun saveSavedGoalTarget(target: Double)

data class DinexDate(val year: Int, val month: Int, val day: Int)

internal fun civilDateFromEpochDays(epochDays: Int = currentDayIndex()): DinexDate {
    val days = epochDays.toLong() + 719468L
    val era = if (days >= 0) days / 146097L else (days - 146096L) / 146097L
    val doe = (days - era * 146097L).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = (y + if (m <= 2) 1 else 0).toInt()
    return DinexDate(year, m, d)
}

internal fun currentDayOfMonth(): Int = civilDateFromEpochDays().day

internal fun currentMonthShort(month: Int = civilDateFromEpochDays().month): String = when (month) {
    1 -> "ENE"; 2 -> "FEB"; 3 -> "MAR"; 4 -> "ABR"; 5 -> "MAY"; 6 -> "JUN"
    7 -> "JUL"; 8 -> "AGO"; 9 -> "SET"; 10 -> "OCT"; 11 -> "NOV"; else -> "DIC"
}

internal fun currentMonthName(month: Int = civilDateFromEpochDays().month): String = when (month) {
    1 -> "Enero"; 2 -> "Febrero"; 3 -> "Marzo"; 4 -> "Abril"; 5 -> "Mayo"; 6 -> "Junio"
    7 -> "Julio"; 8 -> "Agosto"; 9 -> "Septiembre"; 10 -> "Octubre"; 11 -> "Noviembre"; else -> "Diciembre"
}

internal fun currentMonthYearLabel(): String {
    val date = civilDateFromEpochDays()
    return "${currentMonthName(date.month)} ${date.year}"
}

