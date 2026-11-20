package com.example.dinex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// Línea de tiempo de la entrada, en milisegundos.
private const val TraceStart = 60f
private const val TraceEnd = 1350f
private const val FillEnd = 1800f
private const val CoinStart = 1720f
private const val CoinHit = 2060f
private const val RiseStart = 2380f
private const val RiseEnd = 2860f
private const val HoldEnd = 3500f
private const val ZoomStart = 3640f
private const val LaunchEnd = 4300f

// Avance máximo por cuadro y tiempo tras el cual la entrada deja de esperar a un equipo lento.
private const val MaxFrameStep = 64f
private const val MaxLaunchMillis = 6000L

private const val GoldenAngle = 2.39996f

private val LaunchInk = Color(0xFF07100C)
private val LaunchGreen = Color(0xFF2BD47F)
private val LaunchLime = Color(0xFFC6F06A)
private val CoinLight = Color(0xFFFFE08A)
private val CoinDark = Color(0xFFE5A52B)

private fun Float.between(start: Float, end: Float) = ((this - start) / (end - start)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeInOut(x: Float) = x * x * (3f - 2f * x)
private fun backOut(x: Float): Float { val c = 1.70158f; return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2) }

/** Trazos de la billetera de Dinex, en una cuadrícula de 100 × 100. */
private class WalletShape {
    val flap = Path().apply {
        moveTo(22f, 40f); lineTo(61f, 21f)
        cubicTo(67f, 18f, 74f, 21f, 75f, 28f); lineTo(76.5f, 40f)
    }
    val body = Path().apply { addRoundRect(RoundRect(14f, 38f, 80f, 86f, CornerRadius(12f))) }
    val clasp = Path().apply { addRoundRect(RoundRect(60f, 52f, 90f, 72f, CornerRadius(8f))) }
    val dot = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(70f, 62f), 3.6f)) }
    val traces = listOf(flap, body, clasp, dot)
    val measures = traces.map { path -> PathMeasure().apply { setPath(path, false) } }
    val totalLength = measures.sumOf { it.length.toDouble() }.toFloat()
}

/**
 * Entrada de Dinex: una línea de luz dibuja la billetera, se rellena, cae una moneda que la
 * hace rebotar con un destello, aparece el nombre y la cámara entra por el broche verde.
 */
@Composable
internal fun DinexLaunchScreen(
    reduceMotion: Boolean,
    onFinished: () -> Unit,
) {
    var timeline by remember { mutableFloatStateOf(0f) }
    val latestFinish by rememberUpdatedState(onFinished)
    var completed by remember { mutableStateOf(false) }
    fun continueOnce() {
        if (!completed) { completed = true; latestFinish() }
    }
    LaunchedEffect(reduceMotion) {
        val systemScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        if (reduceMotion || systemScale == 0f) {
            timeline = HoldEnd // Sin movimiento: se muestra directamente el logo con su nombre.
            delay(700)
        } else {
            // Si el dispositivo se traba al abrir, la animación espera en vez de saltarse fases.
            val startedAt = withFrameMillis { it }
            var previous = startedAt
            while (timeline < LaunchEnd) {
                val now = withFrameMillis { it }
                val elapsed = (now - previous).toFloat()
                val step = if (now - startedAt > MaxLaunchMillis) elapsed else elapsed.coerceAtMost(MaxFrameStep)
                timeline = (timeline + step / systemScale).coerceAtMost(LaunchEnd)
                previous = now
            }
        }
        continueOnce()
    }
    val shape = remember { WalletShape() }
    val t = timeline

    BoxWithConstraints(Modifier.fillMaxSize().background(LaunchInk)) {
        val logoSize = minOf(maxWidth, maxHeight) * .42f
        val logo = logoSize.coerceAtMost(190.dp)
        val rise = easeInOut(t.between(RiseStart, RiseEnd)) * (1f - easeInOut(t.between(HoldEnd, ZoomStart)))
        val liftDp = 62.dp
        Canvas(
            Modifier.fillMaxSize().semantics { contentDescription = "Dinex. Tu dinero, con propósito."; heading() },
        ) { drawLaunchScene(shape, t, rise, logo.toPx(), liftDp.toPx()) }

        // Nombre y lema, debajo del logo.
        val textOut = 1f - t.between(HoldEnd, HoldEnd + 170f)
        Column(
            Modifier.align(Alignment.Center).offset(y = logo * .42f + 14.dp).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                "DINEX".forEachIndexed { index, letter ->
                    val shown = t.between(RiseStart + 120f + index * 70f, RiseStart + 520f + index * 70f)
                    Text(
                        letter.toString(), color = if (letter == 'X') LaunchGreen else Color.White,
                        fontSize = 42.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 4.dp).graphicsLayer {
                            alpha = shown * textOut
                            translationY = (1f - backOut(shown)) * 26.dp.toPx()
                        },
                    )
                }
            }
            val underline = easeOut(t.between(RiseStart + 380f, RiseStart + 820f))
            Box(
                Modifier.padding(top = 4.dp).size(112.dp, 2.dp)
                    .graphicsLayer { scaleX = underline; alpha = underline * textOut }
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, LaunchGreen, LaunchLime, Color.Transparent))),
            )
            val tagline = easeOut(t.between(RiseStart + 480f, RiseStart + 900f))
            Text(
                "Tu dinero, con propósito.", color = Color.White.copy(alpha = .68f), fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).graphicsLayer { alpha = tagline * textOut; translationY = (1f - tagline) * 10.dp.toPx() },
            )
        }
        LaunchSkip(Modifier.align(Alignment.BottomEnd), 1f - t.between(ZoomStart - 150f, ZoomStart), ::continueOnce)
    }
}

@Composable
private fun LaunchSkip(modifier: Modifier, opacity: Float, onSkip: () -> Unit) {
    TextButton(
        onClick = onSkip,
        modifier = modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)
            .heightIn(min = 48.dp).graphicsLayer { alpha = opacity },
    ) { Text("Saltar", color = Color.White.copy(alpha = .85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

private fun DrawScope.drawLaunchScene(shape: WalletShape, t: Float, rise: Float, logoPx: Float, liftPx: Float) {
    val screen = Offset(size.width / 2, size.height / 2)
    val fill = easeInOut(t.between(TraceEnd - 60f, FillEnd))
    drawLaunchGrid(screen, t)

    // Rebote al caer la moneda, sube para el nombre y luego la cámara entra por el broche.
    val settle = t.between(CoinHit, CoinHit + 460f)
    val wave = sin(settle * PI.toFloat()) * (1f - settle)
    val bounce = 1f + wave * .14f
    val zoom = t.between(ZoomStart, LaunchEnd).pow(3)
    val logoUnit = logoPx / 100f * bounce * (1f - rise * .16f)
    val unit = logoUnit * (1f + zoom * 70f)
    val float = sin((t - RiseEnd).coerceAtLeast(0f) / 300f) * logoPx * .014f * rise
    val restingCenter = screen + Offset(0f, wave * logoPx * .05f + float - liftPx * rise)
    val pivot = Offset(83f, 62f)
    val restingPivot = restingCenter + (pivot - Offset(50f, 56f)) * logoUnit
    val origin = lerp(restingPivot, screen, easeOut(t.between(ZoomStart, ZoomStart + 340f))) - pivot * unit

    // Resplandor detrás del logo, más fuerte en el instante del golpe.
    val hit = 1f - t.between(CoinHit, CoinHit + 520f)
    val glow = fill * .22f + (if (t >= CoinHit) hit * .3f else 0f)
    if (glow > 0f) {
        val radius = logoPx * (1.5f + (1f - hit) * .12f)
        drawCircle(Brush.radialGradient(listOf(LaunchGreen.copy(alpha = glow.coerceAtMost(1f)), Color.Transparent), restingCenter, radius), radius, restingCenter)
    }

    withTransform({ translate(origin.x, origin.y); scale(unit, unit, Offset.Zero) }) {
        drawWalletGuides(t, fill)
        drawImpact(t)
        drawCoin(t)
        if (fill > 0f) drawWalletFill(shape, fill, t)
        drawWalletTrace(shape, t, fill)
        drawImpactFlash(t)
    }
    drawWarpLines(screen, t)
}

/** Cuadrícula que se abre desde el centro de la pantalla. */
private fun DrawScope.drawLaunchGrid(screen: Offset, t: Float) {
    val reveal = easeOut(t.between(0f, 900f))
    if (reveal <= 0f) return
    val color = Color.White.copy(alpha = .07f)
    val step = 34.dp.toPx()
    var x = screen.x % step
    while (x < size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
    var y = screen.y % step
    while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), 1f); y += step }
    val radius = max(size.width, size.height) * (.08f + reveal * .72f)
    drawRect(Brush.radialGradient(0f to Color.Transparent, .55f to LaunchInk.copy(alpha = .35f), 1f to LaunchInk, center = screen, radius = radius))
}

/** Líneas de construcción, como un plano, que se apagan al rellenarse el logo. */
private fun DrawScope.drawWalletGuides(t: Float, fill: Float) {
    val draw = easeInOut(t.between(TraceStart, TraceStart + 800f))
    val opacity = .16f * (1f - fill) * draw
    if (opacity <= 0f) return
    val color = Color.White.copy(alpha = opacity)
    drawLine(color, Offset(50f - 62f * draw, 62f), Offset(50f + 62f * draw, 62f), .35f)
    drawLine(color, Offset(50f, 56f - 52f * draw), Offset(50f, 56f + 52f * draw), .35f)
    drawCircle(color, 44f * draw, Offset(50f, 56f), style = Stroke(.35f))
}

/** La línea de luz recorre cada trazo, con cola de cometa y chispas que quedan atrás. */
private fun DrawScope.drawWalletTrace(shape: WalletShape, t: Float, fill: Float) {
    val opacity = 1f - fill
    if (t < TraceStart || opacity <= 0f) return
    var budget = easeInOut(t.between(TraceStart, TraceEnd)) * shape.totalLength
    shape.traces.forEachIndexed { index, path ->
        val measure = shape.measures[index]
        val length = measure.length
        val portion = budget.coerceIn(0f, length)
        if (portion > 0f) {
            val visible = Path()
            measure.getSegment(0f, portion, visible, true)
            drawPath(visible, LaunchGreen.copy(alpha = .35f * opacity), style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(visible, Color.White.copy(alpha = .95f * opacity), style = Stroke(.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (budget <= length) drawTraceTip(measure, portion, t)
        }
        budget -= length
    }
}

private fun DrawScope.drawTraceTip(measure: PathMeasure, portion: Float, t: Float) {
    val tail = Path()
    measure.getSegment((portion - 22f).coerceAtLeast(0f), portion, tail, true)
    drawPath(tail, LaunchLime.copy(alpha = .9f), style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    for (spark in 1..7) {
        val distance = portion - spark * 4.2f
        if (distance <= 0f) break
        val base = measure.getPosition(distance)
        val drift = spark * .55f
        val point = base + Offset(sin(spark * 2.1f + t / 90f) * drift, cos(spark * 1.7f + t / 110f) * drift)
        drawCircle(LaunchLime.copy(alpha = (1f - spark / 8f) * .8f), .75f - spark * .06f, point)
    }
    val point = measure.getPosition(portion)
    drawCircle(Brush.radialGradient(listOf(LaunchLime.copy(alpha = .85f), Color.Transparent), point, 9f), 9f, point)
    drawCircle(Color.White, 1.2f, point)
}

/** Relleno diagonal desde la esquina inferior izquierda y un brillo que cruza el logo. */
private fun DrawScope.drawWalletFill(shape: WalletShape, fill: Float, t: Float) {
    val reach = fill * 210f
    val wipe = Path().apply { moveTo(0f, 110f); lineTo(reach, 110f); lineTo(0f, 110f - reach); close() }
    clipPath(wipe) {
        drawPath(shape.flap, Color(0xFFB9CDBF), style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(shape.body, Brush.linearGradient(listOf(Color.White, Color(0xFFD5E4DA)), Offset(14f, 38f), Offset(80f, 86f)))
        drawPath(shape.clasp, LaunchInk, style = Stroke(3f))
        drawPath(shape.clasp, Brush.linearGradient(listOf(LaunchGreen, Color(0xFF0E8F55)), Offset(60f, 52f), Offset(90f, 72f)))
        drawPath(shape.dot, Color.White)
    }
    val sweep = t.between(CoinHit + 40f, CoinHit + 760f)
    if (sweep > 0f && sweep < 1f) clipPath(shape.body) {
        val center = -20f + sweep * 140f
        drawRect(
            Brush.linearGradient(listOf(Color.Transparent, LaunchGreen.copy(alpha = .3f), Color.Transparent), Offset(center - 16f, 38f), Offset(center + 16f, 86f)),
            Offset(0f, 30f), Size(100f, 64f),
        )
    }
}

/** Moneda que gira mientras cae; la billetera se dibuja encima y parece guardarla. */
private fun DrawScope.drawCoin(t: Float) {
    if (t < CoinStart || t >= CoinHit) return
    val fall = t.between(CoinStart, CoinHit)
    val center = Offset(47f, -46f + fall * fall * 104f)
    val turn = abs(cos(fall * PI.toFloat() * 2.5f)).coerceAtLeast(.18f)
    withTransform({ scale(turn, 1f, center) }) {
        drawCircle(Brush.linearGradient(listOf(CoinLight, CoinDark), center - Offset(9f, 9f), center + Offset(9f, 9f)), 10f, center)
        drawCircle(Color.White.copy(alpha = .55f), 7f, center, style = Stroke(.9f))
        drawLine(Color.White.copy(alpha = .85f), center - Offset(0f, 4f), center + Offset(0f, 4f), 1.6f, cap = StrokeCap.Round)
    }
}

/** Ondas y chispas que salen de la billetera cuando la moneda entra. */
private fun DrawScope.drawImpact(t: Float) {
    if (t < CoinHit) return
    val center = Offset(47f, 60f)
    for (ring in 0..1) {
        val progress = t.between(CoinHit + ring * 120f, CoinHit + 620f + ring * 120f)
        if (progress <= 0f || progress >= 1f) continue
        val spread = easeOut(progress)
        drawCircle(LaunchGreen.copy(alpha = (1f - progress) * .65f), 26f + spread * 62f, center, style = Stroke(1.4f * (1f - progress) + .2f))
    }
    val colors = listOf(LaunchLime, LaunchGreen, Color.White, CoinLight)
    repeat(30) { index ->
        val progress = t.between(CoinHit, CoinHit + 560f + index % 5 * 60f)
        if (progress >= 1f) return@repeat
        val angle = index * GoldenAngle
        val direction = Offset(cos(angle), sin(angle))
        val travel = 30f + easeOut(progress) * (22f + index * 37 % 42)
        val streak = 2f + (1f - progress) * 5f
        drawLine(
            colors[index % colors.size].copy(alpha = 1f - progress),
            center + direction * (travel - streak), center + direction * travel,
            1.1f * (1f - progress) + .3f, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawImpactFlash(t: Float) {
    val flash = 1f - t.between(CoinHit, CoinHit + 240f)
    if (t < CoinHit || flash <= 0f) return
    val center = Offset(47f, 58f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = flash * flash * .75f), Color.Transparent), center, 58f), 58f, center)
}

/** Líneas de velocidad mientras la cámara atraviesa el broche. */
private fun DrawScope.drawWarpLines(screen: Offset, t: Float) {
    val warp = t.between(ZoomStart, LaunchEnd)
    if (warp <= 0f || warp >= 1f) return
    val reach = max(size.width, size.height)
    val strength = sin(warp * PI.toFloat())
    repeat(44) { index ->
        val angle = index * GoldenAngle
        val direction = Offset(cos(angle), sin(angle))
        val phase = (warp * 2.2f + (index * 53 % 100) / 100f) % 1f
        val start = reach * (.1f + phase * phase * .8f)
        val length = reach * (.03f + phase * .16f)
        drawLine(
            Color.White.copy(alpha = strength * phase * .55f),
            screen + direction * start, screen + direction * (start + length),
            1.6.dp.toPx(), cap = StrokeCap.Round,
        )
    }
}
