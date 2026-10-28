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

/**
 * Duolingo-style nudge sent only when the person has not saved that day.
 * The streak version adds urgency; the goal version mentions the next goal.
 */
fun dinexSaveReminderAlert(streak: Int, goalName: String, goalSaved: Double, goalTarget: Double): DinexAlertText {
    val days = streak.coerceAtLeast(0)
    val goal = goalName.trim()
    return when {
        days > 0 -> DinexAlertText(
            title = "🔥 Tu racha de $days ${if (days == 1) "día" else "días"} está en peligro",
            body = "Ahorra hoy aunque sea S/ 1 y mantén viva tu racha. " +
                "No dejes que se apague por un día.",
        )
        goal.isNotBlank() && goalTarget > 0 -> DinexAlertText(
            title = "Ahorra hoy para «$goal»",
            body = "Aún no guardas nada hoy. Te faltan ${money((goalTarget - goalSaved).coerceAtLeast(0.0))} " +
                "para tu meta; suma aunque sea S/ 1.",
        )
        else -> DinexAlertText(
            title = "Ahorra hoy 💚",
            body = "Aún no guardas nada hoy. Ahorra aunque sea S/ 1: " +
                "cada sol cuenta y tu yo del futuro lo agradece.",
        )
    }
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
