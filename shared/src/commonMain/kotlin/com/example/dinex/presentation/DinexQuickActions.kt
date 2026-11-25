package com.example.dinex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Accesos directos que llegan desde fuera de la app, como los botones de los widgets. */
enum class DinexQuickAction { EXPENSE, INCOME, SAVE, GOALS, PLAN, HISTORY, YUPAY }

object DinexQuickActions {
    var pending by mutableStateOf<DinexQuickAction?>(null)

    /** Recibe el nombre enviado por el widget; ignora cualquier valor desconocido. */
    fun request(name: String?) {
        pending = DinexQuickAction.entries.firstOrNull { it.name == name } ?: return
    }

    /** Entrega la acción una sola vez, para que no se repita al girar la pantalla. */
    fun consume(): DinexQuickAction? = pending.also { pending = null }
}
