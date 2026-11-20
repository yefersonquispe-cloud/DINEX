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
internal fun InsightsPage(
    history: List<Movement>,
    onExport: () -> Unit = {},
    locationAvailable: Boolean = false,
    onRequestLocation: () -> Unit = {},
    dailyLimit: Double = 0.0,
) {
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
                    FilterChip(!weekly, { weekly = false }, label = { Text(t("Mensual")) })
                    FilterChip(weekly, { weekly = true }, label = { Text(t("Últimos 7 días")) })
                    TextButton(onClick = onExport) { Text(t("Exportar")) }
                }
            }
            if (!weekly) item {
                DinexPageHeading("Una mirada más clara", "Análisis", "Descubre tus patrones. Decide tu siguiente paso.", accent = Indigo)
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(t("Mes: {0}", selectedMonth)) }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        (listOf(currentKey) + months).distinct().forEach { month ->
                            DropdownMenuItem(text = { Text(month) }, onClick = { selectedMonth = month; expanded = false })
                        }
                    }
                }
            }
            item { MonthPass(income, expense, movements.size) }
            item { SpendingRhythm(expense, previousExpense, income, if (weekly) "la semana anterior" else "el mes anterior") }
            item { SpendingPlacesSection(movements, locationAvailable, onRequestLocation) }
            if (!weekly && selectedMonth == currentKey) item { ForecastCard(income, expense) }
            item { PeriodCashFlow(movements, if (weekly) (0..6).map { dateIso(civilDateFromEpochDays(currentDayIndex() - 6 + it)) } else
                (1..31).filter { validDate(selectedMonth.take(4).toInt(), selectedMonth.takeLast(2).toInt(), it) }.map { "$selectedMonth-${it.toString().padStart(2, '0')}" }) }
            item {
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                    if (!weekly) Box(Modifier.weight(1f)) { DailyBars(movements, selectedMonth.take(4).toInt(), selectedMonth.takeLast(2).toInt(), dailyLimit) }
                    Box(Modifier.weight(1f)) { SpendingOverview(movements) }
                } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!weekly) DailyBars(movements, selectedMonth.take(4).toInt(), selectedMonth.takeLast(2).toInt(), dailyLimit)
                    SpendingOverview(movements)
                }
            }
            if (antTotal > 0) {
                item {
                    InsightBox(
                        "AH",
                        "Gastos hormiga",
                        t("Llevas {0} en {1} a S/ 15.", money(antTotal), countLabel(smallSpends.size, "compra menor", "compras menores")),
                        Orange,
                    )
                }
            }
            if (topCategory != null && topCategory.second > 0) {
                item {
                    InsightBox(
                        "TOP",
                        "Tu mayor categoría",
                        t("{0} concentra {1} de tu gasto.", topCategory.first.name, money(topCategory.second)),
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
        Text(t("TU RITMO"), color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(when {
            change == null -> t("Comienza a descubrir tus patrones")
            change > 0 -> t("Gastaste {0}% más que {1}", change, t(comparisonLabel))
            change < 0 -> t("Gastaste {0}% menos que {1}", -change, t(comparisonLabel))
            else -> t("Tu gasto se mantuvo estable")
        }, color = Night, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(7.dp))
        Text(t("{0} en gastos · {1} en ingresos", money(expense), money(income)), color = Gray, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        DinexProgress((rate / 100f).coerceIn(0f, 1f), if (rate > 100) Red else GreenDark)
        Spacer(Modifier.height(7.dp))
        Text(t("Usaste {0}% de tus ingresos registrados", rate), color = if (rate > 100) Red else Gray, fontSize = 12.sp)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) t("Ocultar detalle") else t("Ver detalle")) }
        if (expanded) {
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(10.dp))
            Text(t("Periodo anterior: {0} en gastos", money(previousExpense)), color = Night, fontSize = 13.sp)
            Text(t("Disponible después de gastos: {0}", money(income - expense)), color = Night, fontSize = 13.sp)
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
        Text(t("Proyección de fin de mes"), color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(money(forecast), color = Night, fontWeight = FontWeight.Black, fontSize = 30.sp)
        Text(t("Estimación según los ingresos registrados y tu gasto diario promedio"), color = Gray, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        DinexProgress(monthProgress, Indigo)
        Spacer(Modifier.height(6.dp))
        Text(t("Día {0} del mes", day), color = Gray, fontSize = 10.sp)
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
                Text(t(title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Night)
                Spacer(Modifier.height(3.dp))
                Text(t(body), color = Gray, fontSize = 12.sp)
            }
        }
    }
}
