package com.example.dinex

import kotlin.js.Date
import web.storage.localStorage

internal actual fun readLocalValue(key: String): String? = localStorage.getItem("dinex_db_$key")
internal actual fun writeLocalValue(key: String, value: String?) {
    if (value == null) localStorage.removeItem("dinex_db_$key") else localStorage.setItem("dinex_db_$key", value)
}

internal actual fun loadSavedMovements(): String? = localStorage.getItem("dinex_movements")

internal actual fun loadSavedStreak(): Int? = localStorage.getItem("dinex_streak")?.toIntOrNull()

internal actual fun loadSavedSavings(): Double? = localStorage.getItem("dinex_savings")?.toDoubleOrNull()

internal actual fun loadSavedStreakDay(): Int? = localStorage.getItem("dinex_streak_day")?.toIntOrNull()

internal actual fun currentDayIndex(): Int = ((Date.now() - Date().getTimezoneOffset() * 60_000.0) / 86_400_000.0).toInt()

internal actual fun loadSavedReminders(): String? = localStorage.getItem("dinex_reminders")

internal actual fun loadSavedEmail(): String? = localStorage.getItem("dinex_authenticated_email")

internal actual fun loadSavedAccounts(): String? = localStorage.getItem("dinex_accounts")

internal actual fun loadSavedOnboardingCompleted(): Boolean = localStorage.getItem("dinex_onboarding_completed") == "true"

internal actual fun loadSavedGoalName(): String? = localStorage.getItem("dinex_goal_name")
internal actual fun loadSavedGoalTarget(): Double? = localStorage.getItem("dinex_goal_target")?.toDoubleOrNull()
