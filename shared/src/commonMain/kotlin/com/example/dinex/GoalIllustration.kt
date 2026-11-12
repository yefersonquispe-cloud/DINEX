package com.example.dinex

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SavingsGoalDialog(goal: SavingsGoal, onDismiss: () -> Unit, onAdd: () -> Unit, onDelete: () -> Unit = {}) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(goal.name) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalArtwork(goal)
            Text("Ilustración creada por DINEX según tu meta. Las monedas crecen mientras más ahorras.", color = Gray, style = MaterialTheme.typography.bodySmall)
            Text("Ahorrado: ${money(goal.saved)}", style = MaterialTheme.typography.titleLarge)
            if (goal.target > 0) {
                DinexProgress((goal.saved / goal.target).toFloat(), Green)
                Text("Meta: ${money(goal.target)} · Falta ${money((goal.target - goal.saved).coerceAtLeast(0.0))}")
            }
            Text("El ahorro se separa de tu saldo disponible. No se registra como un gasto ni se envía a un banco.", color = Gray)
            TextButton(onClick = onDelete) { Text("Eliminar meta de ahorro", color = Red) }
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
