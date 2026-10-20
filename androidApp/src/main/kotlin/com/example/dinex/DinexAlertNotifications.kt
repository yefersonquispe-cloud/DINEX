package com.example.dinex

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Real Android notifications for Dinex:
 * - a high-priority alert the moment the daily budget is crossed;
 * - a once-a-day nudge (Duolingo style) only when the person has not saved yet.
 */
internal object DinexNotificationCenter {
    const val BUDGET_CHANNEL = "dinex_budget_alerts"
    const val SAVE_CHANNEL = "dinex_save_reminders"
    const val BUDGET_NOTIFICATION_ID = 4210
    const val SAVE_NOTIFICATION_ID = 4211

    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return true
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(BUDGET_CHANNEL, "Presupuesto diario", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Te avisa en cuanto superas tu límite de gasto del día."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(SAVE_CHANNEL, "Recordatorio de ahorro", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Te recuerda ahorrar antes de que termine el día."
            },
        )
    }

    fun post(context: Context, id: Int, channel: String, alert: DinexAlertText) {
        if (!canPost(context)) return
        ensureChannels(context)
        val budget = channel == BUDGET_CHANNEL
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_dinex_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(if (budget) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (budget) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setColor(if (budget) 0xFFB84754.toInt() else 0xFF176B43.toInt())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Registered from MainActivity so common code can post alerts without a Context. */
class AndroidDinexAlerts(context: Context) : DinexAlertSink {
    private val appContext = context.applicationContext

    override fun showBudgetExceeded(spent: Double, limit: Double) {
        DinexNotificationCenter.post(
            appContext,
            DinexNotificationCenter.BUDGET_NOTIFICATION_ID,
            DinexNotificationCenter.BUDGET_CHANNEL,
            dinexBudgetAlert(spent, limit),
        )
    }

    override fun scheduleSaveReminder(enabled: Boolean, hour: Int) {
        DinexSaveReminderScheduler.schedule(appContext, enabled, hour)
        if (!enabled) cancelSaveReminder()
    }

    override fun cancelSaveReminder() {
        DinexNotificationCenter.cancel(appContext, DinexNotificationCenter.SAVE_NOTIFICATION_ID)
    }
}

/**
 * Checks the signed-in profile at reminder time. If the person already saved
 * today, nothing is posted; otherwise the nudge asks them to save before the
 * streak breaks. Periodic work keeps rescheduling itself after reboots.
 */
class DinexSaveReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val context = applicationContext
        return try {
            configureDinexStorage(context)
            val snapshot = readDinexReminderSnapshot()
            if (snapshot.enabled && snapshot.signedIn && !snapshot.savedToday) {
                DinexNotificationCenter.post(
                    context,
                    DinexNotificationCenter.SAVE_NOTIFICATION_ID,
                    DinexNotificationCenter.SAVE_CHANNEL,
                    dinexSaveReminderAlert(snapshot.streak, snapshot.goalName, snapshot.goalSaved, snapshot.goalTarget),
                )
            } else {
                DinexNotificationCenter.cancel(context, DinexNotificationCenter.SAVE_NOTIFICATION_ID)
            }
            Result.success()
        } catch (_: Throwable) {
            // A notification problem must never fail the whole work request.
            Result.success()
        }
    }
}

internal object DinexSaveReminderScheduler {
    private const val UNIQUE_WORK = "dinex_daily_save_reminder"

    fun schedule(context: Context, enabled: Boolean, hour: Int) {
        runCatching {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(UNIQUE_WORK)
                return
            }
            val safeHour = hour.coerceIn(0, 23)
            val request = PeriodicWorkRequestBuilder<DinexSaveReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(nextDelayMillis(safeHour), TimeUnit.MILLISECONDS)
                .addTag(UNIQUE_WORK)
                .build()
            manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.REPLACE, request)
        }
    }

    /** Milliseconds until the next occurrence of [hour]:00 in the device time zone. */
    internal fun nextDelayMillis(hour: Int, now: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (calendar.timeInMillis - now).coerceAtLeast(1_000L)
    }
}
