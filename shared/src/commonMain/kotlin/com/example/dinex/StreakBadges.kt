package com.example.dinex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.streak_acero
import dinex.shared.generated.resources.streak_amatista
import dinex.shared.generated.resources.streak_cobre
import dinex.shared.generated.resources.streak_cristal
import dinex.shared.generated.resources.streak_diamante
import dinex.shared.generated.resources.streak_esmeralda
import dinex.shared.generated.resources.streak_leyenda
import dinex.shared.generated.resources.streak_madera
import dinex.shared.generated.resources.streak_obsidiana
import dinex.shared.generated.resources.streak_oro
import dinex.shared.generated.resources.streak_perla
import dinex.shared.generated.resources.streak_piedra
import dinex.shared.generated.resources.streak_plata
import dinex.shared.generated.resources.streak_platino
import dinex.shared.generated.resources.streak_rubi
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Insignia de la billeterita de Dinex para cada nivel de la racha de ahorro. */
private fun streakBadge(level: StreakLevel): DrawableResource = when (level.name) {
    "Plata" -> Res.drawable.streak_plata
    "Oro" -> Res.drawable.streak_oro
    "Platino" -> Res.drawable.streak_platino
    "Diamante" -> Res.drawable.streak_diamante
    "Esmeralda" -> Res.drawable.streak_esmeralda
    "Rubí" -> Res.drawable.streak_rubi
    "Amatista" -> Res.drawable.streak_amatista
    "Obsidiana" -> Res.drawable.streak_obsidiana
    "Perla" -> Res.drawable.streak_perla
    "Madera" -> Res.drawable.streak_madera
    "Piedra" -> Res.drawable.streak_piedra
    "Acero" -> Res.drawable.streak_acero
    "Cristal" -> Res.drawable.streak_cristal
    "Leyenda" -> Res.drawable.streak_leyenda
    else -> Res.drawable.streak_cobre
}

private val lockedFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Muestra la insignia del nivel; si aún no se gana, se ve en gris y apagada. */
@Composable
internal fun StreakBadge(level: StreakLevel, size: Dp, locked: Boolean = false, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(streakBadge(level)),
        contentDescription = if (locked) "Insignia ${level.name} bloqueada" else "Insignia ${level.name}",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = if (locked) lockedFilter else null,
        alpha = if (locked) .38f else 1f,
    )
}

/** Alerta al subir de nivel: la insignia aparece flotando con brillo, rayos y destellos. */
@Composable
internal fun StreakLevelDialog(level: StreakLevel, streak: Int, onDismiss: () -> Unit) {
    val index = streakLevels.indexOf(level).coerceAtLeast(0)
    val next = streakLevels.getOrNull(index + 1)
    val accent = level.color
    val enter = remember { Animatable(0f) }
    LaunchedEffect(level) { enter.animateTo(1f, spring(dampingRatio = .48f, stiffness = Spring.StiffnessLow)) }
    val loop = rememberInfiniteTransition(label = "insignia")
    val float by loop.animateFloat(-9f, 9f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "flotar")
    val tilt by loop.animateFloat(-4f, 4f, infiniteRepeatable(tween(2300), RepeatMode.Reverse), label = "inclinar")
    val glow by loop.animateFloat(.55f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "brillo")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "rayos")
    val twinkle by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "destellos")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 26.dp).fillMaxWidth()
                .graphicsLayer { val s = .82f + .18f * enter.value; scaleX = s; scaleY = s; alpha = enter.value.coerceIn(0f, 1f) },
            shape = RoundedCornerShape(34.dp),
            color = Color(0xFF0B1510),
            border = BorderStroke(2.dp, accent.copy(alpha = .75f)),
            shadowElevation = 30.dp,
        ) {
            Box(Modifier.fillMaxWidth().background(Brush.radialGradient(listOf(accent.copy(alpha = .30f), Color.Transparent), radius = 900f))) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("¡SUBISTE DE NIVEL!", color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Ganaste una nueva medalla de ahorro", color = Color.White.copy(alpha = .72f), fontSize = 12.sp, textAlign = TextAlign.Center)

                    // Insignia flotante con rayos giratorios, halo y destellos alrededor.
                    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().rotate(spin)) {
                            val rays = 14
                            repeat(rays) { i ->
                                drawArc(accent.copy(alpha = .16f * glow), startAngle = i * 360f / rays, sweepAngle = 9f, useCenter = true)
                            }
                            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .55f * glow), Color.Transparent)), radius = size.minDimension * .42f)
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2, size.height / 2)
                            repeat(8) { i ->
                                val phase = (twinkle + i / 8f) % 1f
                                val angle = i * (2 * PI / 8) + twinkle * .6
                                val r = size.minDimension * (.36f + .1f * phase)
                                val p = Offset(c.x + (cos(angle) * r).toFloat(), c.y + (sin(angle) * r).toFloat())
                                drawCircle(Color.White.copy(alpha = (1f - phase) * .9f), radius = 3.5f + 5f * (1f - phase), center = p)
                            }
                        }
                        StreakBadge(
                            level, 190.dp,
                            modifier = Modifier.offset(y = float.dp).graphicsLayer {
                                rotationZ = tilt
                                val pop = .4f + .6f * enter.value
                                scaleX = pop; scaleY = pop
                            },
                        )
                    }

                    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .18f), border = BorderStroke(1.dp, accent.copy(alpha = .6f))) {
                        Text("MEDALLA DE AHORRO · NIVEL ${index + 1} DE ${streakLevels.size}", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(level.name.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = 1.sp)
                    Text("$streak ${if (streak == 1) "día" else "días"} ahorrando seguido", color = Color.White.copy(alpha = .82f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))

                    // Camino: nivel anterior, actual y siguiente.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        streakLevels.getOrNull(index - 1)?.let { StreakBadge(it, 38.dp) }
                        StreakBadge(level, 50.dp)
                        if (next != null) {
                            Icon(DinexNavigationIcons.Chevron, null, tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(16.dp))
                            StreakBadge(next, 38.dp, locked = true)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (next != null) "Siguiente medalla: ${next.name} en ${next.startsAt - streak} ${if (next.startsAt - streak == 1) "día" else "días"}"
                        else "¡Llegaste al nivel máximo! Eres una leyenda del ahorro.",
                        color = Color.White.copy(alpha = .65f), fontSize = 11.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Seguir mi racha", color = Color(0xFF0A0C10), fontWeight = FontWeight.Black, fontSize = 15.sp) }
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
    }
}
