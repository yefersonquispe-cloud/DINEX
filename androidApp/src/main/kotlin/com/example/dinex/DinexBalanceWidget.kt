package com.example.dinex

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

/** Base de los widgets: elige el diseño según el tamaño y lo actualiza con los datos de la cuenta. */
abstract class DinexWidgetProvider : AppWidgetProvider() {
    protected abstract fun build(context: Context, snapshot: DinexWidgetSnapshot, widthDp: Int, heightDp: Int): RemoteViews

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        render(context, manager, intArrayOf(id))

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == DinexWidgets.ACTION_TOGGLE_PRIVACY) {
            configureDinexStorage(context)
            toggleDinexWidgetPrivacy()
            DinexWidgets.updateAll(context)
        }
    }

    internal fun renderAll(context: Context, manager: AppWidgetManager) {
        val ids = manager.getAppWidgetIds(ComponentName(context, this::class.java))
        if (ids.isNotEmpty()) render(context, manager, ids)
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        configureDinexStorage(context)
        val snapshot = readDinexWidgetSnapshot()
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 170)
            val views = if (snapshot.signedIn) build(context, snapshot, width, height) else DinexWidgets.signedOut(context)
            manager.updateAppWidget(id, views)
        }
    }
}

/** Saldo del mes y gasto de hoy, con accesos rápidos; al agrandarlo suma la semana y los movimientos. */
class DinexBalanceWidget : DinexWidgetProvider() {
    override fun build(context: Context, snapshot: DinexWidgetSnapshot, widthDp: Int, heightDp: Int): RemoteViews {
        val layout = when {
            widthDp >= 250 && heightDp >= 320 -> R.layout.dinex_widget_balance_large
            widthDp >= 250 && heightDp >= 150 -> R.layout.dinex_widget_balance
            else -> R.layout.dinex_widget_balance_compact
        }
        return RemoteViews(context.packageName, layout).apply {
            DinexWidgets.open(context, this, android.R.id.background, null)
            DinexWidgets.bindHeader(context, this, snapshot)
            // En el tamaño pequeño, la racha no entra junto al nombre.
            if (widthDp < 220) setViewVisibility(R.id.widget_streak, View.GONE)
            DinexWidgets.bindBudget(this, snapshot)
            setTextViewText(R.id.widget_balance, snapshot.balance)
            setTextViewText(R.id.widget_income, snapshot.monthIncome)
            setTextViewText(R.id.widget_expense, snapshot.monthExpense)
            DinexWidgets.open(context, this, R.id.widget_action_expense, DinexQuickAction.EXPENSE)
            DinexWidgets.open(context, this, R.id.widget_action_income, DinexQuickAction.INCOME)
            DinexWidgets.open(context, this, R.id.widget_action_save, DinexQuickAction.SAVE)
            if (layout == R.layout.dinex_widget_balance_large) bindDetails(context, snapshot, heightDp)
        }
    }

    private fun RemoteViews.bindDetails(context: Context, snapshot: DinexWidgetSnapshot, heightDp: Int) {
        setTextViewText(R.id.widget_pill_income_value, snapshot.monthIncome)
        setTextViewText(R.id.widget_pill_expense_value, snapshot.monthExpense)
        setTextViewText(R.id.widget_pill_balance_value, snapshot.monthBalance)
        setTextColor(R.id.widget_pill_balance_value, if (snapshot.monthBalanceNum >= 0) 0xFF3DD68C.toInt() else 0xFFF28B82.toInt())

        val chartBitmap = renderCashFlowBitmap(snapshot, 600, 240)
        setImageViewBitmap(R.id.widget_cashflow_chart, chartBitmap)

        // Solo se muestran los movimientos que caben sin aplastar el gráfico.
        val rowsThatFit = ((heightDp - DETAILS_BASE_HEIGHT) / RECENT_ROW_HEIGHT).coerceIn(0, DinexWidgets.RecentRows.size)
        val emptyText = if (snapshot.privateMode) R.string.widget_recent_hidden else R.string.widget_recent_none
        setViewVisibility(R.id.widget_recent_heading, if (rowsThatFit > 0) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_recent_empty, context.getString(emptyText))
        setViewVisibility(R.id.widget_recent_empty, if (rowsThatFit > 0 && snapshot.recent.isEmpty()) View.VISIBLE else View.GONE)
        DinexWidgets.RecentRows.forEachIndexed { index, row ->
            val item = snapshot.recent.getOrNull(index)?.takeIf { index < rowsThatFit }
            setViewVisibility(row.root, if (item == null) View.GONE else View.VISIBLE)
            if (item != null) {
                setTextViewText(row.title, item.title)
                setTextViewText(row.detail, item.detail)
                setTextViewText(row.amount, item.amount)
                setTextColor(row.amount, if (item.income) 0xFF3DD68C.toInt() else 0xFFF2F5F3.toInt())
            }
        }
        DinexWidgets.open(context, this, R.id.widget_cashflow_chart, DinexQuickAction.HISTORY)
    }

    private fun renderCashFlowBitmap(snapshot: DinexWidgetSnapshot, width: Int, height: Int): android.graphics.Bitmap {
        val w = if (width > 0) width else 600
        val h = if (height > 0) height else 240
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val days = snapshot.cashFlowDays
        if (days.isEmpty()) return bitmap

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val gridColor = 0x22FFFFFF.toInt()
        val incomeColor = 0xFF3DD68C.toInt()
        val expenseColor = 0xFFF28B82.toInt()
        val lineColor = 0xFF818CF8.toInt()

        val mid = h / 2f
        val reach = mid * 0.85f

        // Línea central
        paint.color = gridColor
        paint.strokeWidth = 2f
        canvas.drawLine(0f, mid, w.toFloat(), mid, paint)

        val n = days.size.coerceAtLeast(1)
        val slot = w.toFloat() / n
        val barWidth = (slot * 0.52f).coerceIn(3f, 24f)

        val maxIn = days.maxOfOrNull { it.income } ?: 1.0
        val maxOut = days.maxOfOrNull { it.expense } ?: 1.0
        val peak = maxOf(maxIn, maxOut).takeIf { it > 0.0 } ?: 1.0

        val maxRun = days.maxOfOrNull { kotlin.math.abs(it.runningBalance) }?.takeIf { it > 0.0 } ?: 1.0

        // Saldo acumulado
        val lastPastIndex = days.indexOfLast { it.dateIso <= snapshot.todayIso }
        if (lastPastIndex >= 1) {
            val path = android.graphics.Path()
            fun getPoint(i: Int): android.graphics.PointF {
                val cx = slot * i + slot / 2f
                val y = mid - (days[i].runningBalance.toFloat() / maxRun.toFloat() * reach * 0.7f)
                return android.graphics.PointF(cx, y)
            }
            val p0 = getPoint(0)
            path.moveTo(p0.x, p0.y)
            for (i in 1..lastPastIndex) {
                val a = getPoint(i - 1)
                val b = getPoint(i)
                val cx = (a.x + b.x) / 2f
                path.cubicTo(cx, a.y, cx, b.y, b.x, b.y)
            }
            val areaPath = android.graphics.Path(path)
            val lastP = getPoint(lastPastIndex)
            areaPath.lineTo(lastP.x, mid)
            areaPath.lineTo(p0.x, mid)
            areaPath.close()

            paint.style = android.graphics.Paint.Style.FILL
            paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, mid, 0x28818CF8.toInt(), 0x05818CF8.toInt(), android.graphics.Shader.TileMode.CLAMP)
            canvas.drawPath(areaPath, paint)
            paint.shader = null

            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = lineColor
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
            canvas.drawPath(path, paint)
            paint.pathEffect = null

            val pSel = getPoint(lastPastIndex.coerceIn(0, days.lastIndex))
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(pSel.x, pSel.y, 9f, paint)
            paint.color = lineColor
            canvas.drawCircle(pSel.x, pSel.y, 5f, paint)
        }

        // Barras
        days.forEachIndexed { i, day ->
            val cx = slot * i + slot / 2f
            val inH = (day.income / peak * reach).toFloat()
            val outH = (day.expense / peak * reach).toFloat()

            if (inH > 0f) {
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = incomeColor
                canvas.drawRoundRect(cx - barWidth / 2f, mid - inH, cx + barWidth / 2f, mid, barWidth / 2f, barWidth / 2f, paint)
            }
            if (outH > 0f) {
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = expenseColor
                canvas.drawRoundRect(cx - barWidth / 2f, mid, cx + barWidth / 2f, mid + outH, barWidth / 2f, barWidth / 2f, paint)
            }
        }

        return bitmap
    }



    private companion object {
        const val DETAILS_BASE_HEIGHT = 260
        const val RECENT_ROW_HEIGHT = 38
    }
}

/** Anillo con lo gastado hoy frente al límite diario. */
class DinexBudgetWidget : DinexWidgetProvider() {
    override fun build(context: Context, snapshot: DinexWidgetSnapshot, widthDp: Int, heightDp: Int) =
        RemoteViews(context.packageName, if (heightDp >= 185) R.layout.dinex_widget_budget else R.layout.dinex_widget_budget_compact).apply {
            DinexWidgets.open(context, this, android.R.id.background, DinexQuickAction.PLAN)
            DinexWidgets.open(context, this, R.id.widget_action_expense, DinexQuickAction.EXPENSE)
            DinexWidgets.bindBudget(this, snapshot, compactToday = true)
            setViewVisibility(R.id.widget_today, if (heightDp >= 150) View.VISIBLE else View.GONE)
            val active = if (snapshot.overBudget) R.id.widget_ring_over else R.id.widget_ring
            setViewVisibility(R.id.widget_ring, if (snapshot.overBudget) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.widget_ring_over, if (snapshot.overBudget) View.VISIBLE else View.GONE)
            setProgressBar(active, 100, snapshot.todayPercent.coerceIn(0, 100), false)
            setTextViewText(R.id.widget_ring_percent, if (snapshot.privateMode) "••" else "${snapshot.todayPercent}%")
        }
}

/** Total ahorrado y las metas más cercanas a cumplirse. */
class DinexGoalsWidget : DinexWidgetProvider() {
    override fun build(context: Context, snapshot: DinexWidgetSnapshot, widthDp: Int, heightDp: Int) =
        RemoteViews(context.packageName, R.layout.dinex_widget_goals).apply {
            DinexWidgets.open(context, this, android.R.id.background, DinexQuickAction.GOALS)
            DinexWidgets.open(context, this, R.id.widget_action_save, DinexQuickAction.SAVE)
            setTextViewText(R.id.widget_savings, snapshot.savings)
            setViewVisibility(R.id.widget_goals_empty, if (snapshot.goals.isEmpty()) View.VISIBLE else View.GONE)
            val visibleRows = if (heightDp >= 190) 3 else 2
            DinexWidgets.GoalRows.forEachIndexed { index, row ->
                val goal = snapshot.goals.getOrNull(index)?.takeIf { index < visibleRows }
                setViewVisibility(row.root, if (goal == null) View.GONE else View.VISIBLE)
                if (goal != null) {
                    setTextViewText(row.name, goal.name)
                    setTextViewText(row.saved, goal.saved)
                    setTextViewText(row.percent, goal.percent?.let { "$it%" } ?: context.getString(R.string.widget_no_target))
                    setProgressBar(row.bar, 100, goal.percent ?: 0, false)
                }
            }
        }
}

internal object DinexWidgets {
    const val ACTION_TOGGLE_PRIVACY = "com.example.dinex.TOGGLE_WIDGET_PRIVACY"
    const val EXTRA_ACTION = "com.example.dinex.WIDGET_ACTION"

    class RecentRow(val root: Int, val title: Int, val detail: Int, val amount: Int)
    class GoalRow(val root: Int, val name: Int, val saved: Int, val percent: Int, val bar: Int)

    val RecentRows = listOf(
        RecentRow(R.id.widget_recent_0, R.id.widget_recent_title_0, R.id.widget_recent_detail_0, R.id.widget_recent_amount_0),
        RecentRow(R.id.widget_recent_1, R.id.widget_recent_title_1, R.id.widget_recent_detail_1, R.id.widget_recent_amount_1),
        RecentRow(R.id.widget_recent_2, R.id.widget_recent_title_2, R.id.widget_recent_detail_2, R.id.widget_recent_amount_2),
    )
    val GoalRows = listOf(
        GoalRow(R.id.widget_goal_0, R.id.widget_goal_name_0, R.id.widget_goal_saved_0, R.id.widget_goal_percent_0, R.id.widget_goal_bar_0),
        GoalRow(R.id.widget_goal_1, R.id.widget_goal_name_1, R.id.widget_goal_saved_1, R.id.widget_goal_percent_1, R.id.widget_goal_bar_1),
        GoalRow(R.id.widget_goal_2, R.id.widget_goal_name_2, R.id.widget_goal_saved_2, R.id.widget_goal_percent_2, R.id.widget_goal_bar_2),
    )

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(DinexBalanceWidget(), DinexBudgetWidget(), DinexGoalsWidget()).forEach { it.renderAll(context, manager) }
    }

    fun signedOut(context: Context) = RemoteViews(context.packageName, R.layout.dinex_widget_signed_out).apply {
        open(context, this, android.R.id.background, null)
    }

    /** Abre la app; con una acción va directo al formulario o a la sección que corresponde. */
    fun open(context: Context, views: RemoteViews, viewId: Int, action: DinexQuickAction?) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        action?.let { intent.putExtra(EXTRA_ACTION, it.name) }
        val pending = PendingIntent.getActivity(context, (action?.ordinal ?: -1) + 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(viewId, pending)
    }

    fun bindHeader(context: Context, views: RemoteViews, snapshot: DinexWidgetSnapshot) = with(views) {
        setViewVisibility(R.id.widget_streak, if (snapshot.streakDays > 0) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_streak, snapshot.streakLabel)
        setImageViewResource(R.id.widget_eye, if (snapshot.privateMode) R.drawable.ic_widget_eye_off else R.drawable.ic_widget_eye)
        val toggle = Intent(context, DinexBalanceWidget::class.java).setAction(ACTION_TOGGLE_PRIVACY)
        setOnClickPendingIntent(R.id.widget_eye, PendingIntent.getBroadcast(context, 100, toggle, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    fun bindBudget(views: RemoteViews, snapshot: DinexWidgetSnapshot, compactToday: Boolean = false) = with(views) {
        val today = if (compactToday) "${snapshot.todaySpent} de ${snapshot.dailyLimit}" else "Hoy ${snapshot.todaySpent} de ${snapshot.dailyLimit}"
        setTextViewText(R.id.widget_today, today)
        setTextViewText(R.id.widget_status, snapshot.todayStatus)
        setTextColor(R.id.widget_status, if (snapshot.overBudget) 0xFFF0705A.toInt() else 0xFF3DD68C.toInt())
        setViewVisibility(R.id.widget_budget_bar, if (snapshot.overBudget) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_budget_bar_over, if (snapshot.overBudget) View.VISIBLE else View.GONE)
        setProgressBar(if (snapshot.overBudget) R.id.widget_budget_bar_over else R.id.widget_budget_bar, 100, snapshot.todayPercent.coerceIn(0, 100), false)
    }
}
