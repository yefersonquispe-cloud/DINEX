package com.example.dinex

import kotlin.math.roundToInt

private const val HIDDEN_AMOUNT = "S/ ••••"
private val WeekDayLetters = listOf("L", "M", "M", "J", "V", "S", "D")

data class DinexWidgetGoal(val name: String, val saved: String, val percent: Int?)

data class DinexWidgetMovement(val title: String, val detail: String, val amount: String, val income: Boolean)

data class DinexWidgetDay(val letter: String, val percent: Int, val today: Boolean)

/** Lo que muestran los widgets; sin sesión iniciada se queda con estos valores vacíos. */
data class DinexWidgetSnapshot(
    val signedIn: Boolean = false,
    val privateMode: Boolean = false,
    val balance: String = HIDDEN_AMOUNT,
    val monthIncome: String = HIDDEN_AMOUNT,
    val monthExpense: String = HIDDEN_AMOUNT,
    val savings: String = HIDDEN_AMOUNT,
    val todaySpent: String = HIDDEN_AMOUNT,
    val dailyLimit: String = HIDDEN_AMOUNT,
    val todayStatus: String = "",
    val todayPercent: Int = 0,
    val overBudget: Boolean = false,
    val streakDays: Int = 0,
    val streakLabel: String = "",
    val savedToday: Boolean = false,
    val goals: List<DinexWidgetGoal> = emptyList(),
    val recent: List<DinexWidgetMovement> = emptyList(),
    val week: List<DinexWidgetDay> = emptyList(),
    val weekTotal: String = HIDDEN_AMOUNT,
)

/** Resume la cuenta para la pantalla de inicio; en modo privado no muestra montos ni compras. */
internal fun FinancialData.widgetSnapshot(privateMode: Boolean = false, today: Int = currentDayIndex()): DinexWidgetSnapshot {
    fun amount(value: Double) = if (privateMode) HIDDEN_AMOUNT else moneyGrouped(value)
    fun expensesOn(day: String) = movements.filter { !it.income && it.dateIso() == day }.sumOf { it.amount }

    val todayIso = dateIso(civilDateFromEpochDays(today))
    val yesterdayIso = dateIso(civilDateFromEpochDays(today - 1))
    val month = movements.filter { it.dateIso().startsWith(todayIso.take(7)) }
    val spent = expensesOn(todayIso)
    val limit = dailyLimit.toDouble()
    val over = spent > limit
    val days = (6 downTo 0).map { today - it }
    val weekSpent = days.map { expensesOn(dateIso(civilDateFromEpochDays(it))) }
    val peak = weekSpent.max().takeIf { it > 0 } ?: 1.0

    return DinexWidgetSnapshot(
        signedIn = true,
        privateMode = privateMode,
        balance = amount(availableBalance()),
        monthIncome = amount(month.filter { it.income }.sumOf { it.amount }),
        monthExpense = amount(month.filterNot { it.income }.sumOf { it.amount }),
        savings = amount(savings),
        todaySpent = amount(spent),
        dailyLimit = amount(limit),
        todayStatus = if (over) "Te pasaste por ${amount(spent - limit)}" else "Te quedan ${amount(limit - spent)}",
        todayPercent = if (limit > 0) (spent / limit * 100).roundToInt() else 0,
        overBudget = over,
        streakDays = activeStreak(today),
        streakLabel = countLabel(activeStreak(today), "día", "días"),
        savedToday = streakDay == today,
        goals = savingGoals()
            .sortedWith(compareBy<SavingsGoal> { it.target > 0 && it.saved >= it.target }.thenByDescending { if (it.target > 0) it.saved / it.target else 0.0 })
            .take(3)
            .map { goal ->
                val hasTarget = goal.target > 0
                DinexWidgetGoal(
                    name = displayGoalName(goal.name),
                    saved = if (hasTarget && !privateMode) "${amount(goal.saved)} de ${amount(goal.target)}" else amount(goal.saved),
                    percent = if (hasTarget) (goal.saved / goal.target * 100).toInt().coerceIn(0, 100) else null,
                )
            },
        recent = if (privateMode) emptyList() else movements
            .sortedWith(compareByDescending<Movement> { it.dateIso() }.thenByDescending { it.id })
            .take(3)
            .map { item ->
                val day = when (item.dateIso()) { todayIso -> "Hoy"; yesterdayIso -> "Ayer"; else -> item.dateLabel() }
                DinexWidgetMovement(item.title, "$day · ${item.category}", (if (item.income) "+ " else "− ") + moneyGrouped(item.amount), item.income)
            },
        week = days.mapIndexed { index, day ->
            DinexWidgetDay(WeekDayLetters[(day + 3).mod(7)], (weekSpent[index] / peak * 100).roundToInt(), day == today)
        },
        weekTotal = amount(weekSpent.sum()),
    )
}

/** Solo se expone el perfil con sesión iniciada en este equipo. */
fun readDinexWidgetSnapshot(): DinexWidgetSnapshot {
    val repository = DinexRepository()
    val email = repository.session() ?: return DinexWidgetSnapshot()
    return repository.profile(email).widgetSnapshot(privateMode = repository.widgetPrivate())
}

/** Oculta o vuelve a mostrar los montos en los widgets. */
fun toggleDinexWidgetPrivacy() {
    val repository = DinexRepository()
    repository.setWidgetPrivate(!repository.widgetPrivate())
}
