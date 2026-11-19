package com.example.dinex

// ═══════════════════════════════════════════════════════════════════════════
// RECOMENDACIONES DEL REPORTE
// Plan personal de ahorro que acompaña al PDF y al Excel. Todo sale de los
// movimientos reales del periodo (y de las metas de la persona, si las hay):
// nada se inventa. El PDF y el Excel usan este mismo resultado para decir lo mismo.
// ═══════════════════════════════════════════════════════════════════════════

/** Datos de la cuenta que enriquecen las recomendaciones (opcionales). */
internal data class ReportContext(
    val goals: List<SavingsGoal> = emptyList(),
    val savings: Double = 0.0,
    val dailyLimit: Double = 0.0,
)

/** Una recomendación concreta; [impact] es cuánto podría ahorrar y [impactLabel] su unidad. */
internal data class AdviceItem(val title: String, val body: String, val impact: Double? = null, val impactLabel: String = "al mes")

internal data class ReportAdvice(
    /** Salud financiera del periodo, de 0 a 100. */
    val score: Int,
    val level: String,
    val headline: String,
    val summary: String,
    val items: List<AdviceItem>,
    val challengeTitle: String,
    val challenge: String,
    val lessonTitle: String,
    val lesson: String,
    /** Ahorro posible al mes si se siguen las recomendaciones con monto. */
    val potentialMonthly: Double,
)

private const val ANT_LIMIT = 15.0

internal fun reportAdvice(stats: ReportStats, owner: String, context: ReportContext = ReportContext()): ReportAdvice {
    val name = owner.trim().substringBefore(' ').ifBlank { "Hola" }.replaceFirstChar { it.uppercase() }
    val expenses = stats.items.filterNot { it.income }
    val monthCount = stats.months.size.coerceAtLeast(1)
    val monthlyIncome = stats.income / monthCount
    val monthlyExpense = stats.expense / monthCount
    val days = stats.items.map { epochDaysOf(it.year, it.month, it.day) }.let { list ->
        if (list.isEmpty()) 7 else (list.max() - list.min() + 1).coerceAtLeast(7)
    }
    val weeks = days / 7.0
    val rate = stats.savingsRate
    val top = stats.categories.firstOrNull()
    val topShare = if (top != null && stats.expense > 0) top.amount / stats.expense else 0.0
    val ants = expenses.filter { it.amount <= ANT_LIMIT }
    val antShare = if (stats.expense > 0) ants.sumOf { it.amount } / stats.expense else 0.0
    val activeGoals = context.goals.filter { it.target > 0 && it.saved < it.target }

    // Puntaje: parte del porcentaje ahorrado y se ajusta por hábitos.
    var score = when {
        stats.income <= 0 -> 30
        rate < -0.1 -> 12
        rate < 0 -> 28
        rate < 0.1 -> 48
        rate < 0.2 -> 66
        rate < 0.3 -> 82
        else -> 92
    }
    if (antShare > 0.15) score -= 8
    if (topShare > 0.5) score -= 5
    if (context.goals.isNotEmpty()) score += 5
    score = score.coerceIn(5, 100)
    val level = when {
        score >= 80 -> "Excelente"
        score >= 60 -> "Vas bien"
        score >= 40 -> "Puedes mejorar"
        else -> "Atención"
    }

    val headline = when {
        stats.income <= 0 && stats.expense <= 0 -> "$name, aún no hay movimientos en este periodo"
        stats.income <= 0 -> "$name, registra tus ingresos para medir tu ahorro"
        stats.balance < 0 -> "$name, gastaste ${moneyGrouped(-stats.balance)} más de lo que ingresaste"
        else -> "$name, ahorraste el ${fixed(rate * 100, if (rate * 100 < 10) 1 else 0)}% de tus ingresos"
    }
    val summary = when (level) {
        "Excelente" -> "Tienes hábitos sólidos. Ahora haz que tu ahorro trabaje: ponle una meta y no lo toques."
        "Vas bien" -> "Vas por buen camino. Con unos ajustes pequeños puedes llegar al 20 % recomendado."
        "Puedes mejorar" -> "Tu dinero alcanza, pero se escapa en gastos que puedes ordenar. Empieza por un solo cambio."
        else -> "Este periodo tus gastos superaron lo que entró. Prioriza lo esencial y frena los gastos que no planeaste."
    }

    val items = mutableListOf<AdviceItem>()
    // 1. Págate primero (regla del 20 %).
    if (stats.income > 0) {
        if (rate < 0.2) {
            val target = monthlyIncome * 0.2
            val current = (stats.balance / monthCount).coerceAtLeast(0.0)
            items += AdviceItem(
                "Págate primero: separa el 20 %",
                "Apenas recibas dinero, mueve ${moneyGrouped(target)} a tu ahorro antes de gastar. Hoy te quedan en promedio ${moneyGrouped(current)} al mes.",
                (target - current).coerceAtLeast(0.0),
            )
        } else {
            items += AdviceItem(
                "Protege lo que ya ahorraste",
                "Superas el 20 % recomendado. Pasa ${moneyGrouped(stats.balance)} a una meta o a una cuenta aparte para que no se gaste sin darte cuenta.",
            )
        }
    }
    // 2. Recorte en la categoría que más pesa.
    if (top != null && topShare >= 0.25) {
        val cut = top.amount * 0.10 / monthCount
        items += AdviceItem(
            "Recorta 10 % en ${top.label.ifBlank { "tu mayor gasto" }}",
            "${top.label.ifBlank { "Esta categoría" }} se lleva el ${fixed(topShare * 100, 0)}% de lo que gastas. ${categoryTip(top.label)} Un 10 % menos te deja ${moneyGrouped(cut)} al mes.",
            cut,
        )
    }
    // 3. Gastos hormiga.
    if (ants.size >= 4) {
        val total = ants.sumOf { it.amount }
        items += AdviceItem(
            "Ojo con los gastos hormiga",
            "Hiciste ${ants.size} compras pequeñas (hasta ${moneyGrouped(ANT_LIMIT)}) que suman ${moneyGrouped(total)}. Reducirlas a la mitad te deja ${moneyGrouped(total / 2 / monthCount)} al mes.",
            total / 2 / monthCount,
        )
    }
    // 4. Metas: cuánto separar por semana para cumplirlas.
    val goal = activeGoals.minByOrNull { it.target - it.saved }
    if (goal != null) {
        val remaining = goal.target - goal.saved
        val weekly = (if (monthlyIncome > 0) monthlyIncome * 0.2 / 4.33 else remaining / 12).coerceAtLeast(5.0)
        val weeksNeeded = kotlin.math.ceil(remaining / weekly).toInt()
        items += AdviceItem(
            "Acelera tu meta “${displayGoalName(goal.name)}”",
            "Te faltan ${moneyGrouped(remaining)}. Si separas ${moneyGrouped(weekly)} cada semana, la cumples en ${countLabel(weeksNeeded, "semana", "semanas")}.",
            weekly, "por semana",
        )
    } else if (context.goals.isEmpty()) {
        items += AdviceItem(
            "Ponle nombre a tu ahorro",
            "Crea una meta en Dinex (un viaje, un celular o tus estudios). Quien ahorra con un propósito ahorra más y gasta menos por impulso.",
        )
    }
    // 5. Compras grandes: regla de las 24 horas.
    val biggest = stats.largestExpense
    if (biggest != null && monthlyExpense > 0 && biggest.amount >= maxOf(100.0, monthlyExpense * 0.25)) {
        items += AdviceItem(
            "Aplica la regla de las 24 horas",
            "Tu gasto más alto fue “${biggest.title}” (${moneyGrouped(biggest.amount)}). Antes de cualquier compra mayor a ${moneyGrouped(roundTo10(monthlyExpense * 0.15))}, espera un día: evitarás compras por impulso.",
        )
    }
    // 6. Fondo de emergencia.
    if (monthlyExpense > 0 && context.savings < monthlyExpense * 3) {
        items += AdviceItem(
            "Arma tu fondo de emergencia",
            "Lo ideal son 3 meses de gastos: ${moneyGrouped(monthlyExpense * 3)}. Empieza con ${moneyGrouped(roundTo10(monthlyExpense * 0.05).coerceAtLeast(20.0))} al mes y no lo uses para gustos.",
        )
    }
    // 7. Límite diario.
    if (context.dailyLimit > 0) {
        val overDays = expenses.groupBy { epochDaysOf(it.year, it.month, it.day) }.count { (_, list) -> list.sumOf { it.amount } > context.dailyLimit }
        if (overDays > 0) items += AdviceItem(
            "Cuida tu límite diario",
            "Pasaste tu límite de ${moneyGrouped(context.dailyLimit)} en ${countLabel(overDays, "día", "días")}. Revisa la alerta de Dinex antes de pagar y deja las compras no urgentes para mañana.",
        )
    }

    val chosen = items.take(5)
    val potential = chosen.sumOf { item -> item.impact?.let { if (item.impactLabel == "por semana") it * 4.33 else it } ?: 0.0 }

    val (challengeTitle, challenge) = if (top != null && top.amount > 0) {
        val weeklyAverage = top.amount / weeks.coerceAtLeast(1.0)
        "Reto de 7 días" to "Esta semana gasta como máximo ${moneyGrouped(weeklyAverage * 0.9)} en ${top.label.ifBlank { "tu mayor gasto" }} (10 % menos que tu promedio) y registra en Dinex cada sol que ahorres."
    } else {
        "Reto de 7 días" to "Registra cada movimiento durante 7 días seguidos. Lo que se mide, se mejora."
    }
    val lessonTitle = "Regla 50/30/20"
    val lesson = if (monthlyIncome > 0) {
        "Divide tus ingresos: 50 % para necesidades (${moneyGrouped(monthlyIncome * 0.5)}), 30 % para gustos (${moneyGrouped(monthlyIncome * 0.3)}) y 20 % para tu ahorro (${moneyGrouped(monthlyIncome * 0.2)}) cada mes."
    } else {
        "Divide tus ingresos: 50 % para necesidades, 30 % para gustos y 20 % para tu ahorro. Registra tus ingresos en Dinex para calcularlo por ti."
    }
    return ReportAdvice(score, level, headline, summary, chosen, challengeTitle, challenge, lessonTitle, lesson, potential)
}

private fun roundTo10(value: Double): Double = (kotlin.math.ceil(value / 10.0) * 10.0).coerceAtLeast(10.0)

private fun categoryTip(category: String): String = when (category.trim().lowercase()) {
    "comida" -> "Llevar tu almuerzo dos veces por semana y comprar en el mercado ayuda mucho."
    "transporte" -> "Combina rutas, usa transporte público o comparte el taxi cuando puedas."
    "estudios" -> "Busca libros usados, versiones digitales o la biblioteca de tu centro de estudios."
    "entretenimiento" -> "Quédate con una sola suscripción y elige planes gratis algunos fines de semana."
    else -> "Revisa qué compras no estaban planeadas y define un tope mensual."
}
