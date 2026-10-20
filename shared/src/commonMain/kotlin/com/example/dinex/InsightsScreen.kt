package com.example.dinex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
internal fun InsightsPage(history: List<Movement>, onExport: () -> Unit = {}) {
    val months = history.map { it.dateIso().take(7) }.distinct().sortedDescending()
    val current = civilDateFromEpochDays()
    val currentKey = "${current.year}-${current.month.toString().padStart(2, '0')}"
    var selectedMonth by remember { mutableStateOf(currentKey) }
    var weekly by remember { mutableStateOf(false) }
    val movements = if (weekly) reportMovements(history, ReportPeriod.WEEK) else history.filter { it.dateIso().startsWith(selectedMonth) }
    val income = movements.filter { it.income }.sumOf { it.amount }
    val expense = movements.filterNot { it.income }.sumOf { it.amount }
    val previousPeriod = if (weekly) {
        val previousDates = (7..13).map { dateIso(civilDateFromEpochDays(currentDayIndex() - it)) }.toSet()
        history.filter { it.dateIso() in previousDates }
    } else {
        val year = selectedMonth.take(4).toIntOrNull() ?: current.year
        val month = selectedMonth.takeLast(2).toIntOrNull() ?: current.month
        val previousKey = if (month == 1) "${year - 1}-12" else "$year-${(month - 1).toString().padStart(2, '0')}"
        history.filter { it.dateIso().startsWith(previousKey) }
    }
    val previousExpense = previousPeriod.filterNot { it.income }.sumOf { it.amount }
    val smallSpends = movements.filter { !it.income && it.amount in 0.01..14.99 }
    val antTotal = smallSpends.sumOf { it.amount }
    val categoryTotals = movementChartCategories(movements).map { category ->
        category to movements.filter { !it.income && it.category == category.name }.sumOf { it.amount }
    }
    val topCategory = categoryTotals.maxByOrNull { it.second }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 760.dp
        LazyColumn(
            Modifier.fillMaxSize().widthIn(max = 960.dp),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = if (wide) 18.dp else 132.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!weekly, { weekly = false }, label = { Text("Mensual") })
                    FilterChip(weekly, { weekly = true }, label = { Text("Últimos 7 días") })
                    TextButton(onClick = onExport) { Text("Exportar") }
                }
            }
            if (!weekly) item {
                DinexPageHeading("Una mirada más clara", "Análisis", "Descubre tus patrones. Decide tu siguiente paso.", accent = Indigo)
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("Mes: $selectedMonth") }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        (listOf(currentKey) + months).distinct().forEach { month ->
                            DropdownMenuItem(text = { Text(month) }, onClick = { selectedMonth = month; expanded = false })
                        }
                    }
                }
            }
            item { MonthPass(income, expense, movements.size) }
            item { SpendingRhythm(expense, previousExpense, income, if (weekly) "la semana anterior" else "el mes anterior") }
            if (!weekly && selectedMonth == currentKey) item { ForecastCard(income, expense) }
            item { PeriodCashFlow(movements, if (weekly) (0..6).map { dateIso(civilDateFromEpochDays(currentDayIndex() - 6 + it)) } else
                (1..31).filter { validDate(selectedMonth.take(4).toInt(), selectedMonth.takeLast(2).toInt(), it) }.map { "$selectedMonth-${it.toString().padStart(2, '0')}" }) }
            item {
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                    if (!weekly) Box(Modifier.weight(1f)) { DailyBars(movements, selectedMonth.take(4).toInt(), selectedMonth.takeLast(2).toInt()) }
                    Box(Modifier.weight(1f)) { SpendingOverview(movements) }
                } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!weekly) DailyBars(movements, selectedMonth.take(4).toInt(), selectedMonth.takeLast(2).toInt())
                    SpendingOverview(movements)
                }
            }
            if (antTotal > 0) {
                item {
                    InsightBox(
                        "AH",
                        "Gastos hormiga",
                        "Llevas ${money(antTotal)} en ${smallSpends.size} compras menores a S/ 15.",
                        Orange,
                    )
                }
            }
            if (topCategory != null && topCategory.second > 0) {
                item {
                    InsightBox(
                        "TOP",
                        "Tu mayor categoría",
                        "${topCategory.first.name} concentra ${money(topCategory.second)} de tu gasto.",
                        Red,
                    )
                }
            }
            if (expense <= 0) {
                item {
                    DinexCard {
                        DinexEmptyState("Sin datos para analizar", "Cuando registres gastos, aquí verás insights reales.")
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingRhythm(expense: Double, previousExpense: Double, income: Double, comparisonLabel: String) {
    var expanded by remember { mutableStateOf(false) }
    val change = if (previousExpense > 0) ((expense - previousExpense) / previousExpense * 100).toInt() else null
    val rate = if (income > 0) (expense / income * 100).toInt() else 0
    DinexCard(accent = if (change != null && change > 0) Orange else GreenDark, vibrant = true) {
        Text("TU RITMO", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(when {
            change == null -> "Comienza a descubrir tus patrones"
            change > 0 -> "Gastaste $change% más que $comparisonLabel"
            change < 0 -> "Gastaste ${-change}% menos que $comparisonLabel"
            else -> "Tu gasto se mantuvo estable"
        }, color = Night, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(7.dp))
        Text("${money(expense)} en gastos · ${money(income)} en ingresos", color = Gray, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        DinexProgress((rate / 100f).coerceIn(0f, 1f), if (rate > 100) Red else GreenDark)
        Spacer(Modifier.height(7.dp))
        Text("Usaste $rate% de tus ingresos registrados", color = if (rate > 100) Red else Gray, fontSize = 12.sp)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar detalle" else "Ver detalle") }
        if (expanded) {
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(10.dp))
            Text("Periodo anterior: ${money(previousExpense)} en gastos", color = Night, fontSize = 13.sp)
            Text("Disponible después de gastos: ${money(income - expense)}", color = Night, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun ForecastCard(income: Double, expense: Double) {
    val day = currentDayOfMonth()
    val date = civilDateFromEpochDays()
    val daysInMonth = (28..31).last { validDate(date.year, date.month, it) }
    val forecast = max(income - expense / day.coerceAtLeast(1) * daysInMonth, 0.0)
    val monthProgress = (day / daysInMonth.toFloat()).coerceIn(0.05f, 1f)
    DinexCard(elevated = true, accent = Indigo, vibrant = true) {
        Text("Proyección de fin de mes", color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(money(forecast), color = Night, fontWeight = FontWeight.Black, fontSize = 30.sp)
        Text("Estimación según los ingresos registrados y tu gasto diario promedio", color = Gray, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        DinexProgress(monthProgress, Indigo)
        Spacer(Modifier.height(6.dp))
        Text("Día $day del mes", color = Gray, fontSize = 10.sp)
    }
}

@Composable
internal fun DailyBars(movements: List<Movement>, year: Int = civilDateFromEpochDays().year, month: Int = civilDateFromEpochDays().month) {
    val currentDay = currentDayOfMonth()
    val maxMovementDay = movements.filterNot { it.income }.maxOfOrNull { it.day } ?: currentDay
    val days = (1..31).filter { validDate(year, month, it) }
    val values = days.map { day -> movements.filter { !it.income && it.day == day }.sumOf { it.amount } }
    val peak = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val initialDay = if (currentDay in days) currentDay else days.getOrElse(values.indices.maxByOrNull { values[it] } ?: days.lastIndex) { currentDay }
    var selectedDay by remember(movements.toList(), currentDay) { mutableIntStateOf(initialDay) }
    val selectedMovements = movements.filter { !it.income && it.day == selectedDay }
    val selectedTotal = selectedMovements.sumOf { it.amount }
    DinexCard(accent = ElectricBlue) {
        Text("Gasto diario", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Night)
        Text("Toca un día para ver el detalle", color = Gray, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(168.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEachIndexed { i, value ->
                val day = days[i]
                val selected = day == selectedDay
                val height by animateFloatAsState((value / peak * .68).coerceIn(0.04, .68).toFloat(), tween(if (LocalDinexReducedMotion.current) 0 else 450))
                Column(
                    Modifier.width(32.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).clickable { selectedDay = day }.padding(horizontal = 1.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (selected && value > 0) Text(money(value).removePrefix("S/ "), color = Lime, fontSize = 8.sp, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(height)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(
                                if (selected) Brush.verticalGradient(listOf(Lime, Indigo))
                                else Brush.verticalGradient(listOf(Indigo.copy(alpha = .75f), ElectricBlue.copy(alpha = .25f))),
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(day.toString(), color = if (selected) Lime else Gray, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 9.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(RadiusMd), color = CardRaised, border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedMovements.isEmpty()) Border else Indigo.copy(alpha = .3f))) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Día $selectedDay", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(money(selectedTotal), color = if (selectedTotal > 0) Red else Gray, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                if (selectedMovements.isEmpty()) Text("No registraste egresos este día.", color = Gray, fontSize = 11.sp)
                else selectedMovements.forEach { movement ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(categories.firstOrNull { it.name == movement.category }?.color ?: Gray))
                        Spacer(Modifier.width(7.dp))
                        Text(movement.title, color = Night, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(money(movement.amount), color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun InsightBox(code: String, title: String, body: String, tint: Color) {
    DinexCard(accent = tint) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(RadiusSm)).background(tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(if (code == "AH") DinexNavigationIcons.Wallet else DinexNavigationIcons.Analysis, null, tint = tint, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Night)
                Spacer(Modifier.height(3.dp))
                Text(body, color = Gray, fontSize = 12.sp)
            }
        }
    }
}
