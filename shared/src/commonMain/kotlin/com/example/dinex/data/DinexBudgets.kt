package com.example.dinex

import kotlin.math.round

/** Local, explainable estimate based on monthly income and past spending. No remote data transfer. */
internal fun suggestedCategoryBudgets(movements: List<Movement>, history: List<Movement> = movements): Map<String, Double> {
    val income = movements.filter { it.income && it.amount.isFinite() && it.amount > 0 }.sumOf { it.amount }
    val baseline = mapOf("Comida" to .35, "Transporte" to .20, "Estudios" to .20, "Entretenimiento" to .10, "Otros" to .15)
    val pastExpenses = history.filter { !it.income && !it.isInCurrentMonth() && it.amount.isFinite() && it.amount > 0 }
    val pastTotal = pastExpenses.sumOf { it.amount }
    var allocated = 0.0
    return categories.mapIndexed { index, category ->
        val limit = if (index == categories.lastIndex) income - allocated else {
            val pastShare = if (pastTotal > 0) pastExpenses.filter { it.category == category.name }.sumOf { it.amount } / pastTotal else 0.0
            val weight = if (pastTotal > 0) .7 * (baseline[category.name] ?: 0.0) + .3 * pastShare else baseline[category.name] ?: 0.0
            (round(income * weight * 100.0) / 100.0).also { allocated += it }
        }
        category.name to limit.coerceAtLeast(0.0)
    }.toMap()
}

internal fun categoryForReminder(title: String): String {
    val value = title.lowercase()
    return when {
        listOf("comida", "mercado", "restaurante", "delivery", "almuerzo", "cena").any(value::contains) -> "Comida"
        listOf("pasaje", "taxi", "bus", "combustible", "transporte", "gasolina").any(value::contains) -> "Transporte"
        listOf("curso", "pension", "pensión", "instituto", "universidad", "libro", "colegio", "matricula", "matrícula").any(value::contains) -> "Estudios"
        listOf("spotify", "netflix", "cine", "juego", "musica", "música", "steam").any(value::contains) -> "Entretenimiento"
        else -> "Otros"
    }
}
