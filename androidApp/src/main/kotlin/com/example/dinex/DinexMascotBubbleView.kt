package com.example.dinex

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Estados visuales de Oye Dinex, compartidos por la burbuja y el panel. */
enum class DinexVoiceState { IDLE, LISTENING, PROCESSING, RESPONDING, ERROR }

/** Colores de la identidad de Dinex (los mismos de los widgets). */
internal object DinexVoiceColors {
    const val SURFACE = 0xFF0F1613.toInt()
    const val SURFACE_RAISED = 0xFF18231E.toInt()
    const val STROKE = 0xFF27352E.toInt()
    const val ACCENT = 0xFF3DD68C.toInt()
    const val ACCENT_SOFT = 0x333DD68C
    const val ALERT = 0xFFF0705A.toInt()
    const val TEXT = 0xFFF2F7F4.toInt()
    const val TEXT_SOFT = 0xFFA9BCB2.toInt()
    const val THINKING = 0xFF8FB8FF.toInt()
}

/**
 * Burbuja flotante con la mascota oficial de Dinex. La imagen se dibuja completa
 * (sin recortes ni deformación) dentro de un círculo, y alrededor se anima un aro
 * distinto para cada estado: respirar, ondas al escuchar, giro al pensar, rebote
 * al responder y temblor rojo ante un error.
 */
internal class DinexMascotBubbleView(context: Context, private val reducedMotion: Boolean) : View(context) {
    var state: DinexVoiceState = DinexVoiceState.IDLE
        set(value) {
            if (field == value) return
            field = value
            stateStartedAt = System.currentTimeMillis()
            contentDescription = when (value) {
                DinexVoiceState.IDLE -> "Oye Dinex. Toca para hablar, mantén presionado para ver opciones."
                DinexVoiceState.LISTENING -> "Oye Dinex te está escuchando"
                DinexVoiceState.PROCESSING -> "Oye Dinex está pensando"
                DinexVoiceState.RESPONDING -> "Oye Dinex está respondiendo"
                DinexVoiceState.ERROR -> "Oye Dinex tuvo un problema"
            }
            invalidate()
        }

    /** Volumen de la voz (0 a 1) mientras escucha; agranda las ondas. */
    var level: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f) }

    private val mascot: Bitmap? = runCatching { BitmapFactory.decodeResource(resources, R.drawable.dinex_mascot) }.getOrNull()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arcBounds = RectF()
    private val imageBounds = RectF()
    private var phase = 0f
    private var smoothLevel = 0f
    private var stateStartedAt = System.currentTimeMillis()
    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        state = DinexVoiceState.IDLE
        contentDescription = "Oye Dinex. Toca para hablar, mantén presionado para ver opciones."
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!reducedMotion && ValueAnimator.areAnimatorsEnabled()) ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(width, height) / 2f
        val core = outer * 0.74f
        val t = phase * 2f * PI.toFloat()
        smoothLevel += (level - smoothLevel) * 0.25f
        val sinceState = System.currentTimeMillis() - stateStartedAt

        // Desplazamiento según el estado: rebote al responder y temblor al fallar.
        var dx = 0f
        var dy = 0f
        when (state) {
            DinexVoiceState.RESPONDING -> dy = -abs(sin(t * 3f)) * outer * 0.06f
            DinexVoiceState.ERROR -> if (sinceState < 600) dx = sin(sinceState / 600f * 6f * PI.toFloat()) * outer * 0.08f * (1f - sinceState / 600f)
            else -> Unit
        }

        // Aro exterior animado.
        when (state) {
            DinexVoiceState.IDLE -> {
                val breath = 0.5f + 0.5f * sin(t)
                fill.color = DinexVoiceColors.ACCENT
                fill.alpha = (28 + 30 * breath).toInt()
                canvas.drawCircle(cx, cy, core + (outer - core) * (0.55f + 0.25f * breath), fill)
            }
            DinexVoiceState.LISTENING -> {
                // Tres ondas que salen del centro; crecen con el volumen de la voz.
                for (index in 0 until 3) {
                    val wave = (phase * 1.6f + index / 3f) % 1f
                    fill.color = DinexVoiceColors.ACCENT
                    fill.alpha = ((1f - wave) * (70 + 90 * smoothLevel)).toInt().coerceIn(0, 255)
                    canvas.drawCircle(cx, cy, core + (outer - core) * wave * (0.7f + 0.3f * smoothLevel), fill)
                }
            }
            DinexVoiceState.PROCESSING -> {
                stroke.strokeWidth = outer * 0.10f
                stroke.shader = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, DinexVoiceColors.THINKING, DinexVoiceColors.ACCENT), floatArrayOf(0f, 0.6f, 1f))
                val radius = (core + outer) / 2f
                arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
                canvas.save()
                canvas.rotate(phase * 720f, cx, cy)
                canvas.drawArc(arcBounds, 0f, 280f, false, stroke)
                canvas.restore()
                stroke.shader = null
            }
            DinexVoiceState.RESPONDING -> {
                val pulse = 0.5f + 0.5f * sin(t * 3f)
                stroke.strokeWidth = outer * 0.08f
                stroke.color = DinexVoiceColors.ACCENT
                stroke.alpha = (140 + 100 * pulse).toInt()
                canvas.drawCircle(cx + dx, cy + dy, core + (outer - core) * 0.55f, stroke)
            }
            DinexVoiceState.ERROR -> {
                stroke.strokeWidth = outer * 0.09f
                stroke.color = DinexVoiceColors.ALERT
                stroke.alpha = 255
                canvas.drawCircle(cx + dx, cy, core + (outer - core) * 0.5f, stroke)
            }
        }

        // Fondo circular y mascota completa, centrada y sin deformar.
        fill.color = DinexVoiceColors.SURFACE
        fill.alpha = 255
        canvas.drawCircle(cx + dx, cy + dy, core, fill)
        stroke.shader = null
        stroke.strokeWidth = outer * 0.04f
        stroke.color = if (state == DinexVoiceState.ERROR) DinexVoiceColors.ALERT else DinexVoiceColors.ACCENT
        stroke.alpha = if (state == DinexVoiceState.IDLE) 160 else 255
        canvas.drawCircle(cx + dx, cy + dy, core, stroke)
        mascot?.let { image ->
            // "Hablar": la mascota crece y se encoge un poco al ritmo de la respuesta.
            val talk = if (state == DinexVoiceState.RESPONDING) 1f + 0.05f * sin(t * 6f) else 1f
            val size = core * 1.48f * talk
            val scale = min(size / image.width, size / image.height)
            val w = image.width * scale
            val h = image.height * scale
            imageBounds.set(cx + dx - w / 2f, cy + dy - h / 2f + core * 0.02f, cx + dx + w / 2f, cy + dy + h / 2f + core * 0.02f)
            canvas.drawBitmap(image, null, imageBounds, bitmapPaint)
        }

        // Indicador pequeño del estado (punto) para quien no distingue bien los colores.
        val dotColor = when (state) {
            DinexVoiceState.IDLE -> null
            DinexVoiceState.LISTENING -> DinexVoiceColors.ACCENT
            DinexVoiceState.PROCESSING -> DinexVoiceColors.THINKING
            DinexVoiceState.RESPONDING -> DinexVoiceColors.ACCENT
            DinexVoiceState.ERROR -> DinexVoiceColors.ALERT
        }
        if (dotColor != null) {
            fill.color = DinexVoiceColors.SURFACE
            canvas.drawCircle(cx + core * 0.72f, cy - core * 0.72f, outer * 0.17f, fill)
            fill.color = dotColor
            canvas.drawCircle(cx + core * 0.72f, cy - core * 0.72f, outer * 0.11f, fill)
        }
    }
}

/** Barras de voz del panel: se mueven con el volumen mientras Oye Dinex escucha. */
internal class DinexVoiceWaveView(context: Context, private val reducedMotion: Boolean) : View(context) {
    var active: Boolean = false
        set(value) { field = value; visibility = if (value) VISIBLE else GONE; if (value) restart() else animator.cancel() }
    var level: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f) }
    private var phase = 0f
    private var smooth = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DinexVoiceColors.ACCENT; strokeCap = Paint.Cap.ROUND }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    private fun restart() { if (!reducedMotion && ValueAnimator.areAnimatorsEnabled() && isAttachedToWindow) animator.start() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (active) restart() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        smooth += (level - smooth) * 0.3f
        val bars = 21
        val gap = width / (bars * 2f)
        paint.strokeWidth = gap * 1.1f
        val middle = height / 2f
        for (index in 0 until bars) {
            val x = gap + index * gap * 2f
            val centerWeight = 1f - abs(index - bars / 2f) / (bars / 2f)
            val wave = 0.5f + 0.5f * sin((phase * 2f * PI.toFloat() * 2f) + index * 0.55f)
            val amplitude = (0.12f + (0.25f + 0.75f * smooth) * centerWeight * (0.4f + 0.6f * wave)).coerceAtMost(1f)
            paint.alpha = (110 + 145 * centerWeight).toInt()
            canvas.drawLine(x, middle - amplitude * middle * 0.9f, x, middle + amplitude * middle * 0.9f, paint)
        }
    }
}
