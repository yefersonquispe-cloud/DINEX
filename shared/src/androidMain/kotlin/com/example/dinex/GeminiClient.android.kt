package com.example.dinex

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val fastGeminiModels = listOf(
    "gemini-3.5-flash-lite",
    "gemini-3.6-flash",
    "gemini-3.8-flash",
    "gemini-3.7-flash",
    "gemini-2.5-flash",
)

private val visionGeminiModels = listOf(
    "gemini-3.8-flash",
    "gemini-3.1-pro-preview",
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-2.5-pro",
    "gemini-2.5-flash",
)

internal fun readGeminiApiKey(context: Context): String = runCatching {
    context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData?.getString("com.example.dinex.GEMINI_API_KEY")
        .orEmpty()
        .trim()
}.getOrDefault("")

internal suspend fun generateGeminiContent(
    apiKey: String,
    parts: JSONArray,
    systemPrompt: String,
    maxOutputTokens: Int,
    temperature: Double,
    preferVision: Boolean = false,
): String = withContext(Dispatchers.IO) {
    // Con servidor seguro, la imagen y el texto van al servidor; la clave nunca está en el teléfono.
    if (dinexAiProxyEnabled) return@withContext generateThroughProxy(parts, if (preferVision) "receipt" else "chat")
    var lastError: Throwable? = null
    for (model in if (preferVision) visionGeminiModels else fastGeminiModels) {
        try {
            return@withContext requestGemini(
                model = model,
                apiKey = apiKey,
                parts = parts,
                systemPrompt = systemPrompt,
                maxOutputTokens = maxOutputTokens,
                temperature = temperature,
            )
        } catch (error: GeminiModelUnavailable) {
            lastError = error
        }
    }
    throw lastError ?: IllegalStateException("No hay un modelo Gemini disponible")
}

private fun requestGemini(
    model: String,
    apiKey: String,
    parts: JSONArray,
    systemPrompt: String,
    maxOutputTokens: Int,
    temperature: Double,
): String {
    val connection = (URL(
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
    ).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 40_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        setRequestProperty("x-goog-api-key", apiKey)
    }

    return try {
        val body = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put(
                    "parts", JSONArray().put(JSONObject().put("text", systemPrompt)),
                ),
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", parts)
                    },
                ),
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", temperature)
                    put("maxOutputTokens", maxOutputTokens)
                },
            )
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val response = (if ((status in 200..299)) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if ((status !in 200..299)) {
            val detail = runCatching {
                JSONObject(response).optJSONObject("error")?.optString("message")
            }.getOrNull().orEmpty()
            val message = detail.ifBlank { "Error $status al consultar Gemini" }
            if ((status == 404 || status == 429 || message.contains("quota", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true) ||
                message.contains("not supported", ignoreCase = true))
            ) throw GeminiModelUnavailable(message)
            throw IllegalStateException(message)
        }

        val root = JSONObject(response)
        val responseParts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        val answer = buildString {
            if (responseParts != null) {
                for (index in 0 until responseParts.length()) {
                    val text = responseParts.optJSONObject(index)?.optString("text").orEmpty()
                    if (text.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(text)
                    }
                }
            }
        }.trim()
        if (answer.isBlank()) error("Gemini no devolvió una respuesta de texto")
        answer
    } finally {
        connection.disconnect()
    }
}

private class GeminiModelUnavailable(message: String) : IllegalStateException(message)

/** Envía las partes (texto e imagen) al servidor seguro de Dinex IA con el token de Firebase. */
private suspend fun generateThroughProxy(parts: JSONArray, mode: String): String {
    val token = DinexAiAuth.idToken?.invoke() ?: error("Inicia sesión para usar Dinex IA")
    val body = JSONObject().put("mode", mode).put("parts", parts).toString()
    val response = cloudRequest("POST", DINEX_AI_PROXY_URL, body, token)
    val json = runCatching { JSONObject(response.body) }.getOrNull()
    if (response.status !in 200..299) error(json?.optString("error")?.ifBlank { null } ?: "Error ${response.status} del servidor de Dinex IA")
    return json?.optString("text").orEmpty().ifBlank { error("El servidor de Dinex IA no devolvió texto") }
}
