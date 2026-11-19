package com.example.dinex

import android.content.Context
import android.content.pm.PackageManager
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
import java.net.HttpURLConnection
import java.net.URL

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
    dinexAiProxyEnabled || firebaseReady(context) || testGeminiKey(context).isNotBlank()

private fun firebaseReady(context: Context) = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

/**
 * Solo durante la prueba (GEMINI_KEY_IN_APK=true): clave de respaldo si Firebase no verifica la app.
 * En la versión final queda vacía y Dinex usa solo Firebase AI Logic.
 */
private fun testGeminiKey(context: Context): String = runCatching {
    context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData?.getString("com.example.dinex.GEMINI_API_KEY").orEmpty().trim()
}.getOrDefault("")

internal suspend fun generateGeminiContent(
    context: Context,
    parts: JSONArray,
    systemPrompt: String,
    maxOutputTokens: Int,
    temperature: Double,
    preferVision: Boolean = false,
): String = withContext(Dispatchers.IO) {
    // Con servidor seguro, la imagen y el texto van al servidor; la clave nunca está en el teléfono.
    if (dinexAiProxyEnabled) return@withContext generateThroughProxy(parts, if (preferVision) "receipt" else "chat")
    var lastError: Throwable? = null
    if (firebaseReady(context)) {
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
    }
    // Respaldo temporal de la etapa de prueba: la clave directa, solo si viene en el APK.
    val key = testGeminiKey(context)
    if (key.isNotBlank()) {
        for (model in geminiModels) {
            try {
                return@withContext requestGeminiWithKey(model, key, parts, systemPrompt, maxOutputTokens, temperature)
            } catch (error: Exception) {
                lastError = error
                android.util.Log.d("DinexAi", "$model con clave falló: ${error::class.simpleName}: ${error.message}")
                if (!error.modelMightBeUnavailable()) break
            }
        }
    }
    throw lastError ?: IllegalStateException("No hay un modelo Gemini disponible")
}

private fun requestGeminiWithKey(
    model: String,
    apiKey: String,
    parts: JSONArray,
    systemPrompt: String,
    maxOutputTokens: Int,
    temperature: Double,
): String {
    val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        .openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 40_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        setRequestProperty("x-goog-api-key", apiKey)
    }
    return try {
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", temperature).put("maxOutputTokens", maxOutputTokens))
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
            error("Error $status al consultar Gemini. ${detail.take(200)}")
        }
        val responseParts = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        val answer = buildString {
            for (index in 0 until (responseParts?.length() ?: 0)) {
                val text = responseParts?.optJSONObject(index)?.optString("text").orEmpty()
                if (text.isNotBlank()) { if (isNotEmpty()) append('\n'); append(text) }
            }
        }.trim()
        if (answer.isBlank()) error("Gemini no devolvió una respuesta de texto")
        answer
    } finally {
        connection.disconnect()
    }
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
