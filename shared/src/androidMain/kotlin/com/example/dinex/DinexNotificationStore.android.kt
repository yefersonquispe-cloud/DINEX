package com.example.dinex

import android.content.Context
import java.util.UUID

object DinexNotificationStore {
    private const val PREFS = "dinex_notification_capture"
    private const val KEY = "items"

    fun append(context: Context, source: String, title: String, amount: Double, income: Boolean, raw: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val safe = listOf(UUID.randomUUID().toString(), source, title, amount.toString(), income.toString(), raw.take(240)).joinToString("\u001F")
        val current = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        current.add(safe)
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun drain(context: Context): List<CapturedTransaction> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = prefs.getStringSet(KEY, emptySet()).orEmpty().toList()
        prefs.edit().remove(KEY).apply()
        return values.mapNotNull { row ->
            val f = row.split("\u001F")
            if (f.size < 6) null else CapturedTransaction(f[1], f[2], f[3].toDoubleOrNull() ?: return@mapNotNull null, f[4].toBoolean(), f[5])
        }
    }
}
