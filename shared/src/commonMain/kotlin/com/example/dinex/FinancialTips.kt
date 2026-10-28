package com.example.dinex

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

internal enum class TipTopic(val label: String) { Savings("Ahorro"), Spending("Gastos"), Goals("Metas"), Habits("Hábitos") }

/** What the button under a tip does; the app shell decides how. */
internal enum class TipAction(val label: String) {
    AddMovement("Registrar"),
    Save("Ahorrar ahora"),
    Plan("Ver mi plan"),
    Analysis("Ver mis gastos"),
    Assistant("Preguntar a Dinex IA"),
}

/** Higher priority shows first: 3 urgent, 2 worth doing, 1 nice to know, 0 good news. */
internal data class FinancialTip(
    val id: String,
    val topic: TipTopic,
    val priority: Int,
    val title: String,
    val body: String,
    val action: TipAction? = null,
)

internal data class HealthFactor(val label: String, val detail: String, val points: Int, val max: Int)

internal data class FinancialHealth(val score: Int, val factors: List<HealthFactor>) {
    val label get() = when {
        score >= 70 -> "Saludable"
        score >= 40 -> "En camino"
        else -> "Necesita atención"
    }
}

private data class MonthNumbers(
    val income: Double,
    val expenses: Double,
    val expenseItems: List<Movement>,
    val overLimitDays: Int,
    val daysSoFar: Int,
)

private fun FinancialData.monthNumbers(): MonthNumbers {
    val month = movements.filter { it.isInCurrentMonth() && it.amount.isFinite() && it.amount > 0 }
    val expenseItems = month.filter { !it.income }
    val overLimitDays = expenseItems.groupBy { it.day }.count { (_, items) -> items.sumOf { it.amount } > dailyLimit }
    return MonthNumbers(
        income = month.filter { it.income }.sumOf { it.amount },
        expenses = expenseItems.sumOf { it.amount },
        expenseItems = expenseItems,
        overLimitDays = overLimitDays,
        daysSoFar = currentDayOfMonth(),
    )
}

/** A 0–100 score built from four habits, each explained so the person knows what to improve. */
internal fun financialHealth(data: FinancialData, today: Int = currentDayIndex()): FinancialHealth {
    val month = data.monthNumbers()
    val rate = if (month.income > 0) (month.income - month.expenses) / month.income else 0.0
    val savingPoints = (rate / .20 * 30).coerceIn(0.0, 30.0).roundToInt()
    val budgetPoints = if (month.expenseItems.isEmpty()) 15
        else ((1 - month.overLimitDays.toDouble() / month.daysSoFar.coerceAtLeast(1)) * 30).coerceIn(0.0, 30.0).roundToInt()
    val monthlyExpenses = month.expenses.takeIf { it > 0 } ?: 1.0
    val cushionMonths = data.savings / monthlyExpenses
    val cushionPoints = (cushionMonths / 3 * 20).coerceIn(0.0, 20.0).roundToInt()
    val streak = data.activeStreak(today)
    val habitPoints = (streak.coerceAtMost(7) / 7.0 * 20).roundToInt()
    return FinancialHealth(
        score = savingPoints + budgetPoints + cushionPoints + habitPoints,
        factors = listOf(
            HealthFactor("Lo que te queda del mes", if (month.income > 0) "${(rate * 100).roundToInt()}% de tus ingresos (meta: 20%)" else "Registra tus ingresos", savingPoints, 30),
            HealthFactor("Límite diario", if (month.expenseItems.isEmpty()) "Sin gastos este mes" else "Te pasaste ${month.overLimitDays} de ${month.daysSoFar} días", budgetPoints, 30),
            HealthFactor("Colchón de ahorro", "${formatAmount(cushionMonths, 1)} meses de gastos (meta: 3)", cushionPoints, 20),
            HealthFactor("Racha de ahorro", "$streak ${if (streak == 1) "día" else "días"} seguidos (meta: 7)", habitPoints, 20),
        ),
    )
}

/** Tips written from the person's own numbers, most urgent first. */
internal fun personalTips(data: FinancialData, today: Int = currentDayIndex()): List<FinancialTip> {
    val month = data.monthNumbers()
    val tips = mutableListOf<FinancialTip>()

    if (month.income <= 0) {
        tips += FinancialTip("no-income", TipTopic.Habits, 2, "Registra lo que entra este mes",
            "Sin ingresos registrados Dinex no puede calcular cuánto te queda ni cuánto ahorrar. Anota tu sueldo, propinas o ventas.",
            TipAction.AddMovement)
    } else if (month.expenses > month.income) {
        tips += FinancialTip("overspent", TipTopic.Spending, 3, "Este mes gastas más de lo que ganas",
            "Llevas ${money(month.expenses)} en gastos y ${money(month.income)} en ingresos: ${money(month.expenses - month.income)} de diferencia. " +
                "Revisa qué gasto puedes pausar esta semana.", TipAction.Analysis)
    } else {
        val left = month.income - month.expenses
        val rate = left / month.income
        if (rate < .10) {
            tips += FinancialTip("low-margin", TipTopic.Savings, 2, "Apunta a guardar el 10%",
                "Te queda ${money(left)} (${(rate * 100).roundToInt()}% de tus ingresos). Guardar el 10% serían ${money(month.income * .10)} al mes.",
                TipAction.Save)
        } else {
            tips += FinancialTip("good-margin", TipTopic.Savings, 0, "Te queda ${(rate * 100).roundToInt()}% de tus ingresos",
                "Tienes ${money(left)} libres este mes. Pásalos a una meta antes de que se vayan en gastos pequeños.", TipAction.Save)
        }
    }

    if (month.expenses > 0) {
        val byCategory = month.expenseItems.groupBy { it.category }.mapValues { (_, items) -> items.sumOf { it.amount } }
        val (topName, topAmount) = byCategory.maxBy { it.value }
        val share = topAmount / month.expenses
        if (share >= .4 && byCategory.size > 1 || share >= .6) {
            tips += FinancialTip("top-category", TipTopic.Spending, 2, "$topName se lleva ${(share * 100).roundToInt()}% de tus gastos",
                "Llevas ${money(topAmount)} en $topName. Si lo recortas un 10% ahorras ${money(topAmount * .10)} al mes.", TipAction.Analysis)
        }
        val budgets = suggestedCategoryBudgets(data.movements.filter { it.isInCurrentMonth() }, data.movements) + data.categoryBudgets
        byCategory.forEach { (name, spent) ->
            val limit = budgets[name] ?: return@forEach
            if (limit > 0 && spent > limit) {
                tips += FinancialTip("over-$name", TipTopic.Spending, 3, "Te pasaste en $name",
                    "Gastaste ${money(spent)} y tu presupuesto era ${money(limit)}: ${money(spent - limit)} de más.", TipAction.Plan)
            }
        }
        val small = month.expenseItems.filter { it.amount < 10 }
        if (small.size >= 5) {
            tips += FinancialTip("small-expenses", TipTopic.Spending, 1, "Gastos hormiga: ${small.size} compras pequeñas",
                "Compras de menos de S/ 10 ya suman ${money(small.sumOf { it.amount })} este mes. Juntas pesan más de lo que parece.",
                TipAction.Analysis)
        }
    }

    if (month.overLimitDays >= 3) {
        tips += FinancialTip("daily-limit", TipTopic.Habits, 2, "Superaste tu límite ${month.overLimitDays} días",
            "Tu límite es ${money(data.dailyLimit.toDouble())} al día. Si casi siempre te pasas, súbelo un poco para que sea realista, o planifica tus compras.",
            TipAction.Plan)
    }

    val goals = data.savingGoals()
    if (goals.isEmpty()) {
        tips += FinancialTip("no-goal", TipTopic.Goals, 2, "Ponle nombre a tu ahorro",
            "Ahorrar para algo concreto (un celular, un viaje, tu fondo de emergencia) hace más fácil no gastarlo.", TipAction.Save)
    } else {
        goals.filter { it.target > it.saved }.minByOrNull { it.target - it.saved }?.let { goal ->
            val remaining = goal.target - goal.saved
            val weekly = (month.income * .10 / 4.3).takeIf { it >= 1 } ?: 10.0
            val weeks = ceil(remaining / weekly).toInt()
            tips += FinancialTip("goal-${goal.id}", TipTopic.Goals, 1, "Te faltan ${money(remaining)} para «${goal.name}»",
                "Guardando ${money(weekly)} por semana llegas en unas $weeks ${if (weeks == 1) "semana" else "semanas"}.", TipAction.Save)
        }
    }

    val cushionTarget = month.expenses * 3
    if (month.expenses > 0 && data.savings < cushionTarget) {
        tips += FinancialTip("emergency", TipTopic.Savings, 1, "Arma tu fondo de emergencia",
            "Lo ideal es tener 3 meses de gastos guardados: unos ${money(cushionTarget)}. Te faltan ${money(cushionTarget - data.savings)}.",
            TipAction.Save)
    }

    val streak = data.activeStreak(today)
    val savedToday = data.streakDay == today
    when {
        streak > 0 && !savedToday -> tips += FinancialTip("streak-risk", TipTopic.Habits, 3, "No pierdas tu racha de $streak ${if (streak == 1) "día" else "días"}",
            "Ahorra hoy aunque sea S/ 1 para mantenerla viva.", TipAction.Save)
        streak == 0 -> tips += FinancialTip("streak-start", TipTopic.Habits, 1, "Empieza una racha hoy",
            "Ahorrar un poco cada día crea el hábito. Con 7 días seguidos subes de nivel.", TipAction.Save)
    }

    val pending = data.reminders.filter { it.amount.isFinite() && it.amount > 0 }
    if (pending.isNotEmpty()) {
        tips += FinancialTip("reminders", TipTopic.Spending, 2, "Tienes ${pending.size} ${if (pending.size == 1) "pago pendiente" else "pagos pendientes"}",
            "Suman ${money(pending.sumOf { it.amount })}. Sepáralos antes de gastar en otras cosas.", TipAction.Plan)
    }

    return tips.sortedByDescending { it.priority }
}

/** General tips; one is picked per day so the screen changes every time the person comes back. */
internal val dailyTips = listOf(
    "Págate primero: apenas recibas dinero, separa tu ahorro y vive con el resto.",
    "Antes de una compra que no planeaste, espera 24 horas. Si mañana aún la quieres, decide con calma.",
    "Paga el total de tu tarjeta de crédito, no el mínimo: así evitas pagar intereses altos.",
    "Antes de un préstamo o compra en cuotas, mira la TCEA: muestra el costo real del crédito.",
    "Separa la gratificación de julio y diciembre: una parte para tu fondo de emergencia te da tranquilidad.",
    "Revisa tus suscripciones cada mes y cancela las que no usas.",
    "Lleva tu almuerzo dos veces por semana y mira cuánto ahorras al mes.",
    "Compara precios en al menos dos tiendas antes de comprar algo caro.",
    "Un fondo de emergencia de 3 meses de gastos evita que te endeudes por un imprevisto.",
    "Registra tus gastos el mismo día: lo que no se anota, se olvida.",
    "La regla 50/30/20: 50% necesidades, 30% gustos y 20% ahorro. Ajústala a tu realidad.",
    "Pequeñas cantidades suman: S/ 5 al día son más de S/ 1,800 en un año.",
    "Evita comprar con hambre o con prisa: son los momentos de más gastos impulsivos.",
    "Ponle nombre y fecha a cada meta de ahorro; así es más fácil no tocarla.",
    "Si te llega un ingreso extra, ahorra al menos la mitad antes de celebrarlo.",
    "Desconfía de quien promete ganancias altas y seguras: suele ser una estafa.",
    "Nunca compartas tus claves ni códigos de Yape o del banco, aunque te digan que es tu banco.",
    "Haz una lista antes de ir al mercado y cómprala tal cual.",
    "Revisa tus gastos del mes cada domingo: corregir a tiempo es más fácil.",
    "Antes de pedir prestado, pregúntate si la compra puede esperar un mes.",
)

internal fun tipOfTheDay(dayIndex: Int = currentDayIndex(), offset: Int = 0): String =
    dailyTips[((dayIndex + offset) % dailyTips.size + dailyTips.size) % dailyTips.size]

/** Saving `monthly` every month for `years` at a yearly `rate` (0.08 = 8%), compounded monthly. */
internal fun futureValue(monthly: Double, years: Int, rate: Double): Double {
    val months = years * 12
    if (rate <= 0) return monthly * months
    val r = rate / 12
    return monthly * ((1 + r).pow(months) - 1) / r
}
