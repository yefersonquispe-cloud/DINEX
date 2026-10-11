package com.example.dinex

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Small, draggable, accessible overlay. It never covers the entire display. */
internal class DinexVoiceOverlay(
    private val context: Context,
    private val onListen: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var status: TextView? = null
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private fun params(width: Int) = WindowManager.LayoutParams(
        width, WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }
    private val bubbleParams = params(dp(60)).apply {
        height = dp(60)
        x = context.resources.displayMetrics.widthPixels - dp(76)
        y = dp(240)
    }

    fun show() {
        if (bubble != null || !Settings.canDrawOverlays(context)) return
        val view = TextView(context).apply {
            text = "D"
            textSize = 24f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(134, 255, 211))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(24, 51, 46), Color.rgb(12, 20, 27))).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(2), Color.rgb(46, 230, 168))
            }
            elevation = dp(8).toFloat()
            contentDescription = "Dinex por voz. Toca para hablar. Mantén presionado para ver opciones."
            setOnClickListener { showPanel("Te escucho…"); onListen() }
            setOnLongClickListener { showPanel("Di Oye Dinex o pulsa Hablar."); true }
        }
        attachDrag(view)
        runCatching { manager.addView(view, bubbleParams); bubble = view }
    }

    fun showPanel(message: String) {
        show()
        if (bubble == null) return
        if (panel != null) { status?.text = message; status?.announceForAccessibility(message); return }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 28, 36)); cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(43, 68, 68))
            }
            elevation = dp(12).toFloat()
        }
        layout.addView(TextView(context).apply {
            text = "Dinex te acompaña"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            accessibilityHeadingCompat()
        })
        status = TextView(context).apply {
            text = message; textSize = 14f; setTextColor(Color.rgb(170, 205, 193))
            setPadding(0, dp(10), 0, dp(12))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        layout.addView(status)
        fun action(label: String, callback: () -> Unit) {
            layout.addView(Button(context).apply {
                text = label; textSize = 13f; isAllCaps = false
                minHeight = dp(48); setTextColor(Color.rgb(134, 255, 211))
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(29, 45, 52))
                setOnClickListener { callback() }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        }
        action("Hablar") { status?.text = "Te escucho…"; onListen() }
        action("Abrir Dinex") { hidePanel(); onOpenApp() }
        action("Minimizar") { hidePanel() }
        action("Desactivar voz y burbuja") { onStop() }
        val screenWidth = context.resources.displayMetrics.widthPixels
        val width = minOf(dp(296), screenWidth - dp(24))
        val panelParams = params(width).apply {
            x = (bubbleParams.x + dp(60) - width).coerceIn(dp(12), (screenWidth - width - dp(12)).coerceAtLeast(dp(12)))
            y = bubbleParams.y.coerceIn(dp(32), (context.resources.displayMetrics.heightPixels - dp(410)).coerceAtLeast(dp(32)))
        }
        runCatching { manager.addView(layout, panelParams); panel = layout }
    }

    fun showResult(message: String) { showPanel(message) }
    fun hide() {
        hidePanel()
        bubble?.let { runCatching { manager.removeView(it) } }
        bubble = null
    }
    private fun hidePanel() {
        panel?.let { runCatching { manager.removeView(it) } }
        panel = null; status = null
    }
    private fun TextView.accessibilityHeadingCompat() {
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }
    private fun attachDrag(view: View) {
        var startX = 0; var startY = 0; var fingerX = 0f; var fingerY = 0f
        var moved = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = bubbleParams.x; startY = bubbleParams.y
                    fingerX = event.rawX; fingerY = event.rawY; moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - fingerX; val dy = event.rawY - fingerY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        hidePanel()
                        bubbleParams.x = (startX + dx.toInt()).coerceIn(0, (context.resources.displayMetrics.widthPixels - dp(60)).coerceAtLeast(0))
                        bubbleParams.y = (startY + dy.toInt()).coerceIn(dp(24), (context.resources.displayMetrics.heightPixels - dp(90)).coerceAtLeast(dp(24)))
                        runCatching { manager.updateViewLayout(view, bubbleParams) }
                    }
                    moved
                }
                MotionEvent.ACTION_UP -> moved
                else -> false
            }
        }
    }
}
