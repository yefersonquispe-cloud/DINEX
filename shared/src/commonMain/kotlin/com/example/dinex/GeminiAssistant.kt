package com.example.dinex

import androidx.compose.runtime.Composable

enum class GeminiEngine { NANO, CLOUD, NONE }

enum class NanoStatus { CHECKING, AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

data class GeminiReply(
    val text: String,
    val successful: Boolean = true,
    val engine: GeminiEngine = GeminiEngine.CLOUD,
)

interface GeminiAssistant {
    val isConfigured: Boolean

    /** Estado de Gemini Nano en el dispositivo. En iOS/web siempre es UNAVAILABLE. */
    val nanoStatus: NanoStatus get() = NanoStatus.UNAVAILABLE
    val nanoProgress: Float? get() = null

    /** Motivo legible del estado de Nano, útil para saber por qué no está disponible. */
    val nanoDetail: String? get() = null
    val nanoDevice: String get() = ""

    suspend fun ask(message: String, financialContext: String): GeminiReply

    /** Consulta AICore para saber si Nano está disponible o se puede descargar. */
    suspend fun refreshNanoStatus() = Unit

    /** Descarga y prepara Nano. Devuelve un mensaje de error o null si quedó listo. */
    suspend fun prepareNano(): String? = null

    fun close() = Unit
}

@Composable
expect fun rememberGeminiAssistant(): GeminiAssistant
