package com.example.dinex

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════════════════
// RESUMEN: saldo con su evolución, salud financiera, indicadores del mes,
// flujo acumulado, categorías, calendario de gastos, metas y pagos.
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun SummaryPage(data: FinancialData, health: FinancialHealth, actions: WebActions, onNavigate: (WebPage) -> Unit) {
    val movements = data.movements
    val today = civilDateFromEpochDays()
    val todayKey = monthKeyOf(today)
    val month = remember(movements) { movements.filter { it.webMonthKey() == todayKey } }
    val history = remember(movements) { monthlyTotals(movements, todayKey, 6) }
    val current = history.last()
    val previous = history[history.size - 2]
    val available = data.availableBalance()
    PageFrame {
        Columns(ratio = 1.6f, left = { stretch ->
            BalanceHero(available, current, data.savings, month, today, actions, stretch)
        }, right = { stretch ->
            HealthCard(health, stretch) { onNavigate(WebPage.TIPS) }
        })
        KpiRow(history, today)
        Columns(ratio = 1.6f, left = { stretch ->
            CashFlowCard(movements, today, previous, stretch)
        }, right = { stretch ->
            CategoryDonutCard(month.filterNot { it.income }, stretch) { onNavigate(WebPage.ANALYSIS) }
        })
        Columns(ratio = 1.6f, left = { stretch ->
            CalendarCard(movements, stretch)
        }, right = { stretch ->
            TodayCard(month.filter { !it.income && it.day == today.day }.sumOf { it.amount }, data.dailyLimit.toDouble(), data.activeStreak(),
                data.streakDay == currentDayIndex(), actions, stretch) { onNavigate(WebPage.BUDGET) }
        })
        Columns(ratio = 1.6f, left = { stretch ->
            RecentMovementsCard(movements.sortedWith(compareByDescending<Movement> { it.webEpochDay() }.thenByDescending { it.id }).take(6), stretch) { onNavigate(WebPage.MOVEMENTS) }
        }, right = { stretch ->
            GoalsCompactCard(data.savingGoals(), actions) { onNavigate(WebPage.GOALS) }
            RemindersCard(data.reminders, actions, stretch)
        })
    }
}

// ---------------------------------------------------------------- Saldo principal

@Composable
private fun BalanceHero(available: Double, current: MonthTotals, savings: Double, month: List<Movement>, today: DinexDate, actions: WebActions, modifier: Modifier) {
    // Saldo al final de cada día del mes: se parte del saldo de hoy y se descuenta lo que pasó después.
    val points = remember(month, available) {
        val net = month.sumOf { if (it.income) it.amount else -it.amount }
        val start = available + savings - net
        (1..today.day).map { day -> start + month.filter { it.day <= day }.sumOf { if (it.income) it.amount else -it.amount } - savings }
    }
    Column(
        modifier.fillMaxWidth().dinexEntrance().clip(WebCardShape)
            .background(Brush.linearGradient(W.hero))
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(W.lime.copy(alpha = .20f), Color.Transparent), Offset(size.width * .95f, 0f), size.height * .9f), size.height * .9f, Offset(size.width * .95f, 0f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF2E8B57).copy(alpha = .25f), Color.Transparent), Offset(0f, size.height), size.height * .8f), size.height * .8f, Offset(0f, size.height))
                // Líneas finas en diagonal.
                val step = 22.dp.toPx()
                var x = -size.height
                while (x < size.width) { drawLine(Color.White.copy(alpha = .025f), Offset(x, size.height), Offset(x + size.height, 0f), 1f); x += step }
            }
            .padding(26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(CircleShape).background(W.lime.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(W.lime))
                Spacer(Modifier.width(6.dp))
                Text("SALDO DISPONIBLE", color = W.lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("${currentMonthName(today.month)} ${today.year}", color = W.sidebarMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        // Frente al saldo con el que empezó el mes.
        val monthStart = available - month.sumOf { if (it.income) it.amount else -it.amount }
        val change = percentChange(available, monthStart)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(moneyGrouped(rememberCountUp(available)), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.4).sp)
            Spacer(Modifier.width(12.dp))
            if (change != null && kotlin.math.abs(change) >= .1) Box(Modifier.padding(bottom = 10.dp)) { DeltaChip(change, onDark = true) }
        }
        Text("Lo que puedes usar sin tocar tus ahorros · ${if (monthStart >= 0) "empezaste el mes con ${moneyGrouped(monthStart)}" else "este mes"}",
            color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        LineAreaChart(
            listOf(ChartSeries("Saldo", points, W.lime)), labels = points.indices.map { "${it + 1}" },
            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 110.dp), onDark = true, showAxis = false,
            tooltipTitle = { "${it + 1} de ${currentMonthName(today.month).lowercase()}" },
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            HeroStat("Ingresos del mes", "+ ${moneyGrouped(current.income)}", W.mint, DinexNavigationIcons.ArrowUp, Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .1f)))
            HeroStat("Gastos del mes", "- ${moneyGrouped(current.expense)}", W.coral, DinexNavigationIcons.ArrowDown, Modifier.weight(1f).padding(start = 18.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .1f)))
            HeroStat("Ahorrado", moneyGrouped(savings), W.lime, DinexNavigationIcons.Target, Modifier.weight(1f).padding(start = 18.dp))
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroButton("Registrar gasto", DinexNavigationIcons.ArrowDown, filled = true, onClick = actions.onExpense)
            HeroButton("Registrar ingreso", DinexNavigationIcons.ArrowUp, filled = false, onClick = actions.onIncome)
            HeroButton("Ahorrar", DinexNavigationIcons.Target, filled = false) { actions.onSave(null) }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, tint: Color, icon: ImageVector, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = W.sidebarMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeroButton(text: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.dinexPress(interaction).height(42.dp).clip(shape)
            .background(if (filled) W.lime else Color.White.copy(alpha = if (hovered) .12f else .06f))
            .then(if (filled) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = .16f), shape))
            .hoverable(interaction).clickable(interaction, null, onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (filled) W.forest else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (filled) W.forest else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- Salud financiera

@Composable
private fun HealthCard(health: FinancialHealth, modifier: Modifier, onTips: () -> Unit) {
    WCard(modifier.fillMaxWidth()) {
        CardTitle("Salud financiera", "Puntaje de 0 a 100 con tus hábitos del mes", DinexNavigationIcons.Shield)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.BottomCenter) {
            HealthGauge(health.score, Modifier.fillMaxWidth().fillMaxHeight())
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 28.dp)) {
                Text("${rememberCountUp(health.score.toDouble(), 1200).toInt()}", color = W.ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            }
        }
        Text(health.label, color = healthColor(health.score), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))
        health.factors.forEach { factor ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(factor.label, color = W.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${factor.points}/${factor.max}", color = W.ink3, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(5.dp))
                ThinBar(factor.points.toFloat() / factor.max, healthColor(factor.points * 100 / factor.max), 5.dp)
            }
        }
        Spacer(Modifier.weight(1f).heightIn(min = 10.dp))
        LinkButton("Ver consejos para subir tu puntaje", onClick = onTips)
    }
}

// ---------------------------------------------------------------- Indicadores del mes

@Composable
private fun KpiRow(history: List<MonthTotals>, today: DinexDate) {
    val current = history.last()
    val previous = history[history.size - 2]
    val projected = current.expense / today.day.coerceAtLeast(1) * daysInMonthOf(today.year, today.month)
    TileRow(tiles = listOf(
        { KpiTile("Ingresos del mes", moneyGrouped(rememberCountUp(current.income)), DinexNavigationIcons.ArrowUp, W.positive,
            history.map { it.income }, percentChange(current.income, previous.income), false, it) },
        { KpiTile("Gastos del mes", moneyGrouped(rememberCountUp(current.expense)), DinexNavigationIcons.ArrowDown, W.negative,
            history.map { it.expense }, percentChange(current.expense, previous.expense), true, it) },
        { KpiTile("Tasa de ahorro", "${(current.savingRate * 100).toInt()}%", DinexNavigationIcons.Target, W.brand,
            history.map { it.savingRate * 100 }, previous.income.takeIf { it > 0 }?.let { (current.savingRate - previous.savingRate) * 100 }, false, it, deltaIsPoints = true) },
        { KpiTile("Proyección de gasto", moneyGrouped(rememberCountUp(projected)), DinexNavigationIcons.Analysis, W.warning,
            history.dropLast(1).map { it.expense } + projected, percentChange(projected, previous.expense), true, it) },
    ))
}

@Composable
private fun KpiTile(
    label: String, value: String, icon: ImageVector, tint: Color, spark: List<Double>, change: Double?, goodWhenDown: Boolean,
    modifier: Modifier, deltaIsPoints: Boolean = false,
) {
    WCard(modifier, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.weight(1f))
            if (deltaIsPoints) {
                if (change == null) DeltaChip(null) else {
                    val good = change >= 0
                    val color = if (good) W.positive else W.negative
                    Text("${if (change >= 0) "+" else "−"}${fixed(abs(change), 0)} pts", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            } else DeltaChip(change, goodWhenDown)
        }
        Spacer(Modifier.height(12.dp))
        Text(label, color = W.ink3, fontSize = 12.sp)
        Text(value, color = W.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        MiniSpark(spark, tint, Modifier.fillMaxWidth().height(36.dp))
        Text("Últimos 6 meses", color = W.ink3, fontSize = 10.sp)
    }
}

// ---------------------------------------------------------------- Flujo del mes

@Composable
private fun CashFlowCard(movements: List<Movement>, today: DinexDate, previous: MonthTotals, modifier: Modifier) {
    val flow = remember(movements) { cumulativeFlow(movements, today.year, today.month, today.day) }
    val prevYear = monthKeyYear(previous.key); val prevMonth = monthKeyMonth(previous.key)
    val lastMonth = remember(movements) { cumulativeFlow(movements, prevYear, prevMonth, minOf(today.day, daysInMonthOf(prevYear, prevMonth))) }
    val last = flow.last()
    WCard(modifier.fillMaxWidth()) {
        CardTitle("Flujo del mes", "Ingresos y gastos acumulados día a día", DinexNavigationIcons.Analysis) {
            Column(horizontalAlignment = Alignment.End) {
                Text("Te queda", color = W.ink3, fontSize = 11.sp)
                Text(moneyGrouped(last.income - last.expense), color = if (last.income >= last.expense) W.positive else W.negative, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        LineAreaChart(
            listOf(
                ChartSeries("Ingresos", flow.map { it.income }, W.brand),
                ChartSeries("Gastos", flow.map { it.expense }, W.negative),
                ChartSeries("Gastos del mes pasado", lastMonth.map { it.expense }, W.ink3, fill = false, dashed = true),
            ),
            labels = flow.map { "${it.day}" },
            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 230.dp),
            tooltipTitle = { "Hasta el ${it + 1} de ${currentMonthName(today.month).lowercase()}" },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(W.brand, "Ingresos"); Legend(W.negative, "Gastos"); Legend(W.ink3, "Gastos del mes pasado", dashed = true)
            Spacer(Modifier.weight(1f))
            val pace = lastMonth.lastOrNull()?.expense ?: 0.0
            if (pace > 0) Text(
                if (last.expense <= pace) "Vas ${moneyGrouped(pace - last.expense)} por debajo del mes pasado" else "Vas ${moneyGrouped(last.expense - pace)} por encima del mes pasado",
                color = if (last.expense <= pace) W.positive else W.negative, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------------------------------------------------------------- Categorías

@Composable
private fun CategoryDonutCard(expenses: List<Movement>, modifier: Modifier, onAnalysis: () -> Unit) {
    val total = expenses.sumOf { it.amount }
    val groups = remember(expenses) { expenses.groupBy { it.budgetCategory() }.map { (name, list) -> name to list.sumOf { it.amount } }.sortedByDescending { it.second } }
    var hover by remember { mutableStateOf<Int?>(null) }
    WCard(modifier.fillMaxWidth()) {
        CardTitle("En qué se va tu dinero", "Gastos de este mes por categoría") { LinkButton("Análisis", onClick = onAnalysis) }
        Spacer(Modifier.height(14.dp))
        if (groups.isEmpty()) { EmptyNote("Sin gastos este mes", "Cuando registres un gasto verás aquí en qué se va tu dinero."); return@WCard }
        val colors = groups.mapIndexed { i, g -> categoryColor(g.first, i) }
        InteractiveDonut(groups, colors, Modifier.fillMaxWidth().height(200.dp), selected = hover) { hover = it }
        Spacer(Modifier.height(14.dp))
        groups.take(5).forEachIndexed { i, (name, value) ->
            val active = hover == i
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (active) W.hover else Color.Transparent)
                    .hoverable(remember { MutableInteractionSource() }).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colors[i]))
                Spacer(Modifier.width(10.dp))
                Text(name, color = W.ink, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(percentLabel(value, total), color = W.ink3, fontSize = 12.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                Text(moneyGrouped(value), color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
            }
        }
    }
}

// ---------------------------------------------------------------- Calendario

@Composable
private fun CalendarCard(movements: List<Movement>, modifier: Modifier) {
    val today = currentDayIndex()
    val weeks = remember(movements) { spendingCalendar(movements, today, 20) }
    val days = weeks.flatten().filterNot { it.future }
    val active = days.count { it.amount > 0 }
    val calm = days.takeLast(30).count { it.amount <= 0 }
    WCard(modifier.fillMaxWidth()) {
        CardTitle("Calendario de gastos", "Últimas 20 semanas · pasa el mouse por un día", DinexNavigationIcons.Plan) { CalendarLegend() }
        Spacer(Modifier.height(14.dp))
        CalendarHeatmap(weeks, Modifier.fillMaxWidth().weight(1f).heightIn(min = 150.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniFact("$active días con gastos", W.brand)
            MiniFact("$calm días sin gastar en el último mes", W.positive)
            days.maxByOrNull { it.amount }?.takeIf { it.amount > 0 }?.let {
                val date = civilDateFromEpochDays(it.epochDay)
                MiniFact("Día más alto: ${date.day} ${reportMonthShort(date.month)} · ${shortMoney(it.amount)}", W.negative)
            }
        }
    }
}

@Composable
private fun MiniFact(text: String, tint: Color) {
    Row(Modifier.clip(CircleShape).background(tint.copy(alpha = .1f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(6.dp))
        Text(text, color = W.ink2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Hoy

@Composable
private fun TodayCard(spent: Double, limit: Double, streak: Int, savedToday: Boolean, actions: WebActions, modifier: Modifier, onBudget: () -> Unit) {
    WCard(modifier.fillMaxWidth()) {
        CardTitle("Gasto de hoy", "Límite diario ${moneyGrouped(limit)}") { LinkButton("Ajustar", onClick = onBudget) }
        Spacer(Modifier.height(14.dp))
        val progress = if (limit > 0) (spent / limit).toFloat() else 0f
        val over = spent > limit
        val ring by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (LocalDinexReducedMotion.current) 0 else 900), label = "anillo")
        val track = W.line; val color = if (over) W.negative else W.brand; val lime = W.lime
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 13.dp.toPx(); val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 135f, 270f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (ring > 0f) drawArc(Brush.linearGradient(listOf(lime, color)), 135f, 270f * ring, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(progress * 100).toInt()}%", color = W.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("del límite", color = W.ink3, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Gastaste", color = W.ink3, fontSize = 12.sp)
                Text(moneyGrouped(spent), color = W.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(if (over) "Te pasaste por" else "Te quedan", color = W.ink3, fontSize = 12.sp)
                Text(moneyGrouped(abs(limit - spent)), color = if (over) W.negative else W.positive, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.weight(1f).heightIn(min = 14.dp))
        HorizontalDivider(color = W.line)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakBadge(streakLevel(streak), 38.dp, locked = streak == 0)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Racha de ahorro", color = W.ink3, fontSize = 12.sp)
                Text("$streak ${if (streak == 1) "día" else "días"} · ${streakLevel(streak).name}", color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                nextStreakLevel(streak)?.let { next ->
                    Text("Faltan ${next.startsAt - streak} días para ${next.name}", color = W.ink3, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (savedToday) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(W.brandSoft).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(DinexNavigationIcons.Check, null, tint = W.brand, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Hoy ya ahorraste. ¡Sigue así!", color = W.brand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else WButton("Ahorrar hoy y sumar a la racha", DinexNavigationIcons.Add, primary = true) { actions.onSave(null) }
    }
}

// ---------------------------------------------------------------- Movimientos recientes, metas y pagos

@Composable
private fun RecentMovementsCard(items: List<Movement>, modifier: Modifier, onAll: () -> Unit) {
    WCard(modifier.fillMaxWidth(), padding = 0.dp) {
        Box(Modifier.padding(start = 22.dp, end = 14.dp, top = 20.dp, bottom = 8.dp)) {
            CardTitle("Últimos movimientos", "Lo más reciente primero", DinexNavigationIcons.Movements) { LinkButton("Ver todos", onClick = onAll) }
        }
        if (items.isEmpty()) Box(Modifier.padding(horizontal = 22.dp)) { EmptyNote("Todavía no hay movimientos", "Registra tu primer gasto o ingreso.") }
        items.forEach { item -> MovementLine(item) }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
internal fun MovementLine(item: Movement, trailing: (@Composable () -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp)).background(if (hovered) W.hover else Color.Transparent)
            .hoverable(interaction).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(item.category, item.income)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.category} · ${item.method}${if (item.location.isNotBlank()) " · ${item.location}" else ""}", color = W.ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${item.day} ${reportMonthShort(item.month)}", color = W.ink3, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        Text((if (item.income) "+ " else "- ") + moneyGrouped(item.amount), color = if (item.income) W.positive else W.ink,
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.widthIn(min = 100.dp), textAlign = TextAlign.End)
        trailing?.invoke()
    }
}

@Composable
private fun GoalsCompactCard(goals: List<SavingsGoal>, actions: WebActions, onAll: () -> Unit) {
    WCard(Modifier.fillMaxWidth()) {
        CardTitle("Metas de ahorro", "${moneyGrouped(goals.sumOf { it.saved })} ahorrados") { LinkButton("Ver metas", onClick = onAll) }
        Spacer(Modifier.height(12.dp))
        if (goals.isEmpty()) { EmptyNote("Aún no tienes metas", "Dale un propósito a tu ahorro.", "Crear meta") { actions.onSave(null) }; return@WCard }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            goals.sortedByDescending { if (it.target > 0) it.saved / it.target else 0.0 }.take(3).forEach { goal ->
                val progress = if (goal.target > 0) (goal.saved / goal.target).toFloat() else 0f
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { actions.onOpenGoal(goal.id) }.pointerHoverIcon(PointerIcon.Hand).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RingProgress(progress, if (progress >= 1f) W.positive else W.brand, 76.dp, 7.dp) {
                        Image(painterResource(goalTheme(goal.name).main), null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(displayGoalName(goal.name), color = W.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(percentLabel(goal.saved, goal.target), color = W.brand, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
internal fun RemindersCard(reminders: List<PaymentReminder>, actions: WebActions, modifier: Modifier = Modifier) {
    WCard(modifier.fillMaxWidth()) {
        CardTitle("Próximos pagos", if (reminders.isEmpty()) "Sin pagos pendientes" else countLabel(reminders.size, "pendiente", "pendientes")) {
            IconSquare(DinexNavigationIcons.Add, "Agregar recordatorio", actions.onAddReminder)
        }
        if (reminders.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).heightIn(min = 96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(DinexNavigationIcons.Bell, null, tint = W.ink3, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(8.dp))
                Text("Agrega tus pagos fijos y DINEX te los recordará.", color = W.ink3, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            return@WCard
        }
        Spacer(Modifier.height(8.dp))
        reminders.sortedBy { it.date }.take(4).forEachIndexed { i, reminder ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                // Línea de tiempo.
                Column(Modifier.width(18.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(2.dp).weight(1f).background(if (i == 0) Color.Transparent else W.line))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(W.warning).border(2.dp, W.surface, CircleShape))
                    Box(Modifier.width(2.dp).weight(1f).background(if (i == minOf(3, reminders.lastIndex)) Color.Transparent else W.line))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(reminder.title, color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(reminder.date, color = W.ink3, fontSize = 12.sp)
                }
                Text(moneyGrouped(reminder.amount), color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                LinkButton("Pagar") { actions.onResolveReminder(reminder) }
            }
        }
    }
}
