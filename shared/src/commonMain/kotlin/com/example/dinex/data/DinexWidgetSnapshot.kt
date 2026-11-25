package com.example.dinex

import kotlin.math.roundToInt

private const val HIDDEN_AMOUNT = "S/ ••••"
private val WeekDayLetters = listOf("L", "M", "M", "J", "V", "S", "D")

data class DinexWidgetGoal(val name: String, val saved: String, val percent: Int?)

data class DinexWidgetMovement(val title: String, val detail: String, val amount: String, val income: Boolean)

data class DinexWidgetDay(val letter: String, val percent: Int, val today: Boolean)

data class DinexWidgetCashFlowDay(
    val dateIso: String,
    val dayLabel: String,
    val income: Double,
    val expense: Double,
    val runningBalance: Double,
    val isToday: Boolean,
)

/** Lo que muestran los widgets; sin sesión iniciada se queda con estos valores vacíos. */
data class DinexWidgetSnapshot(
    val signedIn: Boolean = false,
    val privateMode: Boolean = false,
    val todayIso: String = "",
    val balance: String = HIDDEN_AMOUNT,
    val monthIncome: String = HIDDEN_AMOUNT,
    val monthExpense: String = HIDDEN_AMOUNT,
    val monthBalance: String = HIDDEN_AMOUNT,
    val monthIncomeNum: Double = 0.0,
    val monthExpenseNum: Double = 0.0,
    val monthBalanceNum: Double = 0.0,
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
    val cashFlowDays: List<DinexWidgetCashFlowDay> = emptyList(),
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
    val weekSpent = (6 downTo 0).map { expensesOn(dateIso(civilDateFromEpochDays(today - it))) }
    val peak = weekSpent.max().takeIf { it > 0 } ?: 1.0

    val year = todayIso.take(4).toInt()
    val monthNum = todayIso.drop(5).take(2).toInt()
    val monthDays = (1..31).filter { validDate(year, monthNum, it) }
    val monthIsoDates = monthDays.map { "$year-${monthNum.toString().padStart(2, '0')}-${it.toString().padStart(2, '0')}" }
    val monthTotals = monthIsoDates.map { date ->
        val items = movements.filter { it.dateIso() == date }
        items.filter { it.income }.sumOf { it.amount } to items.filterNot { it.income }.sumOf { it.amount }
    }
    val running = monthTotals.runningFold(0.0) { acc, day -> acc + day.first - day.second }.drop(1)
    val totalIn = month.filter { it.income }.sumOf { it.amount }
    val totalOut = month.filterNot { it.income }.sumOf { it.amount }
    val netBalance = totalIn - totalOut

    val cashFlowDays = monthIsoDates.mapIndexed { index, date ->
        val dayNum = date.takeLast(2)
        DinexWidgetCashFlowDay(
            dateIso = date,
            dayLabel = dayNum,
            income = monthTotals[index].first,
            expense = monthTotals[index].second,
            runningBalance = running[index],
            isToday = date == todayIso,
        )
    }

    return DinexWidgetSnapshot(
        signedIn = true,
        privateMode = privateMode,
        todayIso = todayIso,
        balance = amount(availableBalance()),
        monthIncome = amount(totalIn),
        monthExpense = amount(totalOut),
        monthBalance = amount(netBalance),
        monthIncomeNum = totalIn,
        monthExpenseNum = totalOut,
        monthBalanceNum = netBalance,
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
        cashFlowDays = cashFlowDays,
        week = (6 downTo 0).map { dayIndex ->
            val day = today - dayIndex
            DinexWidgetDay(WeekDayLetters[(day + 3).mod(7)], (expensesOn(dateIso(civilDateFromEpochDays(day))) / peak * 100).roundToInt(), day == today)
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
