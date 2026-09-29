package com.example.dinex

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Locale
import java.util.regex.Pattern

class DinexNotificationListenerService : NotificationListenerService() {
    private val amountPattern = Pattern.compile(
        "(?:(?:S/|PEN|soles?)\\s*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)|([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:S/|PEN|soles?))",
        Pattern.CASE_INSENSITIVE,
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val text = listOf(extras.getCharSequence(Notification.EXTRA_TITLE), extras.getCharSequence(Notification.EXTRA_TEXT), extras.getCharSequence(Notification.EXTRA_BIG_TEXT)).filterNotNull().joinToString(" · ")
        val normalized = text.lowercase(Locale("es", "PE"))
        val transactionWords = listOf(
            "gastaste", "compraste", "pagaste", "pago", "compra", "consumo", "retiro",
            "cargo", "cobro", "transferencia enviada", "transferiste", "enviado", "débito", "debito",
            "recibiste", "abono", "depósito", "deposito", "te enviaron",
        )
        if (transactionWords.none(normalized::contains)) return
        val match = amountPattern.matcher(text)
        if (!match.find()) return
        val rawAmount = (match.group(1) ?: match.group(2)) ?: return
        val amount = parseAmount(rawAmount) ?: return
        val income = listOf("recibiste", "abono", "ingreso", "depósito", "deposito", "te enviaron").any(normalized::contains)
        val source = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        val title = text.substringBefore("·").trim().ifBlank { "Movimiento $source" }
        DinexNotificationStore.append(this, source, title, amount, income, text)
    }

    private fun parseAmount(raw: String): Double? {
        val value = raw.replace(" ", "")
        val normalized = when {
            value.contains(',') && value.contains('.') -> value.replace(".", "").replace(',', '.')
            value.count { it == ',' } == 1 && value.substringAfter(',').length <= 2 -> value.replace(',', '.')
            value.count { it == '.' } == 1 && value.substringAfter('.').length <= 2 -> value
            else -> value.replace(",", "").replace(".", "")
        }
        return normalized.toDoubleOrNull()
    }
}
