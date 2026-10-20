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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.round

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

@Composable
internal fun CategoryPlans(movements: List<Movement>, budgets: Map<String, Double> = emptyMap(), suggestions: Map<String, Double> = suggestedCategoryBudgets(movements)) {
    val income = movements.filter { it.income }.sumOf { it.amount }
    DinexCard(accent = Lime) {
        Text("Presupuestos por categoría", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Night)
        Text("Sugerencia basada en tus ingresos e historial", color = Gray, fontSize = 12.sp)
        Text("Ingresos del mes: ${money(income)}", color = Gray, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        categories.forEach { category ->
            val spent = movements.filter { !it.income && it.category == category.name }.sumOf { it.amount }
            val custom = budgets.containsKey(category.name)
            val limit = budgets[category.name] ?: suggestions[category.name] ?: 0.0
            val ratio = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
            val warning = ratio >= .8f
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryMark(category.code, category.color)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(category.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Night, modifier = Modifier.weight(1f))
                            Text("${money(spent)} / ${money(limit)}", color = if (warning) Orange else Gray, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        DinexProgress(ratio, if (warning) Orange else category.color)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${(ratio * 100).toInt()}%", color = if (warning) Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Text(if (custom) "Personalizado" else "Sugerido automáticamente", color = Gray, fontSize = 10.sp, modifier = Modifier.padding(start = 44.dp, top = 3.dp))
                if (warning) {
                    Spacer(Modifier.height(4.dp))
                    Text("Cerca del límite en ${category.name.lowercase()}", color = Orange, fontSize = 10.sp)
                }
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
    val parsed = categories.associate { it.name to amounts[it.name].orEmpty().replace(',', '.').toDoubleOrNull() }
    val valid = parsed.values.all { it != null && it.isFinite() && it >= 0 }
    val total = parsed.values.sumOf { it ?: 0.0 }
    val income = suggestions.values.sum()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar presupuestos", color = Night) },
        text = {
            Column(Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estos importes se sugieren a partir de tus ingresos del mes. Cámbialos solo si lo deseas; no se mueve dinero.", color = Gray, fontSize = 12.sp)
                categories.forEach { category ->
                    OutlinedTextField(
                        value = amounts[category.name].orEmpty(),
                        onValueChange = { amounts[category.name] = it },
                        label = { Text(category.name) },
                        prefix = { Text("S/ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Total destinado: ${money(total)} · Ingresos: ${money(income)}", color = if (total > income) Orange else Gray, fontSize = 11.sp)
                if (total > income) Text("El total supera tus ingresos registrados.", color = Orange, fontSize = 11.sp)
                TextButton(onClick = onReset) { Text("Volver a sugerencias automáticas") }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                val updated = overrides.toMutableMap()
                parsed.forEach { (name, amount) ->
                    if (amount != null && amount != initial[name]) updated[name] = amount
                }
                onSave(updated)
            }) { Text("Guardar cambios") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun PaymentPlanner(
    reminders: List<PaymentReminder>,
    onAdd: () -> Unit,
    onDelete: (PaymentReminder) -> Unit,
) {
    DinexCard(elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Próximos pagos", color = Night, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Guardados en tu dispositivo", color = Gray, fontSize = 11.sp)
            }
            Text("${reminders.size}", color = Lime, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (reminders.isEmpty()) {
            Text("Aún no tienes pagos pendientes.", color = Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 10.dp))
        } else {
            reminders.forEach { reminder -> PayLine(reminder, onDelete = { onDelete(reminder) }) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RadiusMd),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenDark),
            border = BorderStroke(1.dp, Green.copy(alpha = .35f)),
        ) { Text("+ Agregar recordatorio") }
    }
}

@Composable
internal fun PayLine(reminder: PaymentReminder, onDelete: () -> Unit) {
    val code = reminder.title.take(3).uppercase()
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryMark(code, Lime, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(reminder.title, color = Night, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(reminder.date, color = Gray, fontSize = 10.sp)
        }
        Text(money(reminder.amount), color = Night, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text("×", modifier = Modifier.clip(CircleShape).clickable(onClick = onDelete).padding(6.dp), color = Gray, fontSize = 16.sp)
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
                Text("S/", color = Orange, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        },
        title = { Text("¿Este pago ya se realizó?", fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = Night) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${reminder.title} · ${money(reminder.amount)}", color = Night, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Si ya pagaste, Dinex puede quitar el recordatorio y registrarlo como egreso.", color = Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            Button(onClick = onRegisterExpense, colors = ButtonDefaults.buttonColors(containerColor = Indigo)) {
                Text("Sí, agregar egreso", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onRemoveOnly) { Text("No, solo eliminar", color = Red) }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray) }
            }
        },
    )
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
