package com.example.dinex

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun loadSavedMovements(): String? =
    NSUserDefaults.standardUserDefaults.stringForKey("dinex_movements")

internal actual fun saveSavedMovements(data: String) {
    NSUserDefaults.standardUserDefaults.setObject(data, forKey = "dinex_movements")
}

internal actual fun loadSavedStreak(): Int? =
    NSUserDefaults.standardUserDefaults.integerForKey("dinex_streak").toInt().takeIf { it > 0 }

internal actual fun saveSavedStreak(days: Int) {
    NSUserDefaults.standardUserDefaults.setInteger(days.toLong(), forKey = "dinex_streak")
}

internal actual fun loadSavedSavings(): Double? =
    NSUserDefaults.standardUserDefaults.objectForKey("dinex_savings")?.let {
        NSUserDefaults.standardUserDefaults.doubleForKey("dinex_savings")
    }

internal actual fun saveSavedSavings(amount: Double) {
    NSUserDefaults.standardUserDefaults.setDouble(amount, forKey = "dinex_savings")
}

internal actual fun loadSavedStreakDay(): Int? =
    NSUserDefaults.standardUserDefaults.objectForKey("dinex_streak_day")?.let {
        NSUserDefaults.standardUserDefaults.integerForKey("dinex_streak_day").toInt()
    }

internal actual fun saveSavedStreakDay(day: Int) {
    NSUserDefaults.standardUserDefaults.setInteger(day.toLong(), forKey = "dinex_streak_day")
}

internal actual fun currentDayIndex(): Int = (NSDate().timeIntervalSince1970 / 86_400.0).toInt()

internal actual fun loadSavedReminders(): String? =
    NSUserDefaults.standardUserDefaults.stringForKey("dinex_reminders")

internal actual fun saveSavedReminders(data: String) {
    NSUserDefaults.standardUserDefaults.setObject(data, forKey = "dinex_reminders")
}

internal actual fun loadSavedEmail(): String? =
    NSUserDefaults.standardUserDefaults.stringForKey("dinex_authenticated_email")

internal actual fun saveSavedEmail(email: String?) {
    if (email == null) NSUserDefaults.standardUserDefaults.removeObjectForKey("dinex_authenticated_email")
    else NSUserDefaults.standardUserDefaults.setObject(email, forKey = "dinex_authenticated_email")
}


internal actual fun loadSavedAccounts(): String? =
    NSUserDefaults.standardUserDefaults.stringForKey("dinex_accounts")

internal actual fun saveSavedAccounts(data: String) {
    NSUserDefaults.standardUserDefaults.setObject(data, forKey = "dinex_accounts")
}

internal actual fun loadSavedOnboardingCompleted(): Boolean =
    NSUserDefaults.standardUserDefaults.stringForKey("dinex_onboarding_completed") == "true"

internal actual fun saveSavedOnboardingCompleted(completed: Boolean) {
    NSUserDefaults.standardUserDefaults.setObject(completed.toString(), forKey = "dinex_onboarding_completed")
}
internal actual fun loadSavedGoalName(): String? = NSUserDefaults.standardUserDefaults.stringForKey("dinex_goal_name")
internal actual fun saveSavedGoalName(name: String) { NSUserDefaults.standardUserDefaults.setObject(name, forKey = "dinex_goal_name") }
internal actual fun loadSavedGoalTarget(): Double? = NSUserDefaults.standardUserDefaults.objectForKey("dinex_goal_target")?.let { NSUserDefaults.standardUserDefaults.doubleForKey("dinex_goal_target") }
internal actual fun saveSavedGoalTarget(target: Double) { NSUserDefaults.standardUserDefaults.setDouble(target, forKey = "dinex_goal_target") }
