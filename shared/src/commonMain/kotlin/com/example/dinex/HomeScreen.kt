package com.example.dinex

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max

@Composable
internal fun HomePage(
    movements: List<Movement>, dailyLimit: Float, streak: Int, savings: Double, goalName: String, goalTarget: Double,
    onDailyLimitChange: (Float) -> Unit,
    onHistory: () -> Unit,
    onAdd: () -> Unit = {},
    onGoalDetails: () -> Unit = {},
    goals: List<SavingsGoal> = emptyList(),
    onOpenGoal: (Int) -> Unit = {},
    notificationsSupported: Boolean = false,
    notificationsEnabled: Boolean = true,
    onEnableNotifications: () -> Unit = {},
) {
    val income = movements.asSequence().filter { it.income }.sumOf { it.amount }
    val expenses = movements.asSequence().filterNot { it.income }.sumOf { it.amount }
    val balance = income - expenses - savings
    val savingRate = if (income > 0) ((income - expenses) / income * 100).toInt().coerceIn(-999, 999) else 0
    val today = movements.asSequence().filter { !it.income && it.isToday() }.sumOf { it.amount }
    val monthly = movements.filter { it.isInCurrentMonth() }
    val recent = movements.sortedWith(compareByDescending<Movement> { it.dateIso() }.thenByDescending { it.id }).take(6)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val desktop = maxWidth >= 760.dp
        LazyColumn(
            modifier = Modifier.widthIn(max = 1180.dp).fillMaxWidth().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = if (desktop) 28.dp else 18.dp, end = if (desktop) 28.dp else 18.dp,
                top = 24.dp, bottom = if (desktop) 24.dp else 132.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                DinexPageHeading(currentMonthYearLabel(), "Tu dinero, con propósito.", "Cada movimiento cuenta. Dale forma a tu próximo objetivo.")
            }
            if (notificationsSupported && !notificationsEnabled) {
                item { NotificationsNudge(onEnableNotifications) }
            }
            item {
                if (desktop) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) { BalanceStrip(balance, income, expenses, savingRate) }
                    Box(Modifier.weight(1f)) { SavingsHero(savings, streak, goalName, goalTarget, onClick = onGoalDetails) }
                } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BalanceStrip(balance, income, expenses, savingRate)
                    SavingsHero(savings, streak, goalName, goalTarget, onClick = onGoalDetails)
                }
            }
            item {
                if (desktop) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) { SpendingOverview(monthly) }
                    Box(Modifier.weight(1f)) { RecentCard(recent, onHistory, empty = movements.isEmpty(), onAdd = onAdd) }
                } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    SpendingOverview(monthly)
                    RecentCard(recent, onHistory, empty = movements.isEmpty(), onAdd = onAdd)
                }
            }
            item {
                // La racha y el ahorro se abren ahora desde la billetera de la barra superior.
                DailyBudget(today, dailyLimit, onDailyLimitChange)
            }
            goals.forEach { goal -> item(key = "goal-${goal.id}") { SavingsGoalCard(goal.name, goal.saved, goal.target, onClick = { onOpenGoal(goal.id) }) } }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
internal fun NotificationsNudge(onEnable: () -> Unit) {
    DinexCard(accent = Orange) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Activa tus alertas", color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Te avisamos si superas tu presupuesto diario y te recordamos ahorrar antes de que termine el día.",
                    color = Gray, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
            ) { Text("Activar", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
internal fun BalanceStrip(balance: Double, income: Double, expenses: Double, savingRate: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().dinexEntrance(),
        shape = RoundedCornerShape(RadiusLg),
        color = Green,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF0E5431), Green, Color(0xFF328859))))
                .padding(22.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("SALDO DISPONIBLE", color = Color.White.copy(alpha = .82f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                Icon(DinexNavigationIcons.Wallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(money(balance), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-1).sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BalanceStat("Ingresos", "+ ${money(income)}", Modifier.weight(1f), expense = false)
                BalanceStat("Gastos", "− ${money(expenses)}", Modifier.weight(1f), expense = true)
            }
        }
    }
}

@Composable
private fun BalanceStat(label: String, value: String, modifier: Modifier, expense: Boolean) {
    val amountColor = if (expense) Color(0xFFFF7182) else Color(0xFF56FF9C)
    Surface(modifier, shape = RoundedCornerShape(RadiusSm), color = Color(0xFF082B1D).copy(alpha = .96f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .23f))) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(label, color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
            Text(value, color = amountColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
        }
    }
}

@Composable
internal fun MiniStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(RadiusSm), color = Card.copy(alpha = .65f), border = BorderStroke(1.dp, tint.copy(alpha = .24f))) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (label == "Ingresos" || label == "Gastos") Icon(if (label == "Ingresos") DinexNavigationIcons.ArrowUp else DinexNavigationIcons.ArrowDown, null, tint = tint, modifier = Modifier.size(14.dp))
                Text(label, color = Gray, fontSize = 12.sp)
            }
            Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
        }
    }
}

@Composable
internal fun MonthSummary(income: Double, expenses: Double, balance: Double, savingRate: Int) {
    DinexCard {
        SectionHeader("Resumen de este mes", currentMonthYearLabel())
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell("Ingresos", money(income), Green, Modifier.weight(1f))
            SummaryCell("Gastos", money(expenses), Red, Modifier.weight(1f))
            SummaryCell("Balance", money(balance), Night, Modifier.weight(1f))
            SummaryCell("% ahorro", "$savingRate%", Lime, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun SummaryCell(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(label, color = Gray, fontSize = 9.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ProgressSection(
    streak: Int,
    savedToday: Boolean,
    savings: Double,
    goalName: String,
    goalTarget: Double,
    onSaveToday: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tu progreso", color = Night, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        SavingsStreak(streak, savedToday, onSaveToday)
        SavingsHero(savings, streak, goalName, goalTarget)
        if (goalName.isNotBlank() && goalTarget > 0) SavingsGoalCard(goalName, savings, goalTarget)
    }
}

@Composable
internal fun SavingsGoalCard(name: String, saved: Double, target: Double, onClick: (() -> Unit)? = null) {
    val progress = if (target > 0) (saved / target).toFloat().coerceIn(0f, 1f) else 0f
    DinexCard(accent = Orange, onClick = onClick) {
        Text("Meta de ahorro", color = Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(name, color = Night, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        DinexProgress(progress, Orange)
        Spacer(Modifier.height(6.dp))
        Text(if (target > 0) "${money(saved)} de ${money(target)} · ${(progress * 100).toInt()}%" else "${money(saved)} · Sin meta total", color = Gray, fontSize = 11.sp)
    }
}

@Composable
internal fun SavingsHero(savings: Double, streak: Int, goalName: String = "", goalTarget: Double = 0.0, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val goal = if (goalTarget > 0) goalTarget else when { savings < 500 -> 500.0; savings < 1000 -> 1000.0; savings < 2500 -> 2500.0; else -> 5000.0 }
    val goalTitle = if (goalTarget > 0 && goalName.isNotBlank()) "Mi tarjeta de ahorro · $goalName" else "Mi tarjeta de ahorro"
    val progress = (savings / goal).toFloat().coerceIn(0f, 1f)
    DinexCard(modifier = modifier.fillMaxWidth(), elevated = true, accent = Green, vibrant = true, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(goalTitle, color = GreenDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(money(savings), color = Night, fontWeight = FontWeight.Black, fontSize = 34.sp)
            }
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Mint), contentAlignment = Alignment.Center) {
                Icon(DinexNavigationIcons.Sparkle, null, tint = GreenDark, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Text("Meta ${money(goal)}", color = Gray, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${(progress * 100).toInt()}%", color = Lime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        DinexProgress(progress, Green, height = 8.dp)
        if (onClick != null) Text("Ver mi meta y su imagen →", color = Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
internal fun SavingsStreak(streak: Int, savedToday: Boolean, onSaveToday: () -> Unit, elevated: Boolean = false) {
    val level = streakLevel(streak)
    val next = nextStreakLevel(streak)
    DinexCard(accent = level.color, elevated = elevated) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniWalletMedal(level.color, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Racha actual", color = Gray, fontSize = 10.sp)
                Text("$streak días", color = Night, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("Nivel ${level.name.uppercase()}", color = readableAccent(level.color), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (next != null) Text("Progreso hacia ${next.name}", color = Gray, fontSize = 10.sp)
            }
            FilledTonalButton(
                onClick = onSaveToday,
                enabled = !savedToday,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = level.color.copy(alpha = .16f),
                    contentColor = readableAccent(level.color),
                    disabledContainerColor = CardRaised,
                    disabledContentColor = Gray,
                ),
            ) { Text(if (savedToday) "Registrado" else "Ahorré hoy", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        if (!savedToday) {
            Spacer(Modifier.height(8.dp))
            Text("Ahorra hoy y suma +1 a tu racha. Aunque sea S/ 1, cuenta.", color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { index, day ->
                val complete = if (streak <= 0) false else index < (((streak - 1) % 7) + 1).coerceAtMost(7)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (complete) level.color else CardRaised),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (complete) "✓" else day, color = if (complete) Color.White else Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Camino de niveles", color = Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(streakLevels) { item ->
                val unlocked = streak >= item.startsAt
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MiniWalletMedal(if (unlocked) item.color else Border, 34.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(item.name, color = if (unlocked) readableAccent(item.color) else Gray.copy(alpha = .75f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text(if (unlocked) "✓" else "${item.startsAt}d", color = Gray, fontSize = 7.sp)
                }
            }
        }
    }
}

/** Floating bubble opened from the wallet in the top bar. */
@Composable
internal fun SavingsBubble(streak: Int, savedToday: Boolean, onSaveToday: () -> Unit, onClose: () -> Unit) {
    // Aprovecha el ancho disponible dejando solo un margen pequeño a los lados.
    Box(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        SavingsStreak(streak, savedToday, onSaveToday, elevated = true)
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp).size(32.dp)
                .clickable(onClickLabel = "Cerrar mi ahorro", onClick = onClose),
            shape = CircleShape,
            color = Card,
            border = BorderStroke(1.dp, Border),
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("×", color = Gray, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun DailyBudget(today: Double, limit: Float, onChange: (Float) -> Unit, onAdjustCategories: (() -> Unit)? = null) {
    val ratio = (today / limit.coerceAtLeast(1f)).toFloat()
    val warning = ratio >= .8f
    val remaining = max(limit - today, 0.0)
    DinexCard(accent = if (warning) Red else Green) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tu presupuesto", color = Night, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (onAdjustCategories != null) {
                TextButton(onClick = onAdjustCategories) { Text("Ajustar categorías", color = GreenDark, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Diario", money(limit.toDouble()), Green, Modifier.weight(1f))
            MiniStat("Gastado", money(today), if (warning) Red else Night, Modifier.weight(1f))
            MiniStat("Disponible", money(remaining), if (warning) Orange else Lime, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        DinexProgress(ratio, if (warning) Red else Green, height = 8.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            if (warning) "Estás cerca del límite de hoy" else "Aún tienes ${money(remaining)} hoy",
            color = if (warning) Red else Gray,
            fontSize = 11.sp,
        )
        Slider(
            value = limit,
            onValueChange = onChange,
            valueRange = 20f..120f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = if (warning) Red else Green,
                activeTrackColor = if (warning) Red else Green,
                inactiveTrackColor = Border,
            ),
        )
    }
}

@Composable
internal fun SpendingOverview(movements: List<Movement>) {
    val expenses = movementChartCategories(movements).map { category -> category to movements.filter { !it.income && it.category == category.name }.sumOf { it.amount } }
    val realTotal = expenses.sumOf { it.second }
    val safeTotal = realTotal.coerceAtLeast(1.0)
    var selected by remember(expenses.map { it.first.name }) { mutableStateOf<Int?>(null) }
    val visible = selected?.let { listOf(expenses[it]) } ?: expenses.filter { it.second > 0 }
    DinexCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("¿A dónde se fue tu dinero?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Night)
                Text(if (selected == null) "Toca una porción para filtrar" else "Filtro activo", color = Gray, fontSize = 11.sp)
            }
            Text(money(realTotal), fontWeight = FontWeight.Black, color = Night, fontSize = 14.sp)
        }
        Spacer(Modifier.height(14.dp))
        if (realTotal <= 0) {
            Text("Todavía no hay gastos registrados este mes.", color = Gray, fontSize = 11.sp)
        } else {
            val onCategory: (String) -> Unit = { name ->
                val index = expenses.indexOfFirst { it.first.name == name }
                selected = if (selected == index) null else index
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val chart: @Composable () -> Unit = {
                    Donut(expenses.map { it.second }, expenses.map { it.first.color }, safeTotal, selected, expenses.map { it.first.name }) { index ->
                    selected = if (selected == index) null else index
                    }
                }
                if (maxWidth < 340.dp) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    chart()
                    SpendingLegend(visible, safeTotal, selected, onCategory, { selected = null }, Modifier.fillMaxWidth())
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    chart()
                    Spacer(Modifier.width(20.dp))
                    SpendingLegend(visible, safeTotal, selected, onCategory, { selected = null }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SpendingLegend(visible: List<Pair<CategoryInfo, Double>>, total: Double, selected: Int?, onCategory: (String) -> Unit, onClear: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        visible.take(5).forEach { (category, value) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .background(category.color.copy(alpha = .06f))
                .clickable(onClickLabel = "Filtrar ${category.name}") { onCategory(category.name) }.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(category.color))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.name, color = Gray, fontSize = 13.sp)
                    if (selected == null) Text(money(value), color = Gray.copy(alpha = .85f), fontSize = 11.sp)
                }
                Text(if (selected == null) "${percent(value, total)}%" else money(value), color = Night, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        if (selected != null) TextButton(onClick = onClear) { Text("Ver todas", color = GreenDark) }
    }
}

@Composable
internal fun Donut(values: List<Double>, chartColors: List<Color>, total: Double, selected: Int?, labels: List<String>, onSelect: (Int) -> Unit) {
    val reduced = LocalDinexReducedMotion.current
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(values, reduced) {
        if (reduced) reveal.snapTo(1f) else { reveal.snapTo(0f); reveal.animateTo(1f, tween(650)) }
    }
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().pointerInput(values, total) {
            detectTapGestures { tap ->
                val dx = tap.x - size.width / 2f
                val dy = tap.y - size.height / 2f
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                val minDimension = minOf(size.width, size.height).toFloat()
                if (distance < minDimension * .24f || distance > minDimension * .54f) return@detectTapGestures
                val angle = ((atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI + 90.0 + 360.0) % 360.0)
                var cursor = 0.0
                values.forEachIndexed { index, value ->
                    val sweep = value / total * 360.0
                    if (value > 0 && angle >= cursor && angle < cursor + sweep) { onSelect(index); return@detectTapGestures }
                    cursor += sweep
                }
            }
        }) {
            var start = -90f
            values.forEachIndexed { index, value ->
                val sweep = (value / total * 360).toFloat()
                val active = selected == null || selected == index
                drawArc(
                    chartColors[index].copy(alpha = if (active) 1f else .16f),
                    start,
                    sweep * reveal.value,
                    false,
                    style = Stroke((if (selected == index) 16.dp else 12.dp).toPx(), cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val value = selected?.let { values.getOrNull(it) } ?: values.maxOrNull() ?: 0.0
            Text("${percent(value, total)}%", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Night)
            Text(selected?.let { labels.getOrNull(it)?.lowercase() } ?: "principal", color = Gray, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun RecentCard(movements: List<Movement>, onHistory: () -> Unit, empty: Boolean = false, onAdd: () -> Unit = {}) {
    DinexCard {
        SectionHeader("Últimos movimientos", action = "Ver todos", onAction = onHistory)
        Spacer(Modifier.height(4.dp))
        if (empty || movements.isEmpty()) {
            DinexEmptyState("Sin movimientos todavía", "Usa el botón + para registrar tu primer ingreso o gasto.", "Ver movimientos", onHistory)
        } else {
            movements.forEach { MovementRow(it) }
        }
    }
}

@Composable
internal fun MovementRow(movement: Movement, onClick: (() -> Unit)? = null) {
    val tint = movementTint(movement)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) SurfaceHover else Color.Transparent, tween(120))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusSm)).background(bg)
            .then(if (onClick != null) Modifier.hoverable(interaction).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryMark(movementCode(movement), tint)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(movement.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Night, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${movement.category} · ${movement.method} · ${movement.dateLabel()}", color = Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AmountText(movement.amount, movement.income)
    }
}
