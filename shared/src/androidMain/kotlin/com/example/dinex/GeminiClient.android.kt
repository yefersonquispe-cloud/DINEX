package com.example.dinex

import android.content.Context
import android.util.Base64
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Modelos del plan gratis (Spark) de Firebase AI Logic. Flash-Lite va primero: lee fotos y texto en
// pocos segundos; Flash queda de respaldo si el primero no está disponible.
private val geminiModels = listOf(
    "gemini-3.5-flash-lite",
    "gemini-3.8-flash",
)

/**
 * Gemini en la nube está listo cuando Firebase se inició con google-services.json.
 * La clave de Gemini ya no viaja en el APK: Firebase AI Logic la guarda en el servidor
 * y App Check confirma que la consulta viene de la app real.
 */
internal fun cloudGeminiAvailable(context: Context): Boolean =
    dinexAiProxyEnabled || runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

internal suspend fun generateGeminiContent(
    parts: JSONArray,
    systemPrompt: String,
    maxOutputTokens: Int,
    temperature: Double,
    preferVision: Boolean = false,
): String = withContext(Dispatchers.IO) {
    // Con servidor seguro, la imagen y el texto van al servidor; la clave nunca está en el teléfono.
    if (dinexAiProxyEnabled) return@withContext generateThroughProxy(parts, if (preferVision) "receipt" else "chat")
    var lastError: Throwable? = null
    for (model in geminiModels) {
        try {
            return@withContext requestFirebaseAi(model, parts, systemPrompt, maxOutputTokens, temperature)
        } catch (error: Exception) {
            lastError = error
            android.util.Log.d("DinexAi", "$model falló: ${error::class.simpleName}: ${error.message}")
            // Si App Check rechaza la app o no hay internet, otro modelo tampoco funcionará.
            if (!error.modelMightBeUnavailable()) break
        }
    }
    throw lastError ?: IllegalStateException("No hay un modelo Gemini disponible")
}

private suspend fun requestFirebaseAi(
    model: String,
    parts: JSONArray,
    systemPrompt: String,
    maxOutputTokens: Int,
    temperature: Double,
): String {
    val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = model,
        generationConfig = generationConfig {
            this.temperature = temperature.toFloat()
            this.maxOutputTokens = maxOutputTokens
        },
        systemInstruction = content { text(systemPrompt) },
        // Igual que antes: si Gemini no responde en 45 segundos, se usa el análisis del teléfono.
        requestOptions = RequestOptions(timeoutInMillis = 45_000L),
    )
    val prompt = content {
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            val image = part.optJSONObject("inline_data")
            if (image != null) {
                inlineData(Base64.decode(image.optString("data"), Base64.NO_WRAP), image.optString("mime_type", "image/jpeg"))
            } else {
                part.optString("text").takeIf { it.isNotBlank() }?.let { text(it) }
            }
        }
    }
    val answer = generativeModel.generateContent(prompt).text.orEmpty().trim()
    if (answer.isBlank()) error("Gemini no devolvió una respuesta de texto")
    return answer
}

private fun Throwable.modelMightBeUnavailable(): Boolean {
    val text = "${this::class.simpleName} ${message.orEmpty()}"
    return listOf("not found", "not supported", "quota", "429", "404", "Quota", "Unsupported").any { it in text }
}

/** Envía las partes (texto e imagen) al servidor seguro de Dinex IA con el token de Firebase. */
private suspend fun generateThroughProxy(parts: JSONArray, mode: String): String {
    val token = DinexAiAuth.idToken?.invoke() ?: error("Inicia sesión para usar Dinex IA")
    val body = JSONObject().put("mode", mode).put("parts", parts).toString()
    val response = cloudRequest("POST", DINEX_AI_PROXY_URL, body, token)
    val json = runCatching { JSONObject(response.body) }.getOrNull()
    if (response.status !in 200..299) error(json?.optString("error")?.ifBlank { null } ?: "Error ${response.status} del servidor de Dinex IA")
    return json?.optString("text").orEmpty().ifBlank { error("El servidor de Dinex IA no devolvió texto") }
}
