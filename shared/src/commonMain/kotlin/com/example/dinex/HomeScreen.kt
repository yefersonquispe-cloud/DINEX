package com.example.dinex

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HomePage(
    movements: List<Movement>, dailyLimit: Float, streak: Int, savings: Double, goalName: String, goalTarget: Double,
    onDailyLimitChange: (Float) -> Unit,
    onHistory: () -> Unit,
    onAdd: () -> Unit = {},
    onGoalDetails: () -> Unit = {},
    goals: List<SavingsGoal> = emptyList(),
    onOpenGoal: (Int) -> Unit = {},
    onScan: () -> Unit = {},
    onVoice: () -> Unit = {},
    onGoals: () -> Unit = {},
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
                    Box(Modifier.weight(1f)) { SavingsHero(savings, goals, onClick = onGoalDetails, onOpenGoal = onOpenGoal) }
                } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BalanceStrip(balance, income, expenses, savingRate)
                    SavingsHero(savings, goals, onClick = onGoalDetails, onOpenGoal = onOpenGoal)
                }
            }
            item { QuickActions(onScan = onScan, onVoice = onVoice, onGoals = onGoals, onHistory = onHistory, wide = desktop) }
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
private fun QuickActions(onScan: () -> Unit, onVoice: () -> Unit, onGoals: () -> Unit, onHistory: () -> Unit, wide: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 10.dp)) {
        // Un solo color de marca y trazos finos para un estilo más formal.
        QuickActionTile("Escanear", "Foto del comprobante", DinexNavigationIcons.ScanReceipt, GreenDark, onScan, wide, Modifier.weight(1f))
        QuickActionTile("Voz", "Dicta un gasto", DinexNavigationIcons.Microphone, GreenDark, onVoice, wide, Modifier.weight(1f))
        QuickActionTile("Metas", "Ahorra para algo", DinexNavigationIcons.Target, GreenDark, onGoals, wide, Modifier.weight(1f))
        QuickActionTile("Historial", "Todos tus movimientos", DinexNavigationIcons.History, GreenDark, onHistory, wide, Modifier.weight(1f))
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Surface(
        modifier = modifier.clip(RoundedCornerShape(RadiusMd)).hoverable(interaction).clickable(onClickLabel = label, onClick = onClick),
        shape = RoundedCornerShape(RadiusMd),
        color = if (hovered) SurfaceHover else Card,
        border = BorderStroke(1.dp, if (hovered) tint.copy(alpha = .35f) else Border),
    ) {
        val badge: @Composable () -> Unit = {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Mint).border(1.dp, tint.copy(alpha = .18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
        if (wide) {
            // Desktop: one compact row with a hint, instead of a tall, mostly empty tile.
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                badge()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text(hint, color = Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                badge()
                Spacer(Modifier.height(8.dp))
                Text(label, color = Night, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
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

/**
 * Tarjeta de ahorro del inicio: el total reservado, el avance de todas las metas juntas
 * y un resumen de hasta tres metas. Tocar la tarjeta abre "Mis metas"; tocar una meta la abre directo.
 */
@Composable
internal fun SavingsHero(
    savings: Double,
    goals: List<SavingsGoal>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onOpenGoal: (Int) -> Unit = {},
) {
    val totalTarget = goals.sumOf { it.target }
    val withTarget = goals.filter { it.target > 0 }
    val progress = if (withTarget.isNotEmpty()) (withTarget.sumOf { minOf(it.saved, it.target) } / withTarget.sumOf { it.target }).toFloat().coerceIn(0f, 1f) else 0f
    val missing = (totalTarget - withTarget.sumOf { minOf(it.saved, it.target) }).coerceAtLeast(0.0)
    DinexCard(modifier = modifier.fillMaxWidth(), accent = Green, onClick = onClick) {
        // Encabezado
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Green.copy(alpha = if (DinexThemeState.dark) .22f else .09f)),
                contentAlignment = Alignment.Center,
            ) { Icon(DinexNavigationIcons.Target, null, tint = GreenDark, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Mis ahorros", color = Night, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Cada sol te acerca a tu meta", color = Gray, fontSize = 12.sp)
            }
            Text(
                when (goals.size) { 0 -> "Sin metas"; 1 -> "1 meta"; else -> "${goals.size} metas" },
                color = NightSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.border(1.dp, Border, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // Total ahorrado y avance general
        Spacer(Modifier.height(18.dp))
        Text("Total ahorrado", color = Gray, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(moneyGrouped(savings), color = Night, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
            if (totalTarget > 0) Text(savingsPercent(progress), color = GreenDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (totalTarget > 0) {
            Spacer(Modifier.height(10.dp))
            SavingsProgress(progress, Green, 6.dp)
            Spacer(Modifier.height(8.dp))
            Row {
                Text("Meta total ${moneyGrouped(totalTarget)}", color = Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(if (missing > 0) "Faltan ${moneyGrouped(missing)}" else "¡Metas cumplidas!", color = Gray, fontSize = 11.sp)
            }
        }

        // Metas
        Spacer(Modifier.height(18.dp))
        if (goals.isEmpty()) {
            Text("Crea una meta para darle un propósito a tu ahorro.", color = Gray, fontSize = 13.sp)
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).border(1.dp, Border.copy(alpha = .8f), RoundedCornerShape(14.dp))) {
                // Se muestran primero las metas con más avance, sin alterar sus importes.
                val shown = goals.sortedByDescending { if (it.target > 0) it.saved / it.target else 0.0 }.take(3)
                shown.forEachIndexed { index, goal ->
                    SavingsGoalRow(goal) { onOpenGoal(goal.id) }
                    if (index < shown.lastIndex) HorizontalDivider(color = Border.copy(alpha = .7f), modifier = Modifier.padding(start = 58.dp))
                }
            }
            if (goals.size > 3) {
                Spacer(Modifier.height(8.dp))
                Text("Y ${goals.size - 3} ${if (goals.size - 3 == 1) "meta más" else "metas más"}", color = Gray, fontSize = 11.sp)
            }
        }

        if (onClick != null) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (goals.isEmpty()) "Crear mi primera meta" else "Ver todas mis metas",
                    color = GreenDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(DinexNavigationIcons.Chevron, null, tint = GreenDark, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Una meta en una fila compacta: ícono pequeño, nombre, cuánto lleva de cuánto y su barra. */
@Composable
private fun SavingsGoalRow(goal: SavingsGoal, onOpen: () -> Unit) {
    val theme = goalTheme(goal.name)
    val name = displayGoalName(goal.name)
    val goalProgress = if (goal.target > 0) (goal.saved / goal.target).toFloat().coerceIn(0f, 1f) else 0f
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Abrir meta $name", onClick = onOpen).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(theme.top.copy(alpha = if (DinexThemeState.dark) .22f else .12f)),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(theme.main), null, Modifier.size(24.dp), contentScale = ContentScale.Fit) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Night, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (goal.target > 0) Text(savingsPercent(goalProgress), color = NightSoft, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (goal.target > 0) "${moneyGrouped(goal.saved)} de ${moneyGrouped(goal.target)}" else "${moneyGrouped(goal.saved)} ahorrados",
                color = Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (goal.target > 0) {
                Spacer(Modifier.height(8.dp))
                SavingsProgress(goalProgress, Green, 4.dp)
            }
        }
    }
}

/** 0.2 %, 4.5 %, 37 %: con decimal cuando el avance es pequeño, para que no diga solo "<1%". */
private fun savingsPercent(progress: Float): String {
    val value = progress * 100
    return when {
        value <= 0f -> "0%"
        value < 10f -> "${fixed(value.toDouble(), 1)}%"
        else -> "${value.toInt()}%"
    }
}

/** Barra fina y redondeada. Si hay algo ahorrado, siempre se ve al menos un punto de avance. */
@Composable
private fun SavingsProgress(progress: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        tween(if (LocalDinexReducedMotion.current) 0 else 520),
        label = "avance de ahorro",
    )
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(if (DinexThemeState.dark) CardRaised else Color(0xFFE9F0EB))) {
        if (progress > 0f) {
            val width = maxOf(maxWidth * animated, height * 2)
            Box(
                Modifier.width(width).fillMaxHeight().clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = .78f), color))),
            )
        }
    }
}

@Composable
internal fun SavingsStreak(streak: Int, savedToday: Boolean, onSaveToday: () -> Unit, elevated: Boolean = false) {
    val level = streakLevel(streak)
    val next = nextStreakLevel(streak)
    DinexCard(accent = level.color, elevated = elevated) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakBadge(level, 58.dp, locked = streak == 0)
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
                        if (complete) Icon(DinexNavigationIcons.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        else Text(day, color = Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
                    StreakBadge(item, 48.dp, locked = !unlocked)
                    Spacer(Modifier.height(4.dp))
                    Text(item.name, color = if (unlocked) readableAccent(item.color) else Gray.copy(alpha = .75f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    if (unlocked) Icon(DinexNavigationIcons.Check, null, tint = Lime, modifier = Modifier.size(10.dp))
                    else Text("${item.startsAt}d", color = Gray, fontSize = 7.sp)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodMark(movement.method, tint)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${movement.category} · ${movement.method} · ${movement.dateLabel()}",
                    color = Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AmountText(movement.amount, movement.income)
    }
}
