package com.example.dinex

import android.content.Context

/** Persistent SQLite inbox. Opening a preview never consumes transactions. */
object DinexNotificationStore {
    private const val KEY = "notification_inbox"
    private const val SEEN = "notification_seen"

    @Synchronized
    fun append(context: Context, source: String, title: String, amount: Double, income: Boolean, raw: String, postedAt: Long = System.currentTimeMillis()) {
        if (!amount.isFinite() || amount <= 0) return
        configureDinexStorage(context)
        fun clean(text: String) = text.replace("\u001F", " ").replace("\n", " ").take(500)
        val signature = (source + "|" + amount + "|" + income + "|" + clean(raw) + "|" + postedAt / 60_000L).hashCode().toString()
        val seen = readLocalValue(SEEN).orEmpty().lines()
        if (signature in seen) return
        val current = readLocalValue(KEY).orEmpty().lines().filter { it.isNotBlank() }.toMutableList()
        if (current.any { it.substringBefore("\u001F") == signature }) return
        current.add(listOf(signature, clean(source), clean(title), amount.toString(), income.toString(), clean(raw), postedAt.toString()).joinToString("\u001F"))
        writeLocalValue(KEY, current.takeLast(500).joinToString("\n"))
    }

    @Synchronized
    fun drain(context: Context): List<CapturedTransaction> {
        configureDinexStorage(context)
        migrateLegacyInbox(context)
        return readLocalValue(KEY).orEmpty().lineSequence().mapNotNull { row ->
            val fields = row.split("\u001F")
            if (fields.size < 7) null else {
                val amount = fields[3].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
                val timestamp = fields[6].toLongOrNull() ?: 0L
                val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
                val date = if (timestamp > 0) DinexDate(calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1, calendar.get(java.util.Calendar.DAY_OF_MONTH)) else null
                timestamp to CapturedTransaction(fields[1], fields[2], amount, fields[4].toBoolean(), fields[5], fields[0], date)
            }
        }.sortedByDescending { it.first }.map { it.second }.toList()
    }

    @Synchronized
    fun acknowledge(context: Context, items: List<CapturedTransaction>) {
        configureDinexStorage(context)
        val ids = items.map { it.captureId }.filter { it.isNotBlank() }.toSet()
        val current = readLocalValue(KEY).orEmpty().lines().filter { it.isNotBlank() }
        writeLocalValue(KEY, current.filterNot { it.substringBefore("\u001F") in ids }.joinToString("\n"))
        val seen = readLocalValue(SEEN).orEmpty().lines().filter { it.isNotBlank() }
        writeLocalValue(SEEN, (seen + ids).distinct().takeLast(1000).joinToString("\n"))
    }

    private fun migrateLegacyInbox(context: Context) {
        if (readLocalValue("notification_migration") != null) return
        val old = context.getSharedPreferences("dinex_notification_capture", Context.MODE_PRIVATE).getStringSet("items", emptySet()).orEmpty()
        val current = readLocalValue(KEY).orEmpty().lines().filter { it.isNotBlank() }
        writeLocalValue(KEY, (current + old).distinct().takeLast(500).joinToString("\n"))
        writeLocalValue("notification_migration", "complete")
    }
}
