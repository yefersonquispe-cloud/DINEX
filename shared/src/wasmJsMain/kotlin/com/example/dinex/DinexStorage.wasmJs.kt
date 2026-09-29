package com.example.dinex

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import web.storage.localStorage

internal actual fun loadSavedMovements(): String? = localStorage.getItem("dinex_movements")

internal actual fun saveSavedMovements(data: String) {
    localStorage.setItem("dinex_movements", data)
}

internal actual fun loadSavedStreak(): Int? = localStorage.getItem("dinex_streak")?.toIntOrNull()

internal actual fun saveSavedStreak(days: Int) {
    localStorage.setItem("dinex_streak", days.toString())
}

internal actual fun loadSavedSavings(): Double? = localStorage.getItem("dinex_savings")?.toDoubleOrNull()

internal actual fun saveSavedSavings(amount: Double) {
    localStorage.setItem("dinex_savings", amount.toString())
}

internal actual fun loadSavedStreakDay(): Int? = localStorage.getItem("dinex_streak_day")?.toIntOrNull()

internal actual fun saveSavedStreakDay(day: Int) {
    localStorage.setItem("dinex_streak_day", day.toString())
}

@OptIn(ExperimentalTime::class)
internal actual fun currentDayIndex(): Int = (Clock.System.now().toEpochMilliseconds() / 86_400_000L).toInt()

internal actual fun loadSavedReminders(): String? = localStorage.getItem("dinex_reminders")

internal actual fun saveSavedReminders(data: String) {
    localStorage.setItem("dinex_reminders", data)
}

internal actual fun loadSavedEmail(): String? = localStorage.getItem("dinex_authenticated_email")

internal actual fun saveSavedEmail(email: String?) {
    if (email == null) localStorage.removeItem("dinex_authenticated_email")
    else localStorage.setItem("dinex_authenticated_email", email)
}

internal actual fun loadSavedAccounts(): String? = localStorage.getItem("dinex_accounts")

internal actual fun saveSavedAccounts(data: String) {
    localStorage.setItem("dinex_accounts", data)
}

internal actual fun loadSavedOnboardingCompleted(): Boolean = localStorage.getItem("dinex_onboarding_completed") == "true"

internal actual fun saveSavedOnboardingCompleted(completed: Boolean) {
    localStorage.setItem("dinex_onboarding_completed", completed.toString())
}

internal actual fun loadSavedGoalName(): String? = localStorage.getItem("dinex_goal_name")
internal actual fun saveSavedGoalName(name: String) { localStorage.setItem("dinex_goal_name", name) }
internal actual fun loadSavedGoalTarget(): Double? = localStorage.getItem("dinex_goal_target")?.toDoubleOrNull()
internal actual fun saveSavedGoalTarget(target: Double) { localStorage.setItem("dinex_goal_target", target.toString()) }

