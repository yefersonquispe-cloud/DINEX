package com.example.dinex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

@Composable
internal fun PlanPage(
    movements: List<Movement>,
    dailyLimit: Float,
    reminders: List<PaymentReminder>,
    onDailyLimitChange: (Float) -> Unit,
    onAddReminder: () -> Unit,
    onDeleteReminder: (PaymentReminder) -> Unit,
    categoryBudgets: Map<String, Double> = emptyMap(),
    onCategoryBudgetChange: (String, Double) -> Unit = { _, _ -> },
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 760.dp
        LazyColumn(
            Modifier.fillMaxSize().widthIn(max = 960.dp),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DinexPageHeading("Construye tu futuro", "Tu plan", "Organiza hoy. Disfruta de un mañana con menos sorpresas.", accent = Lime)
            }
            if (wide) item {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        DailyBudget(movements.filter { !it.income && it.isToday() }.sumOf { it.amount }, dailyLimit, onDailyLimitChange)
                        PaymentPlanner(reminders, onAddReminder, onDeleteReminder)
                    }
                    Box(Modifier.weight(1f)) { CategoryPlans(movements, categoryBudgets, onCategoryBudgetChange) }
                }
            } else {
              item {
                DailyBudget(
                    movements.filter { !it.income && it.isToday() }.sumOf { it.amount },
                    dailyLimit,
                    onDailyLimitChange,
                )
            }
            item { CategoryPlans(movements, categoryBudgets, onCategoryBudgetChange) }
              item { PaymentPlanner(reminders, onAddReminder, onDeleteReminder) }
            }
        }
    }
}

@Composable
internal fun CategoryPlans(movements: List<Movement>, budgets: Map<String, Double> = emptyMap(), onChange: (String, Double) -> Unit = { _, _ -> }) {
    var editing by remember { mutableStateOf<CategoryInfo?>(null) }
    val income = movements.filter { it.income }.sumOf { it.amount }
    DinexCard(accent = Lime) {
        Text("Presupuestos por categoría", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Night)
        Text("Toca una categoría para editar su límite mensual", color = Gray, fontSize = 12.sp)
        Text("Ingresos del mes: ${money(income)}", color = Gray, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        categories.forEach { category ->
            val spent = movements.filter { !it.income && it.category == category.name }.sumOf { it.amount }
            val limit = budgets[category.name] ?: 0.0
            val ratio = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
            val warning = ratio >= .8f
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = category }.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryMark(category.code, category.color)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(category.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Night, modifier = Modifier.weight(1f))
                            Text(if (limit > 0) "${money(spent)} / ${money(limit)}" else "${money(spent)} · Configurar", color = if (warning) Orange else Gray, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        DinexProgress(ratio, if (warning) Orange else category.color)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${(ratio * 100).toInt()}%", color = if (warning) Orange else Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                if (warning) {
                    Spacer(Modifier.height(4.dp))
                    Text("Cerca del límite en ${category.name.lowercase()}", color = Orange, fontSize = 10.sp)
                }
            }
        }
    }
    editing?.let { category ->
        var amount by remember(category.name) { mutableStateOf(budgets[category.name]?.let(::format2).orEmpty()) }
        val parsed = amount.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        val total = budgets.filterKeys { it != category.name }.values.sum() + (parsed ?: 0.0)
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Presupuesto: ${category.name}") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ingresos registrados este mes: ${money(income)}. Elige cuánto deseas destinar; no se mueve dinero.")
                OutlinedTextField(amount, { amount = it }, label = { Text("Límite mensual en soles") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text("Total destinado a categorías: ${money(total)}", color = Gray)
                if (total > income) Text("Tus presupuestos superan los ingresos del mes. Puedes ajustarlos o continuar si prevés nuevos ingresos.", color = Orange)
                Text("0 desactiva el límite de esta categoría.", color = Gray)
            }
        }, confirmButton = { Button(enabled = parsed != null, onClick = { parsed?.let { onChange(category.name, it) }; editing = null }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } })
    }
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
