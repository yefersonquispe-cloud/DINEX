package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberGeminiAssistant(): GeminiAssistant = remember {
    object : GeminiAssistant {
        override val isConfigured = true
        override suspend fun ask(message: String, financialContext: String): GeminiReply {
            val income = Regex("Ingresos:\\s*(S/\\s*-?[0-9]+(?:\\.[0-9]+)?)").find(financialContext)?.groupValues?.getOrNull(1) ?: "tu ingreso"
            val expenses = Regex("Gastos:\\s*(S/\\s*-?[0-9]+(?:\\.[0-9]+)?)").find(financialContext)?.groupValues?.getOrNull(1) ?: "tus gastos"
            val balance = Regex("Saldo disponible:\\s*(S/\\s*-?[0-9]+(?:\\.[0-9]+)?)").find(financialContext)?.groupValues?.getOrNull(1) ?: "tu saldo"
            val answer = if (message.lowercase().contains("ahorr") || message.lowercase().contains("plan"))
                "Tienes $income de ingresos, $expenses de gastos y $balance disponible. Separa primero un 10% y controla tu límite diario para mantener la racha."
            else "Tu resumen registra $income de ingresos, $expenses de gastos y $balance disponible. Puedo ayudarte con un plan, tus gastos o una compra."
            return GeminiReply(answer)
        }
    }
}
