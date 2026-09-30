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
// INSIGHTS
// Spending chart, daily bars and category filtering.
// ═══════════════════════════════════════════════════════════════════════════
@Composable
internal fun InsightsPage(movements: List<Movement>) {
    val income = movements.filter { it.income }.sumOf { it.amount }; val expense = movements.filterNot { it.income }.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize().widthIn(max = 820.dp), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { Text("Análisis", fontWeight = FontWeight.Black, fontSize = 25.sp); Text("Decisiones basadas en tus hábitos", color = Gray, fontSize = 11.sp) }
        item { ForecastCard(income, expense) }
        item { DailyBars(movements) }
        item { InsightBox("AH", "Gastos hormiga", "Llevas S/ 53.90 en compras menores a S/ 15. Si reduces la mitad, tu racha crecerá más rápido.", Orange) }
        item { InsightBox("TOP", "Tu mayor categoría", "Entretenimiento concentra gran parte del gasto. Prueba un tope semanal de S/ 40.", Red) }
    }
}

@Composable
internal fun ForecastCard(income: Double, expense: Double) {
    val forecast = max(income - expense - expense / 12 * 18, 0.0)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF4A62CC), Indigo))).padding(19.dp)) {
        Column { Text("PROYECCIÓN DE FIN DE MES", color = Color.White.copy(alpha = .65f), fontSize = 9.sp, letterSpacing = 1.sp); Text(money(forecast), color = Color.White, fontWeight = FontWeight.Black, fontSize = 29.sp); Text("Si mantienes tu ritmo actual", color = Color.White.copy(alpha = .7f), fontSize = 10.sp); Spacer(Modifier.height(13.dp)); LinearProgressIndicator(progress = { .72f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Lime, trackColor = Color.White.copy(alpha = .18f)) }
    }
}

@Composable
internal fun DailyBars(movements: List<Movement>) {
    val currentDay = currentDayOfMonth()
    val maxMovementDay = movements.filterNot { it.income }.maxOfOrNull { it.day } ?: currentDay
    val days = (1..maxOf(currentDay, maxMovementDay, 12)).toList()
    val values = days.map { day -> movements.filter { !it.income && it.day == day }.sumOf { it.amount } }
    val peak = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val initialDay = if (currentDay in days) currentDay else days.getOrElse(values.indices.maxByOrNull { values[it] } ?: days.lastIndex) { currentDay }
    var selectedDay by remember(movements.toList(), currentDay) { mutableIntStateOf(initialDay) }
    val selectedMovements = movements.filter { !it.income && it.day == selectedDay }
    val selectedTotal = selectedMovements.sumOf { it.amount }
    Surface(shape = RoundedCornerShape(22.dp), color = Card, border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(17.dp)) {
            Text("Gasto diario", fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text("Toca una barra para ver en qué gastaste", color = Gray, fontSize = 10.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().height(190.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                values.forEachIndexed { i, value ->
                    val day = days[i]
                    val selected = day == selectedDay
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(7.dp)).clickable { selectedDay = day }.padding(horizontal = 1.dp),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (selected && value > 0) Text(money(value).removePrefix("S/ "), color = Lime, fontSize = 8.sp, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight((value / peak * .68).coerceIn(0.04, .68).toFloat())
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (selected) Brush.verticalGradient(listOf(Lime, Indigo)) else Brush.verticalGradient(listOf(NightSoft, Mint))),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(day.toString(), color = if (selected) Lime else Gray, fontWeight = if (selected) FontWeight.Black else FontWeight.Normal, fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = CardRaised, border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedMovements.isEmpty()) Border else Indigo.copy(alpha = .35f))) {
                Column(Modifier.fillMaxWidth().padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DÍA $selectedDay", color = GreenDark, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = .8.sp, modifier = Modifier.weight(1f))
                        Text(money(selectedTotal), color = if (selectedTotal > 0) Red else Gray, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                    if (selectedMovements.isEmpty()) Text("No registraste egresos este día.", color = Gray, fontSize = 9.sp)
                    else selectedMovements.forEach { movement ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(categories.firstOrNull { it.name == movement.category }?.color ?: Gray))
                            Spacer(Modifier.width(7.dp))
                            Text(movement.title, color = Night, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(money(movement.amount), color = Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InsightBox(code: String, title: String, body: String, tint: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = tint.copy(alpha = .09f), border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .2f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint), contentAlignment = Alignment.Center) { Text(code, color = Color.White, fontWeight = FontWeight.Black, fontSize = 9.sp) }
            Spacer(Modifier.width(11.dp)); Column { Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp); Spacer(Modifier.height(3.dp)); Text(body, color = Gray, fontSize = 10.sp) }
        }
    }
}
