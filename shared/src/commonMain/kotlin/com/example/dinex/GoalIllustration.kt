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

/** Mensaje cuando el generador gratuito pide esperar (permite 1 imagen cada 45 segundos). */
internal const val GOAL_IMAGE_WAIT = "esperar-generador"

internal interface GoalIllustrator {
    val isConfigured: Boolean
    suspend fun generate(purpose: String): String
}

@Composable internal expect fun rememberGoalIllustrator(): GoalIllustrator

internal fun goalImagePrompt(purpose: String): String =
    "Create one magical cinematic 3D illustration of exactly what this person is saving for: ${purpose.take(120)}. " +
        "Show that object or place as the hero of the image (if travel, the destination; if a phone, a modern phone). " +
        "Glowing light, a few floating gold coins and sparkles, clean composition. No text, numbers, logos or people. Landscape 4:3."

/** Objeto de la meta en inglés: los modelos gratuitos entienden mejor instrucciones cortas en inglés. */
private val goalObjectEn = mapOf(
    "Viaje" to "a suitcase on a tropical beach with an airplane in the sky", "Celular" to "a shiny new smartphone",
    "Tecnología" to "a modern laptop", "Moto" to "a red motorcycle", "Auto" to "a small red car", "Bicicleta" to "a bicycle",
    "Hogar" to "a cozy little house", "Estudios" to "a graduation cap and books", "Libros" to "a stack of books",
    "Juegos" to "a video game controller", "Zapatillas" to "new sneakers", "Ropa" to "new clothes", "Regalo" to "a gift box with a bow",
    "Fondo de emergencia" to "a golden shield", "Música" to "big headphones", "Cámara" to "a photo camera", "Mascota" to "a puppy",
    "Boda" to "a diamond ring", "Negocio" to "a small shop", "Entradas" to "concert tickets", "Televisor" to "a smart TV",
    "Meta" to "a golden trophy", "Ahorro" to "a piggy bank full of gold coins",
)

/** Pide a Gemini (texto) que diga en inglés qué dibujar según el nombre de la meta. */
internal const val GOAL_DESCRIBE_PROMPT = "You turn a personal savings goal written in Spanish into a short English description of the thing or place " +
    "to draw for an illustration, shown alone as a product shot or a landscape. Answer with one line of at most 20 words: " +
    "only the visual description, no people, no text, no quotes."

/**
 * Instrucción para el generador gratuito: lo que la persona quiere lograr es el protagonista.
 * Usa la descripción de Gemini si existe; si no, el objeto del tema y el nombre tal como lo escribió.
 */
internal fun freeGoalImagePrompt(purpose: String, description: String? = null): String {
    val subject = description?.takeIf { it.isNotBlank() }?.trimEnd('.')
        ?: ((goalObjectEn[goalTheme(purpose).label] ?: "a piggy bank full of gold coins") + ", for: " + purpose.take(80))
    return "$subject, still life shot shown alone without any person, 3D render, hero shot, centered, vibrant colors, " +
        "soft cinematic lighting, subtle magical sparkles, highly detailed"
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
                        catch (failure: Throwable) {
                            error = if (failure.message == GOAL_IMAGE_WAIT) "El generador gratuito crea 1 imagen cada 45 segundos. Espera un momento y vuelve a tocar el botón."
                            else "No se pudo crear la imagen con IA. Revisa tu internet e inténtalo otra vez. Tu ahorro sigue guardado."
                        }
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
