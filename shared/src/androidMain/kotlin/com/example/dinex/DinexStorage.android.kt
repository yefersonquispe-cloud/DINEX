package com.example.dinex

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private var dinexDatabase: DinexDatabase? = null

@Synchronized
fun configureDinexStorage(context: Context) {
    if (dinexDatabase != null) return
    val appContext = context.applicationContext
    dinexDatabase = DinexDatabase(appContext)
    migrateLegacyPreferences(appContext)
}

internal actual fun readLocalValue(key: String): String? = checkNotNull(dinexDatabase) { "Configure Dinex storage before use" }.read(key)
internal actual fun writeLocalValue(key: String, value: String?) {
    val database = checkNotNull(dinexDatabase) { "Configure Dinex storage before use" }
    if (value == null) database.delete(key) else database.write(key, value)
}

internal actual fun loadSavedMovements(): String? = dinexDatabase?.read("movements")

internal actual fun loadSavedStreak(): Int? = dinexDatabase?.read("streak")?.toIntOrNull()

internal actual fun loadSavedSavings(): Double? = dinexDatabase?.read("savings")?.toDoubleOrNull()

internal actual fun loadSavedStreakDay(): Int? = dinexDatabase?.read("streak_day")?.toIntOrNull()

internal actual fun currentDayIndex(): Int {
    val now = System.currentTimeMillis()
    return ((now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
}

internal actual fun loadSavedReminders(): String? = dinexDatabase?.read("reminders")

internal actual fun loadSavedEmail(): String? = dinexDatabase?.read("authenticated_email")

internal actual fun loadSavedAccounts(): String? = dinexDatabase?.read("accounts")

internal actual fun loadSavedOnboardingCompleted(): Boolean = dinexDatabase?.read("onboarding_completed") == "true"

internal actual fun loadSavedGoalName(): String? = dinexDatabase?.read("goal_name")
internal actual fun loadSavedGoalTarget(): Double? = dinexDatabase?.read("goal_target")?.toDoubleOrNull()

private class DinexDatabase(context: Context) : SQLiteOpenHelper(context, "dinex.db", null, 1) {
    init { setWriteAheadLoggingEnabled(true) }
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
        check(writableDatabase.insertWithOnConflict("app_state", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "No se pudo guardar la base de datos Dinex"
        }
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
