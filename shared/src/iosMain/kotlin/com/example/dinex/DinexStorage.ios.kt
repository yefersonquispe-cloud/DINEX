@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.dinex

import cnames.structs.sqlite3 as SqliteHandle
import cnames.structs.sqlite3_stmt as SqliteStatement
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import sqlite3.SQLITE_OK
import sqlite3.SQLITE_ROW
import sqlite3.sqlite3_close
import sqlite3.sqlite3_column_text
import sqlite3.sqlite3_exec
import sqlite3.sqlite3_finalize
import sqlite3.sqlite3_open
import sqlite3.sqlite3_prepare_v2
import sqlite3.sqlite3_step

private val dinexDatabase by lazy { IosDinexDatabase() }
private val legacyDefaults get() = NSUserDefaults.standardUserDefaults

internal actual fun readLocalValue(key: String): String? = dinexDatabase.read(key)
internal actual fun writeLocalValue(key: String, value: String?) {
    if (value == null) dinexDatabase.delete(key) else dinexDatabase.write(key, value)
}

private fun readString(key: String, legacyKey: String = "dinex_$key"): String? {
    dinexDatabase.read(key)?.let { return it }
    return legacyDefaults.stringForKey(legacyKey)?.also { dinexDatabase.write(key, it) }
}

private fun readInt(key: String, legacyKey: String = "dinex_$key"): Int? {
    dinexDatabase.read(key)?.toIntOrNull()?.let { return it }
    if (legacyDefaults.objectForKey(legacyKey) == null) return null
    return legacyDefaults.integerForKey(legacyKey).toInt().also { dinexDatabase.write(key, it.toString()) }
}

private fun readDouble(key: String, legacyKey: String = "dinex_$key"): Double? {
    dinexDatabase.read(key)?.toDoubleOrNull()?.let { return it }
    if (legacyDefaults.objectForKey(legacyKey) == null) return null
    return legacyDefaults.doubleForKey(legacyKey).also { dinexDatabase.write(key, it.toString()) }
}

internal actual fun loadSavedMovements(): String? = readString("movements")
internal actual fun saveSavedMovements(data: String) = dinexDatabase.write("movements", data)
internal actual fun loadSavedStreak(): Int? = readInt("streak")
internal actual fun saveSavedStreak(days: Int) = dinexDatabase.write("streak", days.toString())
internal actual fun loadSavedSavings(): Double? = readDouble("savings")
internal actual fun saveSavedSavings(amount: Double) = dinexDatabase.write("savings", amount.toString())
internal actual fun loadSavedStreakDay(): Int? = readInt("streak_day")
internal actual fun saveSavedStreakDay(day: Int) = dinexDatabase.write("streak_day", day.toString())
internal actual fun currentDayIndex(): Int {
    val now = NSDate()
    return ((now.timeIntervalSince1970 + NSTimeZone.localTimeZone.secondsFromGMTForDate(now)) / 86_400.0).toInt()
}
internal actual fun loadSavedReminders(): String? = readString("reminders")
internal actual fun saveSavedReminders(data: String) = dinexDatabase.write("reminders", data)
internal actual fun loadSavedEmail(): String? = readString("authenticated_email")
internal actual fun saveSavedEmail(email: String?) {
    legacyDefaults.removeObjectForKey("dinex_authenticated_email")
    if (email == null) dinexDatabase.delete("authenticated_email") else dinexDatabase.write("authenticated_email", email)
}
internal actual fun loadSavedAccounts(): String? = readString("accounts")
internal actual fun saveSavedAccounts(data: String) = dinexDatabase.write("accounts", data)
internal actual fun loadSavedOnboardingCompleted(): Boolean = readString("onboarding_completed") == "true"
internal actual fun saveSavedOnboardingCompleted(completed: Boolean) = dinexDatabase.write("onboarding_completed", completed.toString())
internal actual fun loadSavedGoalName(): String? = readString("goal_name")
internal actual fun saveSavedGoalName(name: String) = dinexDatabase.write("goal_name", name)
internal actual fun loadSavedGoalTarget(): Double? = readDouble("goal_target")
internal actual fun saveSavedGoalTarget(target: Double) = dinexDatabase.write("goal_target", target.toString())

private class IosDinexDatabase {
    private val handle: CPointer<SqliteHandle> = memScoped {
        val pointer = alloc<CPointerVar<SqliteHandle>>()
        val directory = NSHomeDirectory() + "/Documents"
        check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)) {
            "No se pudo preparar el almacenamiento local Dinex"
        }
        val path = directory + "/dinex.db"
        check(sqlite3_open(path, pointer.ptr) == SQLITE_OK) { "No se pudo abrir la base de datos Dinex" }
        checkNotNull(pointer.value)
    }

    init {
        execute("PRAGMA journal_mode=WAL")
        execute("PRAGMA synchronous=NORMAL")
        execute(
            "CREATE TABLE IF NOT EXISTS app_state (" +
                "state_key TEXT PRIMARY KEY NOT NULL," +
                "state_value TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL)",
        )
    }

    fun read(key: String): String? {
        val database = handle
        val sql = "SELECT state_value FROM app_state WHERE state_key = ${quote(key)} LIMIT 1"
        return memScoped {
            val pointer = alloc<CPointerVar<SqliteStatement>>()
            if (sqlite3_prepare_v2(database, sql, -1, pointer.ptr, null) != SQLITE_OK) return@memScoped null
            val statement = pointer.value ?: return@memScoped null
            try {
                if (sqlite3_step(statement) == SQLITE_ROW) {
                    sqlite3_column_text(statement, 0)?.reinterpret<ByteVar>()?.toKString()
                } else null
            } finally {
                sqlite3_finalize(statement)
            }
        }
    }

    fun write(key: String, value: String) {
        val timestamp = NSDate().timeIntervalSince1970.toLong()
        execute(
            "INSERT OR REPLACE INTO app_state(state_key,state_value,updated_at) VALUES(" +
                "${quote(key)},${quote(value)},$timestamp)",
        )
    }

    fun delete(key: String) = execute("DELETE FROM app_state WHERE state_key = ${quote(key)}")

    private fun execute(sql: String) {
        check(sqlite3_exec(handle, sql, null, null, null) == SQLITE_OK) { "No se pudo guardar la base de datos Dinex" }
    }

    private fun quote(value: String): String = "'${value.replace("'", "''")}'"

    @Suppress("unused")
    fun close() {
        sqlite3_close(handle)
    }
}
