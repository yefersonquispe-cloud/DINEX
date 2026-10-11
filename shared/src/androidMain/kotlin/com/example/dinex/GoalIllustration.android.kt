package com.example.dinex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal actual fun rememberGoalIllustrator(): GoalIllustrator {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val key = readGeminiApiKey(context)
        object : GoalIllustrator {
            override val isConfigured = key.isNotBlank()
            override suspend fun generate(purpose: String): String = withContext(Dispatchers.IO) {
                check(isConfigured)
                val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 90_000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-goog-api-key", key)
                }
                try {
                    val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", goalImagePrompt(purpose))))))
                        .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))
                    connection.outputStream.use { it.write(body.toString().encodeToByteArray()) }
                    check(connection.responseCode in 200..299) { "No se pudo generar la imagen" }
                    val bytes = connection.inputStream.use { it.readBytes() }
                    check(bytes.size <= 16_000_000)
                    val parts = JSONObject(bytes.decodeToString()).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    val encoded = (0 until parts.length()).firstNotNullOfOrNull { i ->
                        val part = parts.getJSONObject(i)
                        (part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data"))?.optString("data")?.takeIf { it.isNotBlank() }
                    } ?: error("Gemini no devolvió una imagen")
                    val decoded = Base64.decode(encoded, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size) ?: error("Imagen inválida")
                    val ratio = minOf(1f, 900f / maxOf(bitmap.width, bitmap.height))
                    val resized = if (ratio < 1f) bitmap.scale((bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1)) else bitmap
                    val compressed = ByteArrayOutputStream().use { stream -> resized.compress(Bitmap.CompressFormat.JPEG, 85, stream); stream.toByteArray() }
                    if (resized !== bitmap) resized.recycle()
                    bitmap.recycle()
                    Base64.encodeToString(compressed, Base64.NO_WRAP)
                } finally { connection.disconnect() }
            }
        }
    }
}
