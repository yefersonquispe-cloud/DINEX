package com.example.dinex

// Acceso a los datos para la burbuja nativa de Oye Dinex (Android). Usa el mismo
// repositorio que la app, así la burbuja y la pantalla principal nunca se mezclan
// entre cuentas: todo se guarda en el perfil de la sesión activa.

/** true si hay una sesión activa en este equipo. */
fun dinexVoiceDataAvailable(): Boolean = DinexRepository().session() != null

/** Finanzas de la sesión activa, o null si nadie inició sesión. */
internal fun voiceFinancialData(): FinancialData? {
    val repository = DinexRepository()
    val email = repository.session() ?: return null
    return repository.profile(email)
}

/** Guarda el movimiento dictado y devuelve su id definitivo (o null sin sesión). */
internal fun saveVoiceMovement(movement: Movement): Int? {
    val repository = DinexRepository()
    val email = repository.session() ?: return null
    val data = repository.profile(email)
    val saved = movement.copy(id = (data.movements.maxOfOrNull { it.id } ?: 0) + 1)
    repository.saveProfile(email, data.copy(movements = listOf(saved) + data.movements))
    return saved.id
}

internal fun saveVoiceReminder(reminder: PaymentReminder): Boolean {
    val repository = DinexRepository()
    val email = repository.session() ?: return false
    val data = repository.profile(email)
    val saved = reminder.copy(id = (data.reminders.maxOfOrNull { it.id } ?: 0) + 1)
    repository.saveProfile(email, data.copy(reminders = listOf(saved) + data.reminders))
    return true
}

/** Deshace el último movimiento guardado por voz (solo si sigue existiendo). */
internal fun undoVoiceMovement(id: Int): Boolean {
    val repository = DinexRepository()
    val email = repository.session() ?: return false
    val data = repository.profile(email)
    if (data.movements.none { it.id == id }) return false
    repository.saveProfile(email, data.copy(movements = data.movements.filterNot { it.id == id }))
    return true
}

/** Compatibilidad: registro directo de una frase (versión anterior de la burbuja). */
fun recordDinexVoiceCommand(text: String): String {
    val data = voiceFinancialData() ?: return "Abre Dinex e inicia sesión para guardar."
    return when (val outcome = VoiceConversation().interpret(text, data)) {
        is VoiceOutcome.Save -> saveVoiceMovement(outcome.draft.movement)?.let { voiceSavedMessage(outcome.draft.movement) } ?: "Abre Dinex e inicia sesión para guardar."
        is VoiceOutcome.SaveReminder -> if (saveVoiceReminder(outcome.reminder)) "Recordatorio guardado: ${outcome.reminder.title}" else "Abre Dinex e inicia sesión para guardar."
        is VoiceOutcome.Confirm -> outcome.prompt
        is VoiceOutcome.Answer -> outcome.text
        is VoiceOutcome.Cancelled -> outcome.text
        is VoiceOutcome.Help -> outcome.text
        is VoiceOutcome.AskAi -> "Abre Dinex IA para preguntar: ${outcome.question}"
    }
}

/** Resultado de un comando de voz, con tipos públicos para la burbuja nativa de Android. */
data class DinexVoiceResult(
    val kind: Kind,
    val text: String,
    /** Filas para mostrar ordenadas (últimos movimientos, mayores gastos). */
    val items: List<String> = emptyList(),
    /** Id del movimiento recién guardado; permite "Deshacer". */
    val savedMovementId: Int? = null,
    /** Pregunta para Dinex IA cuando no es un comando de Oye Dinex. */
    val question: String? = null,
) {
    enum class Kind { ANSWER, SAVED, CONFIRM, REMINDER, CANCELLED, ASK_AI, HELP, NO_SESSION }
}

/**
 * Motor de Oye Dinex para la burbuja: entiende la frase, responde con los datos reales
 * de la cuenta y solo guarda cuando los datos son claros o la persona confirmó.
 */
class DinexVoiceEngine {
    private val conversation = VoiceConversation()
    private var pending: VoiceDraft? = null

    val awaitingConfirmation: Boolean get() = pending != null

    fun handle(text: String): DinexVoiceResult {
        val data = voiceFinancialData() ?: return DinexVoiceResult(DinexVoiceResult.Kind.NO_SESSION, "Abre Dinex e inicia sesión para usar Oye Dinex.")
        return when (val outcome = conversation.interpret(text, data)) {
            is VoiceOutcome.Save -> { pending = null; save(outcome.draft) }
            is VoiceOutcome.Confirm -> {
                pending = outcome.draft
                DinexVoiceResult(DinexVoiceResult.Kind.CONFIRM, outcome.prompt)
            }
            is VoiceOutcome.SaveReminder -> if (saveVoiceReminder(outcome.reminder))
                DinexVoiceResult(DinexVoiceResult.Kind.REMINDER, "Listo: recordatorio “${outcome.reminder.title}” guardado.")
            else DinexVoiceResult(DinexVoiceResult.Kind.NO_SESSION, "Abre Dinex e inicia sesión para guardar.")
            is VoiceOutcome.Answer -> DinexVoiceResult(DinexVoiceResult.Kind.ANSWER, outcome.text, outcome.items)
            is VoiceOutcome.Cancelled -> { pending = null; DinexVoiceResult(DinexVoiceResult.Kind.CANCELLED, outcome.text) }
            is VoiceOutcome.Help -> DinexVoiceResult(DinexVoiceResult.Kind.HELP, outcome.text)
            is VoiceOutcome.AskAi -> DinexVoiceResult(
                DinexVoiceResult.Kind.ASK_AI,
                "Eso se lo puedo preguntar a Dinex IA.",
                question = outcome.question,
            )
        }
    }

    /** Respuesta a los botones Guardar / Cancelar de la confirmación. */
    fun confirm(save: Boolean): DinexVoiceResult {
        val draft = pending ?: return DinexVoiceResult(DinexVoiceResult.Kind.CANCELLED, "No hay nada pendiente.")
        pending = null
        conversation.reset()
        return if (save) save(draft) else DinexVoiceResult(DinexVoiceResult.Kind.CANCELLED, "Listo, no guardé nada.")
    }

    fun undo(movementId: Int): Boolean = undoVoiceMovement(movementId)

    fun reset() { pending = null; conversation.reset() }

    private fun save(draft: VoiceDraft): DinexVoiceResult {
        val id = saveVoiceMovement(draft.movement)
            ?: return DinexVoiceResult(DinexVoiceResult.Kind.NO_SESSION, "Abre Dinex e inicia sesión para guardar.")
        return DinexVoiceResult(DinexVoiceResult.Kind.SAVED, voiceSavedMessage(draft.movement), savedMovementId = id)
    }
}

/** Prefijo público para enviar una pregunta de la burbuja a Dinex IA dentro de la app. */
const val DINEX_VOICE_AI_PREFIX = VOICE_AI_PREFIX
