package com.example.dinex

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@Composable
internal fun SavingsGoalDialog(goal: SavingsGoal, onDismiss: () -> Unit, onAdd: () -> Unit, onDelete: () -> Unit = {}) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(displayGoalName(goal.name), fontWeight = FontWeight.Black) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalArtwork(goal)
            Text("Ilustración creada por DINEX según tu meta. Las monedas crecen mientras más ahorras.", color = Gray, style = MaterialTheme.typography.bodySmall)
            GoalProgress(goal)
            Text("El ahorro se separa de tu saldo disponible. No se registra como un gasto ni se envía a un banco.", color = Gray)
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Eliminar meta de ahorro", color = Red) }
        }
    }, confirmButton = { Button(onClick = onAdd) { Text("Agregar ahorro") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
internal fun SavingsGoalsDialog(goals: List<SavingsGoal>, onOpen: (Int) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    val total = goals.sumOf { it.saved }
    val target = goals.sumOf { it.target }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text("Mis metas de ahorro", fontWeight = FontWeight.Black)
                Text("Toca una meta para ver su avance o agregarle ahorro.", color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Normal)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Resumen de todas las metas.
                Surface(shape = RoundedCornerShape(18.dp), color = Mint.copy(alpha = .6f), border = BorderStroke(1.dp, Border)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("TOTAL RESERVADO", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(money(total), color = GreenDark, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            if (target > 0) Text("de ${money(target)} entre todas tus metas", color = Gray, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${goals.size}", color = Night, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            Text(if (goals.size == 1) "meta" else "metas", color = Gray, fontSize = 11.sp)
                        }
                    }
                }
                if (goals.isEmpty()) Text("Aún no tienes metas. Crea la primera y separa tu ahorro de tu saldo disponible.", color = Gray, fontSize = 13.sp)
                goals.forEach { goal ->
                    val p = if (goal.target > 0) (goal.saved / goal.target).toFloat().coerceIn(0f, 1f) else 0f
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Abrir ${goal.name}") { onOpen(goal.id) },
                        shape = RoundedCornerShape(16.dp), color = Card, border = BorderStroke(1.dp, Border),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            GoalAvatar(goal.name, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(displayGoalName(goal.name), color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                    if (goal.target > 0) Text(percentText(p), color = if (p >= 1f) Lime else GreenDark, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                }
                                Text(if (goal.target > 0) "${money(goal.saved)} de ${money(goal.target)}" else "${money(goal.saved)} · sin meta total", color = Gray, fontSize = 11.sp)
                                if (goal.target > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Border.copy(alpha = .7f))) {
                                        Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Green, Lime))))
                                    }
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(DinexNavigationIcons.Chevron, null, tint = Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Nueva meta", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = GreenDark) } })
}

private fun percentText(p: Float): String = when {
    p <= 0f -> "0%"
    p < .01f -> "<1%"
    else -> "${(p * 100).toInt()}%"
}

/** Avance de la meta: monto que sube animado, barra con hitos y datos para seguir ahorrando. */
@Composable
internal fun GoalProgress(goal: SavingsGoal) {
    val target = goal.target
    val progress = if (target > 0) (goal.saved / target).toFloat().coerceIn(0f, 1f) else 0f
    val bar by animateFloatAsState(progress, tween(1200, easing = FastOutSlowInEasing), label = "barra")
    val shown by animateFloatAsState(goal.saved.toFloat(), tween(1200, easing = FastOutSlowInEasing), label = "monto")
    val remaining = (target - goal.saved).coerceAtLeast(0.0)
    Surface(shape = RoundedCornerShape(20.dp), color = Mint.copy(alpha = .55f), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("AHORRADO", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(money(shown.toDouble()), color = GreenDark, fontWeight = FontWeight.Black, fontSize = 26.sp)
                        if (target > 0) Text("  de ${money(target)}", color = Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                if (target > 0) Surface(shape = RoundedCornerShape(50), color = if (progress >= 1f) Lime else GreenDark) {
                    Text(percentText(progress), color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
            if (target > 0) {
                // Barra con hitos al 25 %, 50 %, 75 % y 100 %.
                BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 11.dp)) {
                    val w = maxWidth
                    Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(50)).background(Border.copy(alpha = .6f)))
                    Box(
                        Modifier.width(w * bar).height(10.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Green, Lime))),
                    )
                    listOf(.25f, .5f, .75f, 1f).forEach { mark ->
                        val reached = bar >= mark
                        Box(
                            Modifier.offset(x = w * mark - 11.dp).size(22.dp).align(Alignment.CenterStart).clip(CircleShape)
                                .background(if (reached) GreenDark else Card).border(1.5.dp, if (reached) GreenDark else Border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (reached) Icon(DinexNavigationIcons.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            else Text("${(mark * 100).toInt()}", color = Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val next = listOf(.25, .5, .75, 1.0).firstOrNull { goal.saved < target * it }
                val pace = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).firstOrNull { remaining / it <= 180 } ?: 100.0
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GoalStat("Falta", money(remaining))
                    if (next != null) GoalStat("Próximo hito ${(next * 100).toInt()}%", money(target * next - goal.saved))
                    if (remaining > 0) GoalStat("Ritmo", "${money(pace)} al día · ${kotlin.math.ceil(remaining / pace).toInt()} días")
                }
            }
            Text(
                when {
                    target <= 0 -> "Ponle una meta total para ver tu avance y tus hitos."
                    progress >= 1f -> "🎉 ¡Meta cumplida! Ya reuniste todo lo que necesitabas."
                    progress >= .75f -> "¡Ya casi! Te falta muy poco para lograrlo."
                    progress >= .5f -> "¡Pasaste la mitad! Sigue así."
                    progress >= .25f -> "Vas avanzando: ya superaste el primer hito."
                    goal.saved > 0 -> "¡Buen comienzo! Cada sol que guardas suma."
                    else -> "Empieza hoy con lo que puedas, aunque sea S/ 1."
                },
                color = NightSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GoalStat(label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Card, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Gray, fontSize = 9.sp)
            Text(value, color = Night, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
