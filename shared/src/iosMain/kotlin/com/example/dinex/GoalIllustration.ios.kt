package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable internal actual fun rememberGoalIllustrator(): GoalIllustrator = remember {
    object : GoalIllustrator {
        override val isConfigured = readIosGeminiApiKey().isNotBlank()
        override suspend fun generate(purpose: String): String = requestIosGoalImage(purpose)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private suspend fun requestIosGoalImage(purpose: String): String = suspendCancellableCoroutine { continuation ->
    val key = readIosGeminiApiKey()
    if (key.isBlank()) { continuation.resumeWithException(IllegalStateException("Configura Gemini")); return@suspendCancellableCoroutine }
    val url = NSURL.URLWithString("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent")!!
    val prompt = goalImagePrompt(purpose).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    val body = """{"contents":[{"parts":[{"text":"$prompt"}]}],"generationConfig":{"responseModalities":["TEXT","IMAGE"]}}"""
    val request = NSMutableURLRequest.requestWithURL(url).apply {
        HTTPMethod = "POST"; setValue("application/json", forHTTPHeaderField = "Content-Type"); setValue(key, forHTTPHeaderField = "x-goog-api-key")
        HTTPBody = body.encodeToByteArray().usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = body.encodeToByteArray().size.toULong()) }; setTimeoutInterval(90.0)
    }
    val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
        if (!continuation.isActive) return@dataTaskWithRequest
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        val raw = data?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() }.orEmpty()
        val image = raw.extractJsonString("data")
        if (error != null || status !in 200..299 || image.isNullOrBlank() || image.length > 8_000_000) continuation.resumeWithException(IllegalStateException("No se pudo generar la imagen"))
        else continuation.resume(image)
    }
    continuation.invokeOnCancellation { task.cancel() }; task.resume()
}
