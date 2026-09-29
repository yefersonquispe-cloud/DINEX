package com.example.dinex

import android.app.Notification
import android.content.ComponentName
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
        val text = buildList {
            add(extras.getCharSequence(Notification.EXTRA_TITLE))
            add(extras.getCharSequence(Notification.EXTRA_TEXT))
            add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            add(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach(::add)
        }.filterNotNull().map { it.toString().trim() }.filter { it.isNotBlank() }.distinct().joinToString(" · ")
        if (text.isBlank()) return
        val normalized = text.lowercase(Locale("es", "PE"))
        val transactionWords = listOf(
            "gastaste", "compraste", "pagaste", "pago realizado", "pago exitoso", "compra aprobada",
            "compra", "consumo", "retiro", "cargo", "cobro", "operación realizada", "operacion realizada",
            "transferencia enviada", "transferiste", "enviaste", "enviado", "débito", "debito",
            "recibiste", "recibido", "abono", "depósito", "deposito", "te enviaron", "te depositaron",
        )
        if (transactionWords.none(normalized::contains)) return
        val match = amountPattern.matcher(text)
        if (!match.find()) return
        val rawAmount = (match.group(1) ?: match.group(2)) ?: return
        val amount = parseAmount(rawAmount) ?: return
        val income = listOf(
            "recibiste", "recibido", "abono", "ingreso", "depósito", "deposito",
            "te enviaron", "te depositaron", "transferencia recibida",
        ).any(normalized::contains)
        val source = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        val title = text.substringBefore("·").trim().ifBlank { "Movimiento $source" }
        DinexNotificationStore.append(this, source, title, amount, income, text, sbn.postTime)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        requestRebind(ComponentName(this, DinexNotificationListenerService::class.java))
    }

    private fun parseAmount(raw: String): Double? {
        val value = raw.replace(" ", "")
        val normalized = when {
            value.contains(',') && value.contains('.') && value.lastIndexOf(',') > value.lastIndexOf('.') -> value.replace(".", "").replace(',', '.')
            value.contains(',') && value.contains('.') -> value.replace(",", "")
            value.count { it == ',' } == 1 && value.substringAfter(',').length <= 2 -> value.replace(',', '.')
            value.count { it == '.' } == 1 && value.substringAfter('.').length <= 2 -> value
            else -> value.replace(",", "").replace(".", "")
        }
        return normalized.toDoubleOrNull()
    }
}
