package com.example.dinex

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Qué muestra el panel de Oye Dinex en cada momento. */
internal enum class VoicePanelMode { HINT, LISTENING, PROCESSING, ANSWER, CONFIRM, SAVED, ASK_AI, ERROR }

internal data class VoicePanelUi(
    val mode: VoicePanelMode,
    val message: String,
    val transcript: String = "",
    val items: List<String> = emptyList(),
    val aiOnline: Boolean = true,
    val handsFree: Boolean = false,
) {
    val state: DinexVoiceState get() = when (mode) {
        VoicePanelMode.HINT -> DinexVoiceState.IDLE
        VoicePanelMode.LISTENING -> DinexVoiceState.LISTENING
        VoicePanelMode.PROCESSING -> DinexVoiceState.PROCESSING
        VoicePanelMode.ANSWER, VoicePanelMode.CONFIRM, VoicePanelMode.SAVED, VoicePanelMode.ASK_AI -> DinexVoiceState.RESPONDING
        VoicePanelMode.ERROR -> DinexVoiceState.ERROR
    }
}

internal interface DinexVoiceOverlayActions {
    fun onListen()
    fun onCancel()
    fun onDoneSpeaking()
    fun onConfirm(save: Boolean)
    fun onUndo()
    fun onAskAi()
    fun onOpenApp()
    fun onDisable()
    fun onPanelClosed()
}

/**
 * Burbuja flotante con la mascota y panel de conversación. Es pequeña, se puede
 * arrastrar y nunca cubre toda la pantalla. Android exige el permiso "mostrar sobre
 * otras apps"; si no está concedido, la burbuja simplemente no aparece.
 */
internal class DinexVoiceOverlay(
    private val context: Context,
    private val reducedMotion: Boolean,
    private val actions: DinexVoiceOverlayActions,
) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubble: DinexMascotBubbleView? = null
    private var panel: LinearLayout? = null
    private var views: PanelViews? = null
    private var current: VoicePanelUi? = null
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val bubbleSize get() = dp(72)

    private fun params(width: Int, height: Int = WindowManager.LayoutParams.WRAP_CONTENT) = WindowManager.LayoutParams(
        width, height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private val bubbleParams by lazy {
        params(bubbleSize, bubbleSize).apply {
            x = context.resources.displayMetrics.widthPixels - bubbleSize - dp(8)
            y = dp(240)
        }
    }

    val panelOpen: Boolean get() = panel != null

    fun show() {
        if (bubble != null || !Settings.canDrawOverlays(context)) return
        val view = DinexMascotBubbleView(context, reducedMotion).apply {
            setOnClickListener { actions.onListen() }
            setOnLongClickListener { render(VoicePanelUi(VoicePanelMode.HINT, hintText())); true }
        }
        attachDrag(view)
        runCatching { manager.addView(view, bubbleParams); bubble = view }
        current?.let { view.state = it.state }
    }

    fun hide() {
        hidePanel()
        bubble?.let { runCatching { manager.removeView(it) } }
        bubble = null
    }

    fun hidePanel() {
        panel?.let { runCatching { manager.removeView(it) } }
        panel = null
        views = null
    }

    fun setLevel(level: Float) {
        bubble?.level = level
        views?.wave?.level = level
    }

    /** Muestra el estado en la burbuja y, si corresponde, en el panel. */
    fun render(ui: VoicePanelUi, openPanel: Boolean = true) {
        // Un aviso de reposo nunca reemplaza una conversación que se está mostrando.
        if (!openPanel && panel != null) { show(); return }
        current = ui
        show()
        bubble?.state = ui.state
        if (!openPanel) return
        if (panel == null) createPanel()
        val v = views ?: return
        v.state.text = when (ui.state) {
            DinexVoiceState.IDLE -> if (ui.handsFree) "● Esperando “Oye Dinex”" else "● Listo para escucharte"
            DinexVoiceState.LISTENING -> "● Escuchando…"
            DinexVoiceState.PROCESSING -> "● Pensando…"
            DinexVoiceState.RESPONDING -> "● Respondiendo"
            DinexVoiceState.ERROR -> "● Algo salió mal"
        }
        v.state.setTextColor(when (ui.state) {
            DinexVoiceState.ERROR -> DinexVoiceColors.ALERT
            DinexVoiceState.PROCESSING -> DinexVoiceColors.THINKING
            DinexVoiceState.IDLE -> DinexVoiceColors.TEXT_SOFT
            else -> DinexVoiceColors.ACCENT
        })
        v.connection.text = if (ui.aiOnline) "IA en línea" else "Sin internet"
        v.connection.setTextColor(if (ui.aiOnline) DinexVoiceColors.ACCENT else DinexVoiceColors.TEXT_SOFT)
        (v.connection.background as? GradientDrawable)?.setStroke(dp(1), if (ui.aiOnline) DinexVoiceColors.ACCENT_SOFT else DinexVoiceColors.STROKE)
        v.wave.active = ui.mode == VoicePanelMode.LISTENING
        v.transcript.visibility = if (ui.transcript.isBlank()) View.GONE else View.VISIBLE
        v.transcript.text = "“${ui.transcript}”"
        v.message.text = ui.message
        v.message.setTextColor(if (ui.mode == VoicePanelMode.ERROR) DinexVoiceColors.ALERT else DinexVoiceColors.TEXT)
        v.items.removeAllViews()
        ui.items.forEach { line -> v.items.addView(itemRow(line)) }
        v.items.visibility = if (ui.items.isEmpty()) View.GONE else View.VISIBLE
        configureButtons(v, ui.mode)
        if (ui.mode != VoicePanelMode.LISTENING) v.message.announceForAccessibility(ui.message)
    }

    private fun configureButtons(v: PanelViews, mode: VoicePanelMode) {
        fun TextView.set(label: String?, onClick: (() -> Unit)?) {
            visibility = if (label == null) View.GONE else View.VISIBLE
            text = label.orEmpty()
            setOnClickListener { onClick?.invoke() }
        }
        when (mode) {
            VoicePanelMode.LISTENING -> { v.secondary.set("Cancelar") { actions.onCancel() }; v.primary.set("Listo") { actions.onDoneSpeaking() } }
            VoicePanelMode.PROCESSING -> { v.secondary.set("Cancelar") { actions.onCancel() }; v.primary.set(null, null) }
            VoicePanelMode.CONFIRM -> { v.secondary.set("Cancelar") { actions.onConfirm(false) }; v.primary.set("Guardar") { actions.onConfirm(true) } }
            VoicePanelMode.ASK_AI -> { v.secondary.set("Volver a escuchar") { actions.onListen() }; v.primary.set("Preguntar a Dinex IA") { actions.onAskAi() } }
            VoicePanelMode.SAVED -> { v.secondary.set("Deshacer") { actions.onUndo() }; v.primary.set("Volver a escuchar") { actions.onListen() } }
            VoicePanelMode.HINT, VoicePanelMode.ANSWER, VoicePanelMode.ERROR ->
                { v.secondary.set("Cerrar") { closePanel() }; v.primary.set(if (mode == VoicePanelMode.HINT) "Hablar" else "Volver a escuchar") { actions.onListen() } }
        }
    }

    private fun closePanel() {
        hidePanel()
        actions.onPanelClosed()
    }

    private class PanelViews(
        val state: TextView, val connection: TextView, val wave: DinexVoiceWaveView, val transcript: TextView,
        val message: TextView, val items: LinearLayout, val secondary: TextView, val primary: TextView,
    )

    private fun rounded(color: Int, radius: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun createPanel() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = rounded(DinexVoiceColors.SURFACE, 24, DinexVoiceColors.STROKE)
            elevation = dp(14).toFloat()
        }
        // Encabezado: mascota, nombre, estado, conexión con Dinex IA y cerrar.
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(context).apply {
            setImageResource(R.drawable.dinex_mascot)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = rounded(DinexVoiceColors.SURFACE_RAISED, 14)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(6), 0) }
        titles.addView(TextView(context).apply {
            text = "Oye Dinex"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(DinexVoiceColors.TEXT)
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        val state = TextView(context).apply { textSize = 12f; setTextColor(DinexVoiceColors.TEXT_SOFT); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        titles.addView(state)
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val connection = TextView(context).apply {
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            background = rounded(0x00000000, 12, DinexVoiceColors.ACCENT_SOFT)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        header.addView(connection)
        header.addView(TextView(context).apply {
            text = "✕"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(DinexVoiceColors.TEXT_SOFT)
            contentDescription = "Cerrar panel"
            setOnClickListener { closePanel() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(4) })
        layout.addView(header)

        val wave = DinexVoiceWaveView(context, reducedMotion).apply { visibility = View.GONE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        layout.addView(wave, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(10) })
        val transcript = TextView(context).apply {
            textSize = 13f; setTextColor(DinexVoiceColors.TEXT_SOFT); setTypeface(typeface, Typeface.ITALIC)
            maxLines = 3; ellipsize = TextUtils.TruncateAt.END
        }
        layout.addView(transcript, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val message = TextView(context).apply {
            textSize = 15f; setTextColor(DinexVoiceColors.TEXT); setLineSpacing(0f, 1.18f)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        layout.addView(message, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val items = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(items, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // Acciones principales: secundaria (contorno) y primaria (verde Dinex).
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(primary: Boolean) = TextView(context).apply {
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            minHeight = dp(46); setPadding(dp(10), 0, dp(10), 0)
            setTextColor(if (primary) DinexVoiceColors.SURFACE else DinexVoiceColors.TEXT)
            background = if (primary) rounded(DinexVoiceColors.ACCENT, 23) else rounded(0x00000000, 23, DinexVoiceColors.STROKE)
            isClickable = true; isFocusable = true
        }
        val secondary = button(false)
        val primary = button(true)
        row.addView(secondary, LinearLayout.LayoutParams(0, dp(46), 1f))
        row.addView(primary, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(8) })
        layout.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        // Enlaces discretos: abrir la app o apagar el asistente.
        val footer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun link(label: String, onClick: () -> Unit) = TextView(context).apply {
            text = label; textSize = 12f; setTextColor(DinexVoiceColors.TEXT_SOFT); gravity = Gravity.CENTER
            minHeight = dp(40); setPadding(dp(12), 0, dp(12), 0); setOnClickListener { onClick() }
        }
        footer.addView(link("Abrir Dinex") { hidePanel(); actions.onOpenApp() })
        footer.addView(TextView(context).apply { text = "·"; setTextColor(DinexVoiceColors.STROKE) })
        footer.addView(link("Desactivar Oye Dinex") { actions.onDisable() })
        layout.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        val metrics = context.resources.displayMetrics
        val width = minOf(dp(320), metrics.widthPixels - dp(24))
        val panelParams = params(width).apply {
            x = (bubbleParams.x + bubbleSize - width).coerceIn(dp(12), (metrics.widthPixels - width - dp(12)).coerceAtLeast(dp(12)))
            // Debajo de la burbuja si cabe; si no, encima.
            val below = bubbleParams.y + bubbleSize + dp(6)
            y = if (below + dp(330) < metrics.heightPixels) below else (bubbleParams.y - dp(340)).coerceAtLeast(dp(32))
        }
        val created = PanelViews(state, connection, wave, transcript, message, items, secondary, primary)
        runCatching { manager.addView(layout, panelParams); panel = layout; views = created }
    }

    private fun itemRow(line: String) = TextView(context).apply {
        text = line; textSize = 13f; setTextColor(DinexVoiceColors.TEXT)
        background = rounded(DinexVoiceColors.SURFACE_RAISED, 12)
        setPadding(dp(12), dp(9), dp(12), dp(9))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
    }

    private fun hintText() = "Toca “Hablar” y di, por ejemplo: “¿cuánto gasté esta semana?” o “registra 25 soles en comida”."

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
                        val metrics = context.resources.displayMetrics
                        bubbleParams.x = (startX + dx.toInt()).coerceIn(0, (metrics.widthPixels - bubbleSize).coerceAtLeast(0))
                        bubbleParams.y = (startY + dy.toInt()).coerceIn(dp(24), (metrics.heightPixels - bubbleSize - dp(24)).coerceAtLeast(dp(24)))
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
