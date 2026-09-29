package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberGeminiAssistant(): GeminiAssistant = remember {
    object : GeminiAssistant {
        override val isConfigured = true
        override suspend fun ask(message: String, financialContext: String) =
            GeminiReply(webFinancialReply(message, financialContext))
    }
}

private fun webFinancialReply(message: String, context: String): String {
    val income = Regex("Ingresos:\\s*(S/\\s*-?[0-9]+(?:\\.[0-9]+)?)").find(context)?.groupValues?.getOrNull(1) ?: "tu ingreso registrado"
    val expenses = Regex("Gastos:\\s*(S/\\s*-?[0-9]+(?:\\.[0-9]+)?)").find(context)?.groupValues?.getOrNull(1) ?: "tus gastos registrados"
    val balance = Regex("Saldo disponible:\\s*(S/\\s*-?[0-9]+(?:\\.[0-9]+)?)").find(context)?.groupValues?.getOrNull(1) ?: "tu saldo actual"
    val question = message.lowercase()
    return when {
        "gasto" in question && ("más" in question || "mayor" in question) -> {
            val categories = context.lineSequence().filter { it.trimStart().startsWith("-") && "de límite" in it }
                .take(5).joinToString("\n") { it.trim() }
            "Este es tu gasto por categoría:\n$categories\nRevisa primero la categoría más cercana a su límite."
        }
        "plan" in question || "ahorr" in question ->
            "Tienes $income de ingresos, $expenses de gastos y $balance disponible. Separa primero un 10% del saldo y usa el límite diario de Dinex para sostener tu racha."
        "puedo" in question || "compr" in question ->
            "Tu saldo registrado es $balance. Antes de comprar, descuenta el monto y confirma que todavía cubres tus recordatorios y varios días de tu límite diario."
        else ->
            "Tu resumen actual muestra $income de ingresos, $expenses de gastos y $balance disponible. Puedo ayudarte a revisar gastos, preparar un plan semanal o evaluar una compra."
    }
}
