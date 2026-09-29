package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberGeminiAssistant(): GeminiAssistant = remember {
    val apiKey = readIosGeminiApiKey()
    object : GeminiAssistant {
        override val isConfigured = apiKey.isNotBlank()
        override suspend fun ask(message: String, financialContext: String): GeminiReply {
            if (apiKey.isBlank()) return GeminiReply(
                "Configura GEMINI_API_KEY en el xcconfig de iOS para activar Dinex IA.",
                successful = false,
            )
            return runCatching {
                requestIosGemini(
                    apiKey = apiKey,
                    prompt = """
                        Eres Dinex IA, un asistente de finanzas personales para usuarios de Perú. Responde en español claro,
                        con montos en soles y recomendaciones breves, concretas y prudentes. No inventes movimientos.
                        Compara categorías con montos cuando pregunten dónde gastan más. Para evaluar una compra, calcula
                        el saldo posterior y considera recordatorios, límite diario y racha. Para un plan, da tres pasos
                        medibles. No uses Markdown, asteriscos ni encabezados decorativos.

                        RESUMEN FINANCIERO:
                        $financialContext

                        PREGUNTA DEL USUARIO:
                        $message
                    """.trimIndent(),
                )
            }.fold(
                onSuccess = { GeminiReply(it.replace("**", "").replace("###", ""), successful = true) },
                onFailure = {
                    GeminiReply("No pude conectar con Gemini: ${it.message ?: "error de red"}", successful = false)
                },
            )
        }
    }
}
