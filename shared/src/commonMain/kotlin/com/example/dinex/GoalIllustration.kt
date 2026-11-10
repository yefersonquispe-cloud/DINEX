package com.example.dinex

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Mensaje que usan las plataformas cuando la cuenta de Gemini no tiene cuota para imágenes. */
internal const val GOAL_IMAGE_QUOTA = "cuota-imagenes"

internal interface GoalIllustrator {
    val isConfigured: Boolean
    suspend fun generate(purpose: String): String
}

@Composable internal expect fun rememberGoalIllustrator(): GoalIllustrator

internal fun goalImagePrompt(purpose: String): String =
    "Create one elegant realistic editorial illustration of this personal savings goal: ${purpose.take(120)}. " +
        "If travel, show a beautiful destination and a small airplane; if a phone, show a modern phone. " +
        "Warm natural light, muted sage and slate colours, clean composition. No text, numbers, logos or people. Landscape 4:3."

/** Texto para el generador gratuito: en inglés (lo entiende mejor) y con estilo mágico y cinematográfico. */
internal fun freeGoalImagePrompt(purpose: String): String {
    val theme = goalTheme(purpose).label
    return "Magical cinematic 3D illustration of a personal savings dream: ${purpose.take(80)} ($theme). " +
        "The dream object glowing in the center, floating gold coins, sparkles and soft volumetric light, " +
        "dreamy teal and emerald background, Pixar style, vibrant, highly detailed, no text, no letters, no people."
}

@OptIn(ExperimentalResourceApi::class, ExperimentalEncodingApi::class)
@Composable
internal fun SavingsGoalDialog(goal: SavingsGoal, illustrator: GoalIllustrator, onDismiss: () -> Unit, onAdd: () -> Unit, onArtwork: (String) -> Unit, externalBusy: Boolean = false, onDelete: () -> Unit = {}) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val image = remember(goal.artwork) { runCatching { Base64.decode(goal.artwork).decodeToImageBitmap() }.getOrNull() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(goal.name) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                if (image != null) MagicAiImage(image, goal.name) else GoalArtwork(goal)
                if (busy || externalBusy) MagicLoading()
            }
            Text(if (image != null) "Imagen creada con IA para tu meta." else "Ilustración mágica creada por DINEX. Las monedas crecen mientras más ahorras.",
                color = Gray, style = MaterialTheme.typography.bodySmall)
            Text("Ahorrado: ${money(goal.saved)}", style = MaterialTheme.typography.titleLarge)
            if (goal.target > 0) {
                DinexProgress((goal.saved / goal.target).toFloat(), Green)
                Text("Meta: ${money(goal.target)} · Falta ${money((goal.target - goal.saved).coerceAtLeast(0.0))}")
            }
            Text("El ahorro se separa de tu saldo disponible. No se registra como un gasto ni se envía a un banco.", color = Gray)
            error?.let { Text(it, color = Orange, style = MaterialTheme.typography.bodySmall) }
            if (illustrator.isConfigured) {
                OutlinedButton(enabled = !busy && !externalBusy && goal.name.isNotBlank(), onClick = {
                    busy = true; error = null
                    scope.launch {
                        try { onArtwork(illustrator.generate(goal.name)) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Throwable) { error = "No se pudo crear la imagen con IA. Revisa tu internet e inténtalo otra vez. Tu ahorro sigue guardado." }
                        finally { busy = false }
                    }
                }) { Text(if (image == null) "✨ Crear imagen mágica con IA" else "✨ Crear otra imagen con IA") }
                Text("Es gratis. Solo se envía el nombre de tu meta (a Google Gemini o a Pollinations.ai), nunca tu saldo ni tus movimientos. La versión gratuita puede llevar una pequeña marca de agua.",
                    color = Gray, style = MaterialTheme.typography.bodySmall)
            }
            if (image != null) TextButton(onClick = { onArtwork("") }) { Text("Usar la ilustración de DINEX", color = GreenDark) }
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
