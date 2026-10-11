package com.example.dinex

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal interface GoalIllustrator {
    val isConfigured: Boolean
    suspend fun generate(purpose: String): String
}

@Composable internal expect fun rememberGoalIllustrator(): GoalIllustrator

internal fun goalImagePrompt(purpose: String): String =
    "Create one elegant realistic editorial illustration of this personal savings goal: ${purpose.take(120)}. " +
        "If travel, show a beautiful destination and a small airplane; if a phone, show a modern phone. " +
        "Warm natural light, muted sage and slate colours, clean composition. No text, numbers, logos or people. Landscape 4:3."

@OptIn(ExperimentalResourceApi::class, ExperimentalEncodingApi::class)
@Composable
internal fun SavingsGoalDialog(goal: SavingsGoal, illustrator: GoalIllustrator, onDismiss: () -> Unit, onAdd: () -> Unit, onArtwork: (String) -> Unit, externalBusy: Boolean = false, onDelete: () -> Unit = {}) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val image = remember(goal.artwork) { runCatching { Base64.decode(goal.artwork).decodeToImageBitmap() }.getOrNull() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(goal.name) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (image != null) {
                Image(image, "Imagen generada por IA de ${goal.name}", Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
                Text("Imagen generada por IA", color = Gray)
            } else Text("Visualiza aquí el objetivo para el que estás ahorrando.", color = Gray)
            Text("Ahorrado: ${money(goal.saved)}", style = MaterialTheme.typography.titleLarge)
            if (goal.target > 0) {
                DinexProgress((goal.saved / goal.target).toFloat(), Green)
                Text("Meta: ${money(goal.target)} · Falta ${money((goal.target - goal.saved).coerceAtLeast(0.0))}")
            }
            Text("El ahorro se separa de tu saldo disponible. No se registra como un gasto ni se envía a un banco.", color = Gray)
            if (busy || externalBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Creando la imagen de tu meta…") }
            error?.let { Text(it, color = Red) }
            if (!illustrator.isConfigured) Text("La generación necesita Gemini con acceso a imágenes. En web requiere un servidor; tu ahorro funciona aunque la imagen no esté disponible.", color = Gray)
            OutlinedButton(enabled = !busy && !externalBusy && illustrator.isConfigured && goal.name.isNotBlank(), onClick = {
                busy = true; error = null
                scope.launch {
                    try { onArtwork(illustrator.generate(goal.name)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Throwable) { error = "No se pudo generar la imagen. Revisa internet, acceso a generación de imágenes y cuota de Gemini. Tu ahorro sigue guardado." }
                    finally { busy = false }
                }
            }) { Text(if (image == null) "Generar imagen con IA" else "Cambiar imagen con IA") }
            Text("Solo se envía a Google el nombre de tu meta, nunca tu saldo ni tus movimientos. La generación puede consumir cuota de tu API.", color = Gray, style = MaterialTheme.typography.bodySmall)
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
                Column(Modifier.fillMaxWidth()) { Text(goal.name); Text("${money(goal.saved)}" + if (goal.target > 0) " de ${money(goal.target)}" else " · Sin meta total") }
            } }
        }
    }, confirmButton = { Button(onClick = onNew) { Text("Nueva meta") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
