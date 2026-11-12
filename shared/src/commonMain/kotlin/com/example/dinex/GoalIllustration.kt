package com.example.dinex

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@Composable
internal fun SavingsGoalDialog(goal: SavingsGoal, onDismiss: () -> Unit, onAdd: () -> Unit, onDelete: () -> Unit = {}) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(goal.name) }, text = {
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Mis metas de ahorro") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Total reservado: ${money(goals.sumOf { it.saved })}", style = MaterialTheme.typography.titleMedium)
            if (goals.isEmpty()) Text("Crea tu primera meta desde el botón +. Puedes ahorrar para varios objetivos a la vez.")
            goals.forEach { goal -> OutlinedButton(onClick = { onOpen(goal.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoalThemeIcon(goal.name, 34.dp)
                    Column(Modifier.weight(1f)) { Text(goal.name); Text("${money(goal.saved)}" + if (goal.target > 0) " de ${money(goal.target)}" else " · Sin meta total") }
                }
            } }
        }
    }, confirmButton = { Button(onClick = onNew) { Text("Nueva meta") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
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
                BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp)) {
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            )
        }
    }
}

@Composable
private fun GoalStat(label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Card, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, color = Gray, fontSize = 9.sp)
            Text(value, color = Night, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
