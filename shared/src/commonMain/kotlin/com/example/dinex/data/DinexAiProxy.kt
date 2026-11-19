package com.example.dinex

// ═══════════════════════════════════════════════════════════════════════════
// SERVIDOR SEGURO DE DINEX IA
// Cuando DINEX_AI_PROXY_URL está configurado (local.properties), Gemini se consulta
// a través de una Cloud Function (carpeta functions/). La clave de Gemini vive solo
// en el servidor (Secret Manager), el servidor verifica la sesión de Firebase y
// aplica el límite diario. Así la clave ya no viaja dentro del APK ni de la web.
// ═══════════════════════════════════════════════════════════════════════════

/** Entrega el token de Firebase de la sesión activa; lo configura [DinexViewModel]. */
internal object DinexAiAuth {
    var idToken: (suspend () -> String?)? = null
}

/** true cuando la app usa el servidor seguro en vez de llamar a Gemini con una clave local. */
internal val dinexAiProxyEnabled: Boolean get() = DINEX_AI_PROXY_URL.isNotBlank()

/** Respuesta del servidor; [remaining] es el cupo diario que queda según el servidor. */
internal data class DinexAiProxyReply(val reply: GeminiReply, val remaining: Int? = null)

/**
 * Pregunta a Dinex IA por el servidor seguro. [history] son los últimos mensajes de la
 * conversación para que la IA mantenga el contexto ("¿y en transporte?").
 */
internal suspend fun askDinexAiProxy(
    question: String,
    financialContext: String,
    history: List<ChatMessage> = emptyList(),
    http: suspend (String, String, String?, String?) -> CloudResponse = ::cloudRequest,
): DinexAiProxyReply {
    val token = runCatching { DinexAiAuth.idToken?.invoke() }.getOrNull()
        ?: return DinexAiProxyReply(GeminiReply("Inicia sesión para usar Dinex IA.", successful = false, engine = GeminiEngine.NONE))
    val turns = history.filterNot { it.error }.takeLast(6).joinToString(",", "[", "]") { message ->
        "{\"role\":\"${if (message.user) "user" else "model"}\",\"text\":${jsonString(message.text.take(1_200))}}"
    }
    val body = "{\"mode\":\"chat\",\"question\":${jsonString(question.take(500))}," +
        "\"context\":${jsonString(financialContext.take(6_000))},\"history\":$turns}"
    val response = runCatching { http("POST", DINEX_AI_PROXY_URL, body, token) }.getOrElse {
        return DinexAiProxyReply(GeminiReply("No pude conectar con Dinex IA. Revisa tu internet e inténtalo otra vez.", successful = false, engine = GeminiEngine.NONE))
    }
    val json = runCatching { parseJson(response.body) as? Map<*, *> }.getOrNull().orEmpty()
    val remaining = (json["remaining"] as? Number)?.toInt()
    val text = (json["text"] as? String)?.replace("**", "")?.replace("###", "")?.trim().orEmpty()
    if (response.status in 200..299 && text.isNotBlank()) {
        return DinexAiProxyReply(GeminiReply(text, engine = GeminiEngine.CLOUD), remaining)
    }
    val friendly = when (response.status) {
        401, 403 -> "Tu sesión venció. Vuelve a iniciar sesión para usar Dinex IA."
        429 -> (json["error"] as? String)?.takeIf { it.isNotBlank() }
            ?: "Ya usaste tus $DAILY_AI_QUERIES consultas de hoy. Mañana podrás preguntar otra vez."
        504 -> "Dinex IA tardó demasiado en responder. Inténtalo otra vez."
        else -> "Dinex IA no está disponible en este momento. Inténtalo en unos segundos."
    }
    return DinexAiProxyReply(GeminiReply(friendly, successful = false, engine = GeminiEngine.NONE), remaining)
}

/** Une la conversación reciente con la pregunta, para los motores que reciben un solo texto. */
internal fun questionWithHistory(question: String, history: List<ChatMessage>): String {
    val previous = history.filterNot { it.error }.takeLast(6)
    if (previous.isEmpty()) return question
    return buildString {
        appendLine("Conversación reciente (para entender preguntas como \"¿y en transporte?\"):")
        previous.forEach { appendLine((if (it.user) "Usuario: " else "Dinex IA: ") + it.text.take(600)) }
        appendLine()
        append("Pregunta actual: ").append(question)
    }
}
