package com.example.dinex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_card_balance_bg
import dinex.shared.generated.resources.dinex_card_savings_bg
import dinex.shared.generated.resources.dinex_app_icon
import dinex.shared.generated.resources.dinex_mascot_ai
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max

// ═══════════════════════════════════════════════════════════════════════════
// PLANS & REMINDERS
// Budgets, recurring payments and reminder-to-expense confirmation.
// ═══════════════════════════════════════════════════════════════════════════
@Composable
internal fun PlanPage(
    movements: List<Movement>,
    dailyLimit: Float,
    reminders: List<PaymentReminder>,
    onDailyLimitChange: (Float) -> Unit,
    onAddReminder: () -> Unit,
    onDeleteReminder: (PaymentReminder) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().widthIn(max = 820.dp), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { Text("Tu plan", fontWeight = FontWeight.Black, fontSize = 25.sp); Text("Límites claros, menos sorpresas", color = Gray, fontSize = 11.sp) }
        item { DailyBudget(movements.filter { !it.income && it.day == currentDayOfMonth() }.sumOf { it.amount }, dailyLimit, onDailyLimitChange) }
        item { CategoryPlans(movements) }
        item { PaymentPlanner(reminders, onAddReminder, onDeleteReminder) }
    }
}

@Composable
internal fun CategoryPlans(movements: List<Movement>) {
    Surface(shape = RoundedCornerShape(22.dp), color = Card, border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(17.dp)) {
            Text("Presupuestos por categoría", fontWeight = FontWeight.Black, fontSize = 16.sp); Text("Controla antes de gastar", color = Gray, fontSize = 10.sp); Spacer(Modifier.height(13.dp))
            categories.forEach { category ->
                val spent = movements.filter { !it.income && it.category == category.name }.sumOf { it.amount }
                val ratio = (spent / category.limit).toFloat().coerceIn(0f, 1f)
                Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(35.dp).clip(RoundedCornerShape(11.dp)).background(category.color.copy(alpha = .11f)), contentAlignment = Alignment.Center) { Text(category.code, color = category.color, fontWeight = FontWeight.Black, fontSize = 9.sp) }
                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
                        Row { Text(category.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text("${money(spent)} / ${money(category.limit)}", color = Gray, fontSize = 9.sp) }
                        Spacer(Modifier.height(5.dp)); LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = category.color, trackColor = category.color.copy(alpha = .1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PaymentPlanner(
    reminders: List<PaymentReminder>,
    onAdd: () -> Unit,
    onDelete: (PaymentReminder) -> Unit,
) {
    Surface(shape = RoundedCornerShape(22.dp), color = CardRaised, border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(17.dp)) {
            Row { Column(Modifier.weight(1f)) { Text("Próximos pagos", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp); Text("Guardados en tu dispositivo", color = Color.White.copy(alpha = .55f), fontSize = 10.sp) }; Text("${reminders.size}", color = Lime, fontWeight = FontWeight.Black, fontSize = 22.sp) }
            Spacer(Modifier.height(12.dp))
            if (reminders.isEmpty()) {
                Text("Aún no tienes pagos pendientes.", color = Color.White.copy(alpha = .65f), fontSize = 10.sp, modifier = Modifier.padding(vertical = 14.dp))
            } else {
                reminders.forEach { reminder -> PayLine(reminder, onDelete = { onDelete(reminder) }) }
            }
            Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Lime), border = androidx.compose.foundation.BorderStroke(1.dp, Lime.copy(alpha = .45f))) { Text("+ Agregar recordatorio") }
        }
    }
}

@Composable
internal fun PayLine(reminder: PaymentReminder, onDelete: () -> Unit) {
    val code = reminder.title.take(3).uppercase()
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .09f)), contentAlignment = Alignment.Center) { Text(code, color = Lime, fontWeight = FontWeight.Black, fontSize = 8.sp) }
        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(reminder.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp); Text(reminder.date, color = Color.White.copy(alpha = .5f), fontSize = 8.sp) }; Text(money(reminder.amount), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.width(8.dp))
        Text("×", modifier = Modifier.clip(CircleShape).clickable(onClick = onDelete).padding(6.dp), color = Color.White.copy(alpha = .55f), fontSize = 15.sp)
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
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Orange.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Text("S/", color = Orange, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
        },
        title = { Text("¿Este pago ya se realizó?", fontWeight = FontWeight.Black, textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${reminder.title} · ${money(reminder.amount)}", color = Night, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Si ya pagaste, Dinex puede quitar el recordatorio y registrarlo automáticamente como egreso.", color = Gray, fontSize = 10.sp, textAlign = TextAlign.Center)
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
