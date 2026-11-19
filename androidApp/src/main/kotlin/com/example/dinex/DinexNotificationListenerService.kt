package com.example.dinex

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

class DinexNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        getSharedPreferences("dinex_notification_status", MODE_PRIVATE).edit().putBoolean("connected", true).apply()
        // Recover financial alerts still visible when access was just enabled or reconnected.
        runCatching { activeNotifications?.takeLast(50)?.forEach(::onNotificationPosted) }
    }

    override fun onDestroy() {
        getSharedPreferences("dinex_notification_status", MODE_PRIVATE).edit().putBoolean("connected", false).apply()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        // Privacidad: los chats y conversaciones se ignoran por completo. Dinex solo revisa
        // avisos de pago (Yape, Plin, bancos), que no son mensajes de conversación.
        if (sbn.notification.category == Notification.CATEGORY_MESSAGE) return
        if (NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification) != null) return
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
        val source = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        val transaction = parseDinexTransactionNotification(source, text) ?: return
        DinexNotificationStore.append(this, source, transaction.title, transaction.amount, transaction.income, text, sbn.postTime)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        getSharedPreferences("dinex_notification_status", MODE_PRIVATE).edit().putBoolean("connected", false).apply()
        runCatching { requestRebind(ComponentName(this, DinexNotificationListenerService::class.java)) }
    }

}
