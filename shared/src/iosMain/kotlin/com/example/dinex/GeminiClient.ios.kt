package com.example.dinex

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val iosFastGeminiModels = listOf(
    "gemini-3.5-flash-lite",
    "gemini-3.6-flash",
    "gemini-3.8-flash",
    "gemini-3.7-flash",
    "gemini-2.5-flash",
)

private val iosVisionGeminiModels = listOf(
    "gemini-3.8-flash",
    "gemini-3.1-pro-preview",
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-2.5-pro",
    "gemini-2.5-flash",
)

internal fun readIosGeminiApiKey(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("GEMINI_API_KEY") as? String)
        .orEmpty()
        .takeUnless { it.startsWith("$(") }
        .orEmpty()
        .trim()

@OptIn(ExperimentalForeignApi::class)
internal suspend fun requestIosGemini(
    apiKey: String,
    prompt: String,
    imageBase64: String? = null,
    maxOutputTokens: Int = 700,
): String {
    var lastFailure: Throwable? = null
    for (model in if (imageBase64 == null) iosFastGeminiModels else iosVisionGeminiModels) {
        try {
            return requestIosGeminiModel(model, apiKey, prompt, imageBase64, maxOutputTokens)
        } catch (failure: IosGeminiModelUnavailable) {
            lastFailure = failure
        }
    }
    throw lastFailure ?: IllegalStateException("No hay un modelo Gemini disponible")
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private suspend fun requestIosGeminiModel(
    model: String,
    apiKey: String,
    prompt: String,
    imageBase64: String?,
    maxOutputTokens: Int,
): String = suspendCancellableCoroutine { continuation ->
    val url = NSURL.URLWithString(
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
    ) ?: run {
        continuation.resumeWithException(IllegalStateException("URL de Gemini inválida"))
        return@suspendCancellableCoroutine
    }
    val parts = buildString {
        append('[')
        if (imageBase64 != null) {
            append("{\"inline_data\":{\"mime_type\":\"image/jpeg\",\"data\":\"")
            append(imageBase64)
            append("\"}},")
        }
        append("{\"text\":\"")
        append(prompt.jsonEscape())
        append("\"}]")
    }
    val body = """{"contents":[{"role":"user","parts":$parts}],"generationConfig":{"temperature":0.15,"maxOutputTokens":$maxOutputTokens}}"""
    val request = NSMutableURLRequest.requestWithURL(url).apply {
        HTTPMethod = "POST"
        setValue("application/json; charset=UTF-8", forHTTPHeaderField = "Content-Type")
        setValue(apiKey, forHTTPHeaderField = "x-goog-api-key")
        HTTPBody = body.encodeToByteArray().toNSData()
        setTimeoutInterval(45.0)
    }
    val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
        if (!continuation.isActive) return@dataTaskWithRequest
        if (error != null) {
            continuation.resumeWithException(IllegalStateException(error.localizedDescription))
            return@dataTaskWithRequest
        }
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        val responseText = data?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() }.orEmpty()
        if ((status !in 200..299)) {
            val detail = responseText.extractJsonString("message") ?: "Error $status al consultar Gemini"
            val failure = if (status == 404 || status == 429 || detail.contains("quota", ignoreCase = true) ||
                detail.contains("not found", ignoreCase = true) || detail.contains("not supported", ignoreCase = true))
                IosGeminiModelUnavailable(detail) else IllegalStateException(detail)
            continuation.resumeWithException(failure)
            return@dataTaskWithRequest
        }
        val answer = responseText.extractJsonString("text")
        if (answer.isNullOrBlank()) continuation.resumeWithException(IllegalStateException("Gemini no devolvió texto"))
        else continuation.resume(answer)
    }
    continuation.invokeOnCancellation { task.cancel() }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

private fun String.jsonEscape(): String = buildString(length + 16) {
    var cursor = 0
    while (cursor < length) {
        when (val char = this@jsonEscape[cursor++]) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}

internal fun String.extractJsonString(field: String): String? {
    val needle = "\"$field\""
    var searchFrom = 0
    while (searchFrom < length) {
        val keyIndex = indexOf(needle, searchFrom)
        if (keyIndex < 0) return null
        var cursor = keyIndex + needle.length
        while (cursor < length && this[cursor].isWhitespace()) cursor++
        if (cursor >= length || this[cursor] != ':') {
            searchFrom = keyIndex + needle.length
            continue
        }
        cursor++
        while (cursor < length && this[cursor].isWhitespace()) cursor++
        if (cursor >= length || this[cursor] != '"') {
            searchFrom = keyIndex + needle.length
            continue
        }
        cursor++
        val result = StringBuilder()
        while (cursor < length) {
            when (val char = this[cursor++]) {
                '"' -> return result.toString()
                '\\' -> {
                    if (cursor >= length) return null
                    when (val escaped = this[cursor++]) {
                        '"' -> result.append('"')
                        '\\' -> result.append('\\')
                        '/' -> result.append('/')
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            if (cursor + 4 > length) return null
                            val code = substring(cursor, cursor + 4).toIntOrNull(16) ?: return null
                            result.append(code.toChar())
                            cursor += 4
                        }
                        else -> result.append(escaped)
                    }
                }
                else -> result.append(char)
            }
        }
        return null
    }
    return null
}

private class IosGeminiModelUnavailable(message: String) : IllegalStateException(message)
