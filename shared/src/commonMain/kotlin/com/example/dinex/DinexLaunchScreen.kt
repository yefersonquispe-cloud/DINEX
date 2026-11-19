package com.example.dinex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Two stages: a real stroke reveal, followed by the polished typographic mark. */
@Composable
internal fun DinexLaunchScreen(
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    onFinished: () -> Unit,
) {
    val writing = remember { Animatable(0f) }
    val finish = remember { Animatable(0f) }
    val latestFinish by rememberUpdatedState(onFinished)
    var completed by remember { mutableStateOf(false) }
    fun continueOnce() {
        if (!completed) { completed = true; latestFinish() }
    }
    LaunchedEffect(reduceMotion) {
        val systemScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        if (reduceMotion || systemScale == 0f) {
            writing.snapTo(1f)
            finish.snapTo(1f)
            delay(120)
        } else {
            // El lápiz escribe cada letra y luego la palabra toma fuerza, letra por letra.
            writing.animateTo(1f, tween(1500, easing = LinearEasing))
            finish.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            delay(220)
        }
        continueOnce()
    }
    Box(
        Modifier.fillMaxSize().background(AppBg)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        DinexAuroraBackground()
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            drawRect(Brush.radialGradient(
                listOf(Mint.copy(alpha = .75f), Color.Transparent),
                Offset(size.width * .66f, size.height * .43f), size.maxDimension * .62f,
            ))
            // A restrained technical texture, without bitmap branding.
            val spacing = 48.dp.toPx()
            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) {
                    drawCircle(Green.copy(alpha = .06f), 1f, Offset(x, y))
                    y += spacing
                }
                x += spacing
            }
        }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 600.dp).fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "TU FUTURO EMPIEZA AQUÍ", color = Gray, fontSize = 10.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer { alpha = (writing.value * 5f).coerceIn(0f, 1f) },
            )
            Spacer(Modifier.height(34.dp))
            Box(Modifier.fillMaxWidth().height(125.dp).semantics {
                contentDescription = "Dinex. Tu dinero, con propósito."
                heading()
            }, contentAlignment = Alignment.Center) {
                DrawnDinex(writing.value, finish.value)
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.width(64.dp).height(2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(finish.value).background(
                    Brush.horizontalGradient(listOf(Color.Transparent, Green.copy(alpha = .8f), Color.Transparent)),
                ))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Tu dinero, con propósito.",
                color = Night,
                fontWeight = FontWeight.Medium, fontSize = 19.sp, textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val shown = (finish.value * 1.8f - .2f).coerceIn(0f, 1f)
                    alpha = shown; translationY = (1f - shown) * 12.dp.toPx()
                },
            )
            Spacer(Modifier.height(9.dp))
            Text(
                "Pequeñas decisiones. Grandes mañanas.", color = Gray, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val shown = (finish.value * 2f - 1f).coerceIn(0f, 1f)
                    alpha = shown; translationY = (1f - shown) * 10.dp.toPx()
                },
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).widthIn(max = 600.dp).fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onReduceMotion(true); continueOnce() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Reducir movimiento", color = Gray, fontSize = 12.sp)
            }
            TextButton(onClick = ::continueOnce, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Continuar", color = GreenDark, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DrawnDinex(writing: Float, finish: Float) {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        val scale = minOf(size.width / 850f, size.height / 210f)
        val left = (size.width - 850f * scale) / 2
        val top = (size.height - 210f * scale) / 2
        fun p(x: Float, y: Float) = Offset(left + x * scale, top + y * scale)
        val strokes = listOf(
            Path().apply {
                moveTo(p(35f, 30f).x, p(35f, 30f).y); lineTo(p(35f, 180f).x, p(35f, 180f).y)
                cubicTo(p(180f, 190f).x, p(180f, 190f).y, p(180f, 20f).x, p(180f, 20f).y, p(35f, 30f).x, p(35f, 30f).y)
            },
            Path().apply { moveTo(p(220f, 30f).x, p(220f, 30f).y); lineTo(p(220f, 180f).x, p(220f, 180f).y) },
            Path().apply {
                moveTo(p(285f, 180f).x, p(285f, 180f).y); lineTo(p(285f, 30f).x, p(285f, 30f).y)
                lineTo(p(430f, 180f).x, p(430f, 180f).y); lineTo(p(430f, 30f).x, p(430f, 30f).y)
            },
            Path().apply {
                moveTo(p(610f, 30f).x, p(610f, 30f).y); lineTo(p(500f, 30f).x, p(500f, 30f).y)
                lineTo(p(500f, 180f).x, p(500f, 180f).y); lineTo(p(615f, 180f).x, p(615f, 180f).y)
            },
            Path().apply { moveTo(p(500f, 102f).x, p(500f, 102f).y); lineTo(p(593f, 102f).x, p(593f, 102f).y) },
            Path().apply { moveTo(p(677f, 30f).x, p(677f, 30f).y); lineTo(p(817f, 180f).x, p(817f, 180f).y) },
            Path().apply { moveTo(p(817f, 30f).x, p(817f, 30f).y); lineTo(p(677f, 180f).x, p(677f, 180f).y) },
        )
        val measures = strokes.map { PathMeasure().apply { setPath(it, false) } }
        // Entre un trazo y otro el lápiz se levanta y viaja hasta la siguiente letra.
        val penTravel = 58f * scale
        val total = measures.sumOf { it.length.toDouble() }.toFloat() + penTravel * (strokes.size - 1)
        var budget = writing * total
        var tip = p(35f, 30f)
        var lift = 0f
        var trail = emptyList<Offset>()
        // Un brillo recorre la palabra cuando termina de escribirse.
        val sweep = finish * 1.3f - .15f
        val shineStart = (sweep - .1f).coerceIn(0f, 1f)
        val shine = sweep.coerceIn(shineStart, 1f)
        val shineEnd = (sweep + .1f).coerceIn(shine, 1f)
        val ink = Brush.linearGradient(
            0f to Night, shineStart to Night, shine to (if (finish in .01f..<1f) Color(0xFF34C77E) else Night), shineEnd to Night, 1f to GreenDark,
            start = p(0f, 0f), end = p(850f, 0f),
        )
        strokes.forEachIndexed { index, path ->
            val measure = measures[index]
            val length = measure.length
            val local = (budget / length).coerceIn(0f, 1f)
            val portion = length * local * local * (3f - 2f * local) // acelera y frena como una mano real
            // Cada trazo toma grosor y da un pequeño salto, uno detrás de otro.
            val own = (finish * 1.7f - index * .1f).coerceIn(0f, 1f)
            val back = own - 1f
            val weight = 5f + 15f * (1f + 2.70158f * back * back * back + 1.70158f * back * back)
            translate(top = -sin(own * PI.toFloat()) * 10f * scale) {
                drawPath(path, Green.copy(alpha = .07f), style = Stroke(20f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (portion > 0f) {
                    val visible = Path()
                    measure.getSegment(0f, portion, visible, true)
                    drawPath(visible, ink, style = Stroke(weight * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (local < 1f) {
                        // Tinta fresca justo detrás de la punta.
                        val fresh = Path()
                        measure.getSegment((portion - 44f * scale).coerceAtLeast(0f), portion, fresh, true)
                        drawPath(fresh, GreenDark, style = Stroke(8.5f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            if (budget in 0f..length) {
                tip = measure.getPosition(portion)
                trail = (1..7).map { step -> measure.getPosition((portion - step * 15f * scale).coerceAtLeast(0f)) }
            } else if (budget > length && index < strokes.lastIndex && budget <= length + penTravel) {
                val travel = (budget - length) / penTravel
                tip = lerp(measure.getPosition(length), measures[index + 1].getPosition(0f), travel * travel * (3f - 2f * travel))
                lift = sin(travel * PI.toFloat())
                trail = emptyList()
            } else if (index == strokes.lastIndex && budget >= length) tip = measure.getPosition(length)
            budget -= length + penTravel
        }
        if (writing in .001f..<1f) {
            trail.forEachIndexed { step, point ->
                val jitter = sin(writing * 120f + step * 2f) * 5f * scale
                drawCircle((if (step % 2 == 0) Lime else Green).copy(alpha = (1f - step / 8f) * .55f), (3.2f - step * .32f) * scale, point + Offset(jitter, -jitter))
            }
        }
        // El lápiz sigue la letra y al terminar sale volando.
        val leaving = (finish / .4f).coerceIn(0f, 1f)
        if (writing > 0f && leaving < 1f) {
            val away = Offset(90f, -90f) * (leaving * leaving) * maxOf(scale, .62f)
            drawPencil(tip + away, maxOf(scale, .62f), sin(writing * 95f) * 7f + leaving * 40f, lift, ((writing * 14f).coerceIn(0f, 1f)) * (1f - leaving))
        }
        if (finish > 0f) {
            val opacity = sin(finish * PI.toFloat()).coerceIn(0f, 1f)
            for (index in 0..11) {
                val angle = index * (PI.toFloat() / 6f)
                val radius = (14f + (index % 3 + 3) * 22f * finish) * scale
                val star = p(747f, 105f) + Offset(cos(angle) * radius * 1.6f, sin(angle) * radius)
                val arm = (index % 2 + 3f) * scale
                drawLine(GreenDark.copy(alpha = opacity), star - Offset(arm, 0f), star + Offset(arm, 0f), 1.5f)
                drawLine(GreenDark.copy(alpha = opacity), star - Offset(0f, arm), star + Offset(0f, arm), 1.5f)
            }
        }
    }
}

/** Lápiz con punta de grafito, madera, cuerpo, virola y borrador. */
private fun DrawScope.drawPencil(tip: Offset, size: Float, wobble: Float, lift: Float, opacity: Float) {
    val raised = tip + Offset(0f, -16f * lift * size)
    if (lift > 0f) drawCircle(Night.copy(alpha = .14f * lift * opacity), 5f * size, tip)
    rotate(wobble, pivot = raised) {
        val along = Offset(.58f, -.81f)
        val across = Offset(.81f, .58f)
        fun at(distance: Float, side: Float) = raised + along * (distance * size) + across * (side * size)
        fun piece(from: Float, to: Float, halfFrom: Float, halfTo: Float, color: Color) {
            val shape = Path().apply {
                at(from, -halfFrom).let { moveTo(it.x, it.y) }
                at(to, -halfTo).let { lineTo(it.x, it.y) }
                at(to, halfTo).let { lineTo(it.x, it.y) }
                at(from, halfFrom).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(shape, color.copy(alpha = opacity))
        }
        piece(0f, 15f, 0f, 5.5f, Color(0xFFEBC594))
        piece(0f, 5.5f, 0f, 2f, Color(0xFF2B2E35))
        piece(15f, 66f, 5.5f, 5.5f, Color(0xFFF2A93B))
        piece(15f, 66f, 1.8f, 1.8f, Color(0xFFFFC765))
        piece(66f, 73f, 5.5f, 5.5f, Color(0xFFB9C5D2))
        piece(73f, 83f, 5.5f, 4.6f, Color(0xFFE66678))
    }
}
