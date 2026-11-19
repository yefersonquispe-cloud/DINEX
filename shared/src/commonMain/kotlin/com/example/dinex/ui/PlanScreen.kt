package com.example.dinex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PlanPage(
    movements: List<Movement>,
    dailyLimit: Float,
    reminders: List<PaymentReminder>,
    onDailyLimitChange: (Float) -> Unit,
    onAddReminder: () -> Unit,
    onDeleteReminder: (PaymentReminder) -> Unit,
    categoryBudgets: Map<String, Double> = emptyMap(),
    historyMovements: List<Movement> = movements,
    onCategoryBudgetsSave: (Map<String, Double>) -> Unit = {},
    onCategoryBudgetsReset: () -> Unit = {},
) {
    var showBudgetEditor by remember { mutableStateOf(false) }
    val suggestions = suggestedCategoryBudgets(movements, historyMovements)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 760.dp
        LazyColumn(
            Modifier.fillMaxSize().widthIn(max = 960.dp),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = if (wide) 18.dp else 132.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DinexPageHeading("Construye tu futuro", "Tu plan", "Organiza hoy. Disfruta de un mañana con menos sorpresas.", accent = Lime)
            }
            if (wide) item {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        DailyBudget(movements.filter { !it.income && it.isToday() }.sumOf { it.amount }, dailyLimit, onDailyLimitChange,
                            onAdjustCategories = { showBudgetEditor = true })
                        PaymentPlanner(reminders, onAddReminder, onDeleteReminder)
                    }
                    Box(Modifier.weight(1f)) { CategoryPlans(movements, categoryBudgets, suggestions) }
                }
            } else {
              item {
                DailyBudget(
                    movements.filter { !it.income && it.isToday() }.sumOf { it.amount },
                    dailyLimit,
                    onDailyLimitChange,
                    onAdjustCategories = { showBudgetEditor = true },
                )
            }
            item { CategoryPlans(movements, categoryBudgets, suggestions) }
              item { PaymentPlanner(reminders, onAddReminder, onDeleteReminder) }
            }
        }
    }
    if (showBudgetEditor) CategoryBudgetEditorDialog(
        suggestions = suggestions,
        overrides = categoryBudgets,
        onDismiss = { showBudgetEditor = false },
        onSave = { onCategoryBudgetsSave(it); showBudgetEditor = false },
        onReset = { onCategoryBudgetsReset(); showBudgetEditor = false },
    )
}

/**
 * Presupuestos por categoría: una barra de reparto muestra cómo se divide el mes y cada
 * categoría tiene su "batería" de 10 celdas, con un estado claro (va bien, cerca, te pasaste).
 */
@Composable
internal fun CategoryPlans(movements: List<Movement>, budgets: Map<String, Double> = emptyMap(), suggestions: Map<String, Double> = suggestedCategoryBudgets(movements)) {
    val income = movements.filter { it.income }.sumOf { it.amount }
    val rows = categories.map { category ->
        val spent = movements.filter { !it.income && it.budgetCategory() == category.name }.sumOf { it.amount }
        val limit = budgets[category.name] ?: suggestions[category.name] ?: 0.0
        Triple(category, spent, limit)
    }
    val totalLimit = rows.sumOf { it.third }
    val totalSpent = rows.sumOf { it.second }
    val customCount = categories.count { budgets.containsKey(it.name) }
    DinexCard(accent = Lime) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(t("PRESUPUESTO DEL MES"), color = GreenDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(t("Presupuestos por categoría"), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Night)
            }
            Text(
                if (customCount > 0) t("{0} personalizados", customCount) else t("Sugeridos"),
                color = NightSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).background(Border.copy(alpha = .45f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(money(totalSpent), color = Night, fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text(t("  de {0}", money(totalLimit)), color = Gray, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(
            if (income > 0) t("Repartido según tus ingresos del mes ({0})", money(income)) else t("Registra tus ingresos para afinar estas sugerencias"),
            color = Gray, fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        // Barra de reparto: cada color es una categoría; lo gastado se ve sólido sobre su tramo.
        if (totalLimit > 0) {
            Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.filter { it.third > 0 }.forEach { (category, spent, limit) ->
                    Box(Modifier.weight(limit.toFloat()).fillMaxHeight().background(category.color.copy(alpha = .22f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((spent / limit).toFloat().coerceIn(0f, 1f)).background(category.color))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        rows.forEachIndexed { index, (category, spent, limit) ->
            CategoryBudgetRow(category, spent, limit)
            if (index < rows.lastIndex) HorizontalDivider(color = Border.copy(alpha = .6f), modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun CategoryBudgetRow(category: CategoryInfo, spent: Double, limit: Double) {
    val ratio = if (limit > 0) spent / limit else 0.0
    val (label, tone) = when {
        limit <= 0 -> t("Sin límite") to Gray
        ratio > 1.0 -> t("Te pasaste") to Red
        ratio >= .8 -> t("Cerca del límite") to Orange
        spent <= 0 -> t("Sin gastos") to GreenDark
        else -> t("Va bien") to GreenDark
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryMark(category.code, category.color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t(category.name), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Night)
                Text(
                    when {
                        limit <= 0 -> t("{0} gastados", money(spent))
                        spent > limit -> t("{0} de {1} · {2} de más", money(spent), money(limit), money(spent - limit))
                        else -> t("{0} de {1} · quedan {2}", money(spent), money(limit), money(limit - spent))
                    },
                    color = Gray, fontSize = 12.sp, maxLines = 1,
                )
            }
            Text(
                label, color = tone, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(tone.copy(alpha = .10f)).padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        BudgetCells(ratio, category.color)
    }
}

/** Batería de 10 celdas: se llenan con el color de la categoría; al pasarse, las últimas se vuelven rosa. */
@Composable
private fun BudgetCells(ratio: Double, color: androidx.compose.ui.graphics.Color) {
    val filled = (ratio.coerceIn(0.0, 1.0) * 10).let { if (ratio > 0 && it < 1) 1.0 else it }
    val still = LocalDinexReducedMotion.current
    val animated by androidx.compose.animation.core.animateFloatAsState(filled.toFloat(), androidx.compose.animation.core.tween(if (still) 0 else 700), label = "celdas")
    Row(Modifier.fillMaxWidth().height(9.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(10) { cell ->
            val amount = (animated - cell).coerceIn(0f, 1f)
            val cellColor = when {
                ratio > 1.0 -> Red
                ratio >= .8 && cell >= 7 -> Orange
                else -> color
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Border.copy(alpha = .55f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(amount).background(cellColor))
            }
        }
    }
}

@Composable
internal fun CategoryBudgetEditorDialog(
    suggestions: Map<String, Double>,
    overrides: Map<String, Double>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Double>) -> Unit,
    onReset: () -> Unit,
) {
    val initial = remember { categories.associate { it.name to (overrides[it.name] ?: suggestions[it.name] ?: 0.0) } }
    val amounts = remember { mutableStateMapOf<String, String>().apply { initial.forEach { (name, value) -> put(name, format2(value)) } } }
    val parsed = categories.associate { it.name to parseMoneyInput(amounts[it.name].orEmpty()) }
    val valid = parsed.values.all { it != null && it.isFinite() && it >= 0 }
    val total = parsed.values.sumOf { it ?: 0.0 }
    val income = suggestions.values.sum()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Ajustar presupuestos"), color = Night) },
        text = {
            Column(Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Estos importes se sugieren a partir de tus ingresos del mes. Cámbialos solo si lo deseas; no se mueve dinero."), color = Gray, fontSize = 12.sp)
                categories.forEach { category ->
                    OutlinedTextField(
                        value = amounts[category.name].orEmpty(),
                        onValueChange = { amounts[category.name] = it },
                        label = { Text(category.name) },
                        prefix = { Text(t("S/ ")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Total destinado: ${money(total)} · Ingresos: ${money(income)}", color = if (total > income) Orange else Gray, fontSize = 11.sp)
                if (total > income) Text(t("El total supera tus ingresos registrados."), color = Orange, fontSize = 11.sp)
                TextButton(onClick = onReset) { Text(t("Volver a sugerencias automáticas")) }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                val updated = overrides.toMutableMap()
                parsed.forEach { (name, amount) ->
                    if (amount != null && amount != initial[name]) updated[name] = amount
                }
                onSave(updated)
            }) { Text(t("Guardar cambios")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancelar")) } },
    )
}

/**
 * Próximos pagos: cada recordatorio con su "hoja de calendario", urgencia por color
 * (hoy, mañana, próximo) y un resumen amable de lo que viene.
 */
@Composable
internal fun PaymentPlanner(
    reminders: List<PaymentReminder>,
    onAdd: () -> Unit,
    onDelete: (PaymentReminder) -> Unit,
) {
    val ordered = reminders.sortedBy { reminderUrgency(it.date) }
    val total = reminders.sumOf { it.amount }
    val next = ordered.firstOrNull()
    DinexCard(elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("RECORDATORIOS"), color = GreenDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(t("Próximos pagos"), color = Night, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            if (reminders.isNotEmpty()) Column(horizontalAlignment = Alignment.End) {
                Text(t("Por pagar"), color = Gray, fontSize = 11.sp)
                Text(money(total), color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (next == null) {
            Surface(shape = RoundedCornerShape(18.dp), color = Green.copy(alpha = .08f), border = BorderStroke(1.dp, Green.copy(alpha = .2f))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Green.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Icon(DinexNavigationIcons.Check, null, tint = GreenDark, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t("Estás al día"), color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(t("Anota la luz, el internet o tu pensión y te avisamos a tiempo."), color = Gray, fontSize = 12.sp)
                    }
                }
            }
        } else {
            // Frase personal sobre el pago más cercano.
            Text(
                reminderHeadline(next), color = NightSoft, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            ordered.forEach { reminder -> PayLine(reminder, onDelete = { onDelete(reminder) }) }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Green.copy(alpha = .12f), contentColor = GreenDark),
            elevation = null,
        ) {
            Icon(DinexNavigationIcons.Bell, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(t("Agregar recordatorio"), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 0 = hoy, 1 = mañana, 2 = con fecha, 3 = sin fecha. Sirve para ordenar y para el color. */
private fun reminderUrgency(date: String): Int = when (date.trim().uppercase()) {
    "HOY" -> 0
    "MAÑANA", "MANANA" -> 1
    "PRÓXIMO", "PROXIMO", "" -> 3
    else -> 2
}

private fun reminderHeadline(reminder: PaymentReminder): String {
    val amount = if (reminder.amount > 0) " de ${money(reminder.amount)}" else ""
    return when (reminderUrgency(reminder.date)) {
        0 -> "Hoy toca pagar ${reminder.title}$amount. Tenlo en cuenta antes de gastar."
        1 -> "Mañana pagas ${reminder.title}$amount. Sepáralo desde hoy y duerme tranquilo."
        2 -> "Tu próximo pago es ${reminder.title}$amount, el ${reminder.date.lowercase()}."
        else -> "Tienes pendiente ${reminder.title}$amount. Ponle fecha para que te avisemos."
    }
}

@Composable
internal fun PayLine(reminder: PaymentReminder, onDelete: () -> Unit) {
    val urgency = reminderUrgency(reminder.date)
    val tone = when (urgency) { 0 -> Red; 1 -> Orange; 2 -> GreenDark; else -> Gray }
    val raw = reminder.date.trim()
    // Hoja de calendario: día y mes si hay fecha; si no, HOY / MAÑ / —.
    val (big, small) = when {
        urgency == 0 -> "HOY" to ""
        urgency == 1 -> "MAÑ" to ""
        raw.contains('/') || raw.contains('-') -> raw.split('/', '-').let { parts ->
            parts.first() to (parts.getOrNull(1)?.toIntOrNull()?.let { currentMonthShort(it) } ?: "")
        }
        raw.toIntOrNull() != null -> raw to currentMonthShort()
        else -> "—" to ""
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(CardRaised.copy(alpha = .7f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.size(width = 46.dp, height = 50.dp).clip(RoundedCornerShape(12.dp)).background(Card),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(10.dp).background(tone))
            Spacer(Modifier.weight(1f))
            Text(big, color = Night, fontWeight = FontWeight.Black, fontSize = if (big.length > 2) 13.sp else 17.sp)
            if (small.isNotBlank()) Text(small, color = Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(reminder.title, color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text(
                when (urgency) { 0 -> "Vence hoy"; 1 -> "Vence mañana"; 2 -> "Vence el ${raw.lowercase()}"; else -> "Sin fecha fija" },
                color = tone, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            )
        }
        if (reminder.amount > 0) Text(money(reminder.amount), color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(DinexNavigationIcons.Close, "Quitar recordatorio ${reminder.title}", tint = Gray, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
internal fun ReminderPaymentDialog(
    reminder: PaymentReminder,
    onDismiss: () -> Unit,
    onRemoveOnly: () -> Unit,
    onRegisterExpense: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Card,
        icon = {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Orange.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Text(t("S/"), color = Orange, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        },
        title = { Text(t("¿Este pago ya se realizó?"), fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = Night) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${reminder.title} · ${money(reminder.amount)}", color = Night, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(t("Si ya pagaste, Dinex puede quitar el recordatorio y registrarlo como egreso."), color = Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            Button(onClick = onRegisterExpense, colors = ButtonDefaults.buttonColors(containerColor = Indigo)) {
                Text(t("Sí, agregar egreso"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onRemoveOnly) { Text(t("No, solo eliminar"), color = Red) }
                TextButton(onClick = onDismiss) { Text(t("Cancelar"), color = Gray) }
            }
        },
    )
}
