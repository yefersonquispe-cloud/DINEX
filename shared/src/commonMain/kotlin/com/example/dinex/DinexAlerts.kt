package com.example.dinex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Compose-observable revision bumped by the Android shell whenever the user may
 * have changed notification permission (dialog result or return from settings).
 */
object DinexNotificationPermission {
    var revision by mutableIntStateOf(0)
        private set

    fun refresh() { revision++ }
}

/** Copy for one real system notification, without platform formatting. */
data class DinexAlertText(val title: String, val body: String)

/**
 * Platform hook for real notifications. Android registers it from MainActivity,
 * so common code and the ViewModel can ask for alerts without owning a Context.
 * Other targets simply leave the sink empty and keep working unchanged.
 */
interface DinexAlertSink {
    fun showBudgetExceeded(spent: Double, limit: Double)
    fun scheduleSaveReminder(enabled: Boolean, hour: Int)
    fun cancelSaveReminder()
}

/** Best-effort bridge: a failed notification must never break saving a movement. */
object DinexAlerts {
    var sink: DinexAlertSink? = null

    fun budgetExceeded(spent: Double, limit: Double) {
        runCatching { sink?.showBudgetExceeded(spent, limit) }
    }

    fun scheduleSaveReminder(enabled: Boolean, hour: Int) {
        runCatching { sink?.scheduleSaveReminder(enabled, hour) }
    }

    fun cancelSaveReminder() {
        runCatching { sink?.cancelSaveReminder() }
    }
}

/** Message shown the moment an expense crosses the daily limit. */
fun dinexBudgetAlert(spent: Double, limit: Double): DinexAlertText {
    val overrun = (spent - limit).coerceAtLeast(0.0)
    return DinexAlertText(
        title = "Superaste tu presupuesto diario",
        body = "Hoy gastaste ${money(spent)} de ${money(limit)}. Te pasaste por ${money(overrun)}. " +
            "Puedes ajustar tu límite o frenar los gastos hasta mañana.",
    )
}

/** Consejos cortos que acompañan al recordatorio; cambian cada día. */
internal val dailySavingTips = listOf(
    "Antes de comprar algo, espera 24 horas; si aún lo quieres, cómpralo.",
    "Ahorra primero y gasta lo que queda, no al revés.",
    "Revisa tus suscripciones y cancela las que no usas.",
    "Llevar tu almuerzo dos veces por semana puede ahorrarte unos S/ 40 al mes.",
    "Guarda las monedas del día en tu alcancía: suman más de lo que crees.",
    "Prueba la regla 50/30/20: necesidades, gustos y ahorro.",
    "Compara precios antes de pagar con Yape o Plin.",
    "Ponle nombre a tu meta: ahorrar para algo concreto es más fácil.",
    "Los gastos hormiga suman: un café diario puede ser S/ 150 al mes.",
    "Un fondo de emergencia de 3 meses te evita endeudarte.",
    "Si recibes un ingreso extra, guarda al menos la mitad.",
    "Paga tus gustos en efectivo: ver el dinero salir ayuda a gastar menos.",
    "Haz una lista antes de ir al mercado y cúmplela.",
    "Revisa tus gastos de la semana en Análisis: ahí está tu fuga.",
)

/**
 * Recordatorio diario que solo llega si la persona no ahorró ese día.
 * El título, el mensaje y el consejo cambian según el día ([day] = días desde 1970),
 * así no llega el mismo texto todos los días.
 */
fun dinexSaveReminderAlert(
    streak: Int,
    goalName: String,
    goalSaved: Double,
    goalTarget: Double,
    day: Int = currentDayIndex(),
): DinexAlertText {
    val days = streak.coerceAtLeast(0)
    val goal = goalName.trim()
    val pick = day.mod(Int.MAX_VALUE)
    fun <T> List<T>.ofToday(shift: Int = 0): T = this[(pick + shift).mod(size)]
    val tip = "Consejo: " + dailySavingTips.ofToday(pick % 5 + 3)
    val hasGoal = goal.isNotBlank()
    val left = (goalTarget - goalSaved).coerceAtLeast(0.0)

    val (title, message) = when {
        days > 0 -> {
            val next = nextStreakLevel(days)
            val dayWord = if (days == 1) "día" else "días"
            val keep = if (hasGoal) "Guarda aunque sea S/ 1 en «$goal» y mantén viva tu racha."
                else "Ahorra hoy aunque sea S/ 1 y mantén viva tu racha."
            listOfNotNull(
                "🔥 Tu racha de $days $dayWord está en peligro" to keep,
                "🔥 $days $dayWord seguidos ahorrando, ¡no la cortes hoy!" to keep,
                "⏰ Aún puedes sumar el día ${days + 1} de tu racha" to "Te queda poco tiempo hoy. $keep",
                next?.let { "🏅 Te faltan ${it.startsAt - days} ${if (it.startsAt - days == 1) "día" else "días"} para la medalla ${it.name}" to keep },
                "💪 Llevas $days $dayWord cuidando tu dinero" to "No pierdas lo que ya lograste. $keep",
            ).ofToday()
        }
        hasGoal && goalTarget > 0 -> {
            val percent = (goalSaved / goalTarget * 100).toInt().coerceIn(0, 100)
            val daily = 5.0
            val daysToGoal = kotlin.math.ceil(left / daily).toInt()
            listOf(
                "Ahorra hoy para «$goal»" to "Aún no guardas nada hoy. Te faltan ${money(left)} para tu meta; suma aunque sea S/ 1.",
                "🎯 «$goal» te espera" to "Te faltan ${money(left)}. Cada sol que guardas hoy te acerca un poco más.",
                "📈 Vas al $percent% de «$goal»" to "Hoy aún no ahorras. Súmale aunque sea S/ 1 y mira cómo sube tu barra.",
                "🗓️ «$goal» en $daysToGoal días" to "Si guardas ${money(daily)} al día, llegas a tu meta en $daysToGoal días.",
            ).ofToday()
        }
        else -> {
            val start = if (hasGoal) "Empieza a llenar «$goal» hoy." else "Cada sol cuenta y tu yo del futuro lo agradece."
            listOf(
                "Ahorra hoy 💚" to "Aún no guardas nada hoy. Ahorra aunque sea S/ 1: $start",
                "🐷 Tu alcancía tiene hambre" to "Dale aunque sea S/ 1 hoy. $start",
                "✨ Pequeños ahorros, grandes logros" to "Guardar S/ 1 todos los días vale más que guardar mucho una vez. $start",
                "🙌 ¿Y si hoy guardas S/ 2?" to "Es menos que un pasaje y te ayuda a empezar tu racha. $start",
                "🔥 Hoy puede ser el día 1 de tu racha" to "Ahorra aunque sea S/ 1 y gana tu primera medalla: Cobre. $start",
            ).ofToday()
        }
    }
    return DinexAlertText(title, "$message $tip")
}

/** 12-hour label used by the reminder settings and the notification detail. */
fun reminderHourLabel(hour: Int): String {
    val safe = hour.coerceIn(0, 23)
    val display = when {
        safe == 0 -> 12
        safe > 12 -> safe - 12
        else -> safe
    }
    return "$display:00 ${if (safe < 12) "am" else "pm"}"
}

/** Everything the Android worker needs to decide whether to nudge the user. */
data class DinexReminderSnapshot(
    val signedIn: Boolean,
    val savedToday: Boolean,
    val streak: Int,
    val goalName: String,
    val goalSaved: Double,
    val goalTarget: Double,
    val enabled: Boolean,
    val hour: Int,
)

/** Reads only the signed-in profile; called by the background reminder worker. */
fun readDinexReminderSnapshot(): DinexReminderSnapshot {
    val repository = DinexRepository()
    val email = repository.session()
    val data = email?.let(repository::profile) ?: FinancialData()
    val goal = data.savingGoals().lastOrNull()
    return DinexReminderSnapshot(
        signedIn = email != null && data.onboardingCompleted,
        savedToday = data.streakDay == currentDayIndex(),
        streak = data.activeStreak(),
        goalName = goal?.name.orEmpty(),
        goalSaved = goal?.saved ?: data.savings,
        goalTarget = goal?.target ?: data.goalTarget,
        enabled = repository.saveReminderEnabled(),
        hour = repository.saveReminderHour(),
    )
}
