package com.example.dinex

import androidx.compose.runtime.Composable

data class GeminiReply(
    val text: String,
    val successful: Boolean = true,
)

interface GeminiAssistant {
    val isConfigured: Boolean
    suspend fun ask(message: String, financialContext: String): GeminiReply
}

@Composable
expect fun rememberGeminiAssistant(): GeminiAssistant
