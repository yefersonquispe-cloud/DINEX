package com.example.dinex

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** A compact, read-only home-screen view of the current local account. */
class DinexBalanceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) updateAll(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.example.dinex.REFRESH_BALANCE_WIDGET"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DinexBalanceWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            configureDinexStorage(context)
            val snapshot = readDinexWidgetSnapshot()
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.dinex_balance_widget)
                views.setTextViewText(R.id.widget_title, snapshot.title)
                views.setTextViewText(R.id.widget_balance, snapshot.balance)
                views.setTextViewText(R.id.widget_savings, snapshot.savings)
                views.setOnClickPendingIntent(R.id.widget_root, openApp)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
