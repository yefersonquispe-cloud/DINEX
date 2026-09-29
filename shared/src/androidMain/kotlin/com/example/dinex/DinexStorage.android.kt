package com.example.dinex

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private var dinexDatabase: DinexDatabase? = null

fun configureDinexStorage(context: Context) {
    val appContext = context.applicationContext
    dinexDatabase = DinexDatabase(appContext)
    migrateLegacyPreferences(appContext)
}

internal actual fun loadSavedMovements(): String? = dinexDatabase?.read("movements")

internal actual fun saveSavedMovements(data: String) {
    dinexDatabase?.write("movements", data)
}

internal actual fun loadSavedStreak(): Int? = dinexDatabase?.read("streak")?.toIntOrNull()

internal actual fun saveSavedStreak(days: Int) {
    dinexDatabase?.write("streak", days.toString())
}

internal actual fun loadSavedSavings(): Double? = dinexDatabase?.read("savings")?.toDoubleOrNull()

internal actual fun saveSavedSavings(amount: Double) {
    dinexDatabase?.write("savings", amount.toString())
}

internal actual fun loadSavedStreakDay(): Int? = dinexDatabase?.read("streak_day")?.toIntOrNull()

internal actual fun saveSavedStreakDay(day: Int) {
    dinexDatabase?.write("streak_day", day.toString())
}

internal actual fun currentDayIndex(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

internal actual fun loadSavedReminders(): String? = dinexDatabase?.read("reminders")

internal actual fun saveSavedReminders(data: String) {
    dinexDatabase?.write("reminders", data)
}

internal actual fun loadSavedEmail(): String? = dinexDatabase?.read("authenticated_email")

internal actual fun saveSavedEmail(email: String?) {
    if (email == null) dinexDatabase?.delete("authenticated_email")
    else dinexDatabase?.write("authenticated_email", email)
}

internal actual fun loadSavedAccounts(): String? = dinexDatabase?.read("accounts")

internal actual fun saveSavedAccounts(data: String) {
    dinexDatabase?.write("accounts", data)
}

internal actual fun loadSavedOnboardingCompleted(): Boolean = dinexDatabase?.read("onboarding_completed") == "true"

internal actual fun saveSavedOnboardingCompleted(completed: Boolean) {
    dinexDatabase?.write("onboarding_completed", completed.toString())
}
internal actual fun loadSavedGoalName(): String? = dinexDatabase?.read("goal_name")
internal actual fun saveSavedGoalName(name: String) { dinexDatabase?.write("goal_name", name) }
internal actual fun loadSavedGoalTarget(): Double? = dinexDatabase?.read("goal_target")?.toDoubleOrNull()
internal actual fun saveSavedGoalTarget(target: Double) { dinexDatabase?.write("goal_target", target.toString()) }

private class DinexDatabase(context: Context) : SQLiteOpenHelper(context, "dinex.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE app_state (state_key TEXT PRIMARY KEY NOT NULL, state_value TEXT NOT NULL, updated_at INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun read(key: String): String? = readableDatabase.query(
        "app_state",
        arrayOf("state_value"),
        "state_key = ?",
        arrayOf(key),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun write(key: String, value: String) {
        val values = ContentValues().apply {
            put("state_key", key)
            put("state_value", value)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("app_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(key: String) {
        writableDatabase.delete("app_state", "state_key = ?", arrayOf(key))
    }
}

private fun migrateLegacyPreferences(context: Context) {
    val database = dinexDatabase ?: return
    val preferences = context.getSharedPreferences("dinex", Context.MODE_PRIVATE)
    if (database.read("movements") == null) {
        preferences.getString("movements", null)?.let { database.write("movements", it) }
    }
    if (database.read("streak") == null && preferences.contains("streak")) {
        database.write("streak", preferences.getInt("streak", 6).toString())
    }
}
