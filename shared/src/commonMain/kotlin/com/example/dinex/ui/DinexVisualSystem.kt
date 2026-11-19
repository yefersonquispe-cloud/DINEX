package com.example.dinex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One decorative animation, drawn behind content without recomposing screens. */
@Composable
internal fun DinexAuroraBackground(modifier: Modifier = Modifier) {
    val motion: State<Float> = if (LocalDinexReducedMotion.current) remember { mutableStateOf(.5f) }
    else rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "ambient drift",
    )
    Canvas(modifier.fillMaxSize().clearAndSetSemantics {}) {
        drawRect(AppBg)
        val drift = motion.value
        drawRect(Brush.radialGradient(
            listOf(Green.copy(alpha = .055f), Color.Transparent),
            Offset(size.width * (.82f - drift * .12f), size.height * .05f), size.maxDimension * .63f,
        ))
        drawRect(Brush.radialGradient(
            listOf(Lime.copy(alpha = .045f), Color.Transparent),
            Offset(size.width * (.04f + drift * .08f), size.height * .42f), size.maxDimension * .52f,
        ))
    }
}

@Composable
internal fun Modifier.dinexEntrance(): Modifier {
    val reduced = LocalDinexReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(380))
    }
    return graphicsLayer {
        alpha = .15f + progress.value * .85f
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

/** Se hunde un poco al presionar, como un botón físico. */
@Composable
internal fun Modifier.dinexPress(interaction: InteractionSource, pressedScale: Float = .97f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalDinexReducedMotion.current
    val scale by animateFloatAsState(if (pressed && !reduced) pressedScale else 1f, spring(stiffness = Spring.StiffnessMedium), label = "presión")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Monto que sube o baja suavemente hasta [target] y termina siempre en el valor exacto. */
@Composable
internal fun rememberCountUp(target: Double, durationMillis: Int = 750): Double {
    val reduced = LocalDinexReducedMotion.current
    var shown by remember { mutableDoubleStateOf(if (reduced) target else 0.0) }
    LaunchedEffect(target, reduced) {
        if (!reduced) {
            val start = shown
            animate(0f, 1f, animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)) { progress, _ ->
                shown = start + (target - start) * progress
            }
        }
        shown = target
    }
    return shown
}

@Composable
internal fun DinexPageHeading(eyebrow: String, title: String, subtitle: String, accent: Color = GreenDark, modifier: Modifier = Modifier) {
    Column(modifier.dinexEntrance(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Text(t(eyebrow).uppercase(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        }
        Text(t(title), color = Night, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8f).sp, lineHeight = 33.sp, modifier = Modifier.semantics { heading() })
        Text(t(subtitle), color = Gray, fontSize = 14.sp, lineHeight = 21.sp)
    }
}
