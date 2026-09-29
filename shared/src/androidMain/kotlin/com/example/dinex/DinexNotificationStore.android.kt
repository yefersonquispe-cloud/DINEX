package com.example.dinex

import android.content.Context

object DinexNotificationStore {
    private const val PREFS = "dinex_notification_capture"
    private const val KEY = "items"

    fun append(
        context: Context,
        source: String,
        title: String,
        amount: Double,
        income: Boolean,
        raw: String,
        postedAt: Long = System.currentTimeMillis(),
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cleanRaw = raw.replace("\u001F", " ").take(500)
        // Algunas apps bancarias actualizan la misma notificación varias veces.
        // Una firma por minuto evita duplicados sin perder compras posteriores.
        val minuteBucket = postedAt / 60_000L
        val signature = "$source|$title|$amount|$income|$cleanRaw|$minuteBucket".hashCode().toString()
        val safe = listOf(signature, source, title, amount.toString(), income.toString(), cleanRaw, postedAt.toString()).joinToString("\u001F")
        val current = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        current.removeAll { it.substringBefore("\u001F") == signature }
        current.add(safe)
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun drain(context: Context): List<CapturedTransaction> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = prefs.getStringSet(KEY, emptySet()).orEmpty().toList().sortedByDescending {
            it.split("\u001F").getOrNull(6)?.toLongOrNull() ?: 0L
        }
        prefs.edit().remove(KEY).apply()
        return values.mapNotNull { row ->
            val f = row.split("\u001F")
            if (f.size < 6) null else CapturedTransaction(f[1], f[2], f[3].toDoubleOrNull() ?: return@mapNotNull null, f[4].toBoolean(), f[5])
        }
    }
}
