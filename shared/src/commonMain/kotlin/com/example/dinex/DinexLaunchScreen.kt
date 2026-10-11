package com.example.dinex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
            delay(200)
        } else {
            writing.animateTo(1f, tween(1600, easing = LinearEasing))
            finish.animateTo(1f, tween(600))
            delay(400)
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
                listOf(Color(0xFF16332E).copy(alpha = .6f), Color.Transparent),
                Offset(size.width * .66f, size.height * .43f), size.maxDimension * .62f,
            ))
            // A restrained technical texture, without bitmap branding.
            val spacing = 48.dp.toPx()
            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) {
                    drawCircle(Color.White.copy(alpha = .035f), 1f, Offset(x, y))
                    y += spacing
                }
                x += spacing
            }
        }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 600.dp).fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("TU FUTURO EMPIEZA AQUÍ", color = Gray, fontSize = 10.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(34.dp))
            Box(Modifier.fillMaxWidth().height(125.dp).semantics {
                contentDescription = "Dinex. Tu dinero, con propósito."
                heading()
            }, contentAlignment = Alignment.Center) {
                DrawnDinex(writing.value, finish.value)
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.width(64.dp).height(2.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, Green.copy(alpha = .8f), Color.Transparent)),
            ))
            Spacer(Modifier.height(24.dp))
            Text(
                "Tu dinero, con propósito.",
                color = Night.copy(alpha = .45f + .55f * finish.value),
                fontWeight = FontWeight.Medium, fontSize = 19.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(9.dp))
            Text("Pequeñas decisiones. Grandes mañanas.", color = Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
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
        val total = measures.sumOf { it.length.toDouble() }.toFloat()
        var budget = writing * total
        var tip = p(35f, 30f)
        strokes.forEachIndexed { index, path ->
            val measure = measures[index]
            drawPath(path, Color.White.copy(alpha = .045f), style = Stroke(20f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val portion = budget.coerceIn(0f, measure.length)
            if (portion > 0f) {
                val visible = Path()
                measure.getSegment(0f, portion, visible, true)
                drawPath(visible,
                    Brush.linearGradient(listOf(Night, Night, GreenDark), p(0f, 0f), p(850f, 0f)),
                    style = Stroke((5f + 15f * finish) * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                if (budget <= measure.length) tip = measure.getPosition(portion)
            }
            budget -= measure.length
        }
        if (writing < 1f && writing > 0f) {
            // Pencil follows the exact letter stroke rather than sweeping across it.
            drawLine(Orange, tip + Offset(4f, -4f), tip + Offset(21f, -28f), 5f * scale, StrokeCap.Round)
            drawLine(Night, tip, tip + Offset(4f, -4f), 3f * scale, StrokeCap.Round)
        }
        if (finish > 0f) {
            val wand = p(817f - 130f * (1f - finish), 18f)
            drawLine(Night.copy(alpha = 1f - finish), wand + Offset(12f, 15f), wand + Offset(31f, 35f), 3f * scale, StrokeCap.Round)
            for (index in 0..7) {
                val angle = index * 0.7854f
                val radius = (10f + 35f * finish) * scale
                val star = wand + Offset(cos(angle) * radius, sin(angle) * radius)
                val opacity = sin(finish * 3.14159f).coerceIn(0f, 1f)
                drawLine(GreenDark.copy(alpha = opacity), star - Offset(3f * scale, 0f), star + Offset(3f * scale, 0f), 1.5f)
                drawLine(GreenDark.copy(alpha = opacity), star - Offset(0f, 3f * scale), star + Offset(0f, 3f * scale), 1.5f)
            }
        }
    }
}
