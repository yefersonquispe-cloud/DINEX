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
// HOME / WALLET
// Main dashboard, the two-card carousel, daily streak and budget.
// ═══════════════════════════════════════════════════════════════════════════
@Composable
internal fun HomePage(
    movements: List<Movement>, dailyLimit: Float, streak: Int, savedToday: Boolean, savings: Double, goalName: String, goalTarget: Double,
    onDailyLimitChange: (Float) -> Unit, onSaveToday: () -> Unit,
    onHistory: () -> Unit,
) {
    val income = movements.asSequence().filter { it.income }.sumOf { it.amount }
    val expenses = movements.asSequence().filterNot { it.income }.sumOf { it.amount }
    val today = movements.asSequence().filter { (!it.income && it.day == currentDayOfMonth()) }.sumOf { it.amount }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      LazyColumn(
        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().align(Alignment.TopCenter),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
            Text("Buenas tardes", color = Gray, fontSize = 12.sp)
            Text("Tu dinero, bajo control", color = Night, fontWeight = FontWeight.Black, fontSize = 25.sp)
        }
        // El botón central “+” es el único acceso general para registrar.
        // Así evitamos repetir gasto/ingreso/ahorro en varias zonas del inicio.
        item { BalanceCarousel(income, expenses, savings, streak, goalName, goalTarget) }
        if (goalName.isNotBlank() && goalTarget > 0) item { SavingsGoalCard(goalName, savings, goalTarget) }
        item { SavingsStreak(streak, savedToday, onSaveToday) }
        item { DailyBudget(today, dailyLimit, onDailyLimitChange) }
        item { SpendingOverview(movements) }
        item { RecentCard(movements.take(4), onHistory) }
        item { Spacer(Modifier.height(8.dp)) }
      }
    }
}

@Composable
internal fun SavingsGoalCard(name: String, saved: Double, target: Double) {
    val progress = (saved / target).toFloat().coerceIn(0f, 1f)
    Surface(shape = RoundedCornerShape(26.dp), color = Card, border = androidx.compose.foundation.BorderStroke(1.dp, Orange.copy(alpha = .35f))) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF374A91), Color(0xFF171B3E)))), contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.dinex_mascot_ai), "Meta $name", Modifier.size(70.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("MI META DE AHORRO", color = Orange, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(name, color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Orange, trackColor = CardRaised)
                Spacer(Modifier.height(5.dp))
                Text("${money(saved)} de ${money(target)} · ${(progress * 100).toInt()}%", color = Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
internal fun BalanceCarousel(income: Double, expenses: Double, savings: Double, streak: Int, goalName: String = "", goalTarget: Double = 0.0) {
    val listState = rememberLazyListState()
    Column {
        BoxWithConstraints {
            // En escritorio no dejamos que la tarjeta se estire hasta llenar todo el monitor.
            // En móvil conserva el ancho disponible y sigue siendo deslizable.
            val cardWidth = if (maxWidth > 700.dp) (maxWidth / 2 - 10.dp).coerceAtMost(520.dp) else maxWidth
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { BalanceHero(income, expenses, Modifier.width(cardWidth)) }
                item { SavingsHero(savings, streak, goalName, goalTarget, Modifier.width(cardWidth)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(2) { index ->
                Box(Modifier.padding(horizontal = 3.dp).size(if (listState.firstVisibleItemIndex == index) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (listState.firstVisibleItemIndex == index) Lime else Border))
            }
        }
        Text("Desliza para ver tus tarjetas Dinex", color = Gray, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun BalanceHero(income: Double, expenses: Double, modifier: Modifier = Modifier) {
    val balance = income - expenses
    Box(
        modifier.aspectRatio(1.72f).clip(RoundedCornerShape(28.dp)),
    ) {
        Image(painterResource(Res.drawable.dinex_card_balance_bg), "Tarjeta Dinex", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("SALDO DISPONIBLE", color = Color.White.copy(alpha = .62f), fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(money(balance), color = Color.White, fontWeight = FontWeight.Black, fontSize = 32.sp)
                }
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .16f)) {
                    Text("+8.4%", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroMini("INGRESOS", income, true, Modifier.weight(1f))
                HeroMini("GASTOS", expenses, false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun SavingsHero(savings: Double, streak: Int, goalName: String = "", goalTarget: Double = 0.0, modifier: Modifier = Modifier) {
    val level = streakLevel(streak)
    val goal = if (goalTarget > 0) goalTarget else when { savings < 500 -> 500.0; savings < 1000 -> 1000.0; savings < 2500 -> 2500.0; else -> 5000.0 }
    val goalTitle = if (goalTarget > 0 && goalName.isNotBlank()) "META: ${goalName.uppercase()}" else "MI TARJETA DE AHORRO"
    Box(modifier.aspectRatio(1.72f).clip(RoundedCornerShape(28.dp))) {
        Image(painterResource(Res.drawable.dinex_card_savings_bg), "Tarjeta Dinex Ahorro", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(goalTitle, color = Color.White.copy(alpha = .68f), fontSize = 10.sp, letterSpacing = 1.1.sp); Text(money(savings), color = Color.White, fontWeight = FontWeight.Black, fontSize = 32.sp) }
                MiniWalletMedal(level.color, 54.dp)
            }
            Spacer(Modifier.height(18.dp))
            Text(if (goalTarget > 0) "Objetivo ${money(goal)}" else "Meta sugerida ${money(goal)}", color = Color.White.copy(alpha = .70f), fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { (savings / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Lime, trackColor = Color.White.copy(alpha = .16f))
        }
    }
}

@Composable
internal fun HeroMini(label: String, value: Double, positive: Boolean, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .09f)) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = Color.White.copy(alpha = .55f), fontSize = 9.sp, letterSpacing = .8.sp)
            Text((if (positive) "+ " else "- ") + money(value), color = if (positive) Lime else Color(0xFFFFA19B),
                fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
internal fun SavingsStreak(streak: Int, savedToday: Boolean, onSaveToday: () -> Unit) {
    val level = streakLevel(streak)
    val next = nextStreakLevel(streak)
    Surface(shape = RoundedCornerShape(26.dp), color = level.color.copy(alpha = .08f), border = androidx.compose.foundation.BorderStroke(1.dp, level.color.copy(alpha = .34f))) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniWalletMedal(level.color, 54.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Racha de ahorro", color = Night, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("$streak días · Nivel ${level.name}", color = level.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (next != null) Text("${next.startsAt - streak} días para ${next.name}", color = Gray, fontSize = 8.sp)
                }
                FilledTonalButton(onClick = onSaveToday, enabled = !savedToday,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = level.color.copy(alpha = .16f), contentColor = level.color)) {
                    Text(if (savedToday) "Registrado" else "Ahorré hoy", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { index, day ->
                    val complete = if (streak <= 0) false else index < (((streak - 1) % 7) + 1).coerceAtMost(7)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(25.dp).clip(RoundedCornerShape(8.dp)).background(if (complete) level.color else CardRaised), contentAlignment = Alignment.Center) {
                            Text(if (complete) "▥" else day, color = if (complete) Color.White else level.color.copy(alpha = .7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(3.dp)); Text(day, color = level.color.copy(alpha = .7f), fontSize = 8.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp)); Text("CAMINO DE 365 DÍAS", color = Gray, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(streakLevels) { item ->
                    val unlocked = streak >= item.startsAt
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { MiniWalletMedal(if (unlocked) item.color else Border, 42.dp); Text(item.name, color = if (unlocked) item.color else Gray.copy(alpha = .55f), fontSize = 7.sp, fontWeight = FontWeight.Bold); Text(if (unlocked) "✓" else "${item.startsAt}d", color = Gray, fontSize = 7.sp) }
                }
            }
        }
    }
}

@Composable
internal fun DailyBudget(today: Double, limit: Float, onChange: (Float) -> Unit) {
    val ratio = (today / limit.coerceAtLeast(1f)).toFloat()
    val warning = ratio >= .8f
    Surface(shape = RoundedCornerShape(26.dp), color = Card, border = androidx.compose.foundation.BorderStroke(1.dp, (if (warning) Red else Indigo).copy(alpha = .28f))) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF171821), if (warning) Color(0xFF29171C) else Color(0xFF17152A))))) {
        Column(Modifier.padding(18.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("PRESUPUESTO DE HOY", color = if (warning) Red else GreenDark, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(3.dp)); Text(if (warning) "Momento de bajar el ritmo" else "Vas construyendo un buen día", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("${money(today)} usados de ${money(limit.toDouble())}", color = Gray, fontSize = 11.sp)
                }
                Box(Modifier.size(56.dp).clip(CircleShape).background((if (warning) Red else Indigo).copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("${(ratio * 100).toInt()}%", color = if (warning) Red else GreenDark, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            }
            Spacer(Modifier.height(11.dp))
            LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
                color = if (warning) Red else Green, trackColor = Border)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (warning) "Baja el ritmo para no excederte" else "Vas bien, aún tienes ${money(max(limit - today, 0.0))}",
                    color = if (warning) Red else GreenDark, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("Ajustar", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Slider(value = limit, onValueChange = onChange, valueRange = 20f..120f, steps = 9, colors = SliderDefaults.colors(thumbColor = if (warning) Red else Indigo, activeTrackColor = if (warning) Red else Indigo, inactiveTrackColor = Border))
        }
        }
    }
}

@Composable
internal fun SpendingOverview(movements: List<Movement>) {
    val expenses = categories.map { category -> category to movements.filter { !it.income && it.category == category.name }.sumOf { it.amount } }
    val realTotal = expenses.sumOf { it.second }
    val safeTotal = realTotal.coerceAtLeast(1.0)
    var selected by remember { mutableStateOf<Int?>(null) }
    val visible = selected?.let { listOf(expenses[it]) } ?: expenses.filter { it.second > 0 }
    Surface(shape = RoundedCornerShape(22.dp), color = Card, border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("¿A dónde se fue?", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(if (selected == null) "Toca un color para filtrar" else "Filtro activo · toca de nuevo para quitar", color = Gray, fontSize = 10.sp)
                }
                Text(money(realTotal), fontWeight = FontWeight.Black, color = Night)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Donut(expenses.map { it.second }, expenses.map { it.first.color }, safeTotal, selected) { index -> selected = if (selected == index) null else index }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visible.take(5).forEach { (category, value) ->
                        Row(Modifier.clip(RoundedCornerShape(9.dp)).clickable { val index = expenses.indexOfFirst { it.first.name == category.name }; selected = if (selected == index) null else index }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(category.color)); Spacer(Modifier.width(8.dp))
                            Text(category.name, modifier = Modifier.weight(1f), color = Gray, fontSize = 10.sp)
                            Text(if (selected == null) "${percent(value, safeTotal)}%" else money(value), color = if (selected == null) Night else category.color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                    if (selected != null) Text("Ver todas las categorías", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.clickable { selected = null }.padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
internal fun Donut(values: List<Double>, chartColors: List<Color>, total: Double, selected: Int?, onSelect: (Int) -> Unit) {
    Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
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
                drawArc(chartColors[index].copy(alpha = if (active) 1f else .16f), start, sweep, false, style = Stroke((if (selected == index) 22.dp else 18.dp).toPx(), cap = StrokeCap.Butt)); start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val value = selected?.let { values.getOrNull(it) } ?: values.maxOrNull() ?: 0.0
            Text("${percent(value, total)}%", fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(selected?.let { categories.getOrNull(it)?.name?.lowercase() } ?: "principal", color = Gray, fontSize = 8.sp)
        }
    }
}

@Composable
internal fun RecentCard(movements: List<Movement>, onHistory: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = Card, border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Últimos movimientos", fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("Ver todos", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.clickable(onClick = onHistory).padding(6.dp))
            }
            Spacer(Modifier.height(7.dp)); movements.forEach { MovementRow(it) }
        }
    }
}

@Composable
internal fun MovementRow(movement: Movement) {
    val info = categories.firstOrNull { it.name == movement.category }
    val tint = if (movement.income) Green else info?.color ?: Gray
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
            Text(if (movement.income) "IN" else info?.code ?: "GT", color = tint, fontWeight = FontWeight.Black, fontSize = 10.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(movement.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${movement.day} ${currentMonthShort().lowercase()}  ·  ${movement.method}", color = Gray, fontSize = 9.sp)
        }
        Text((if (movement.income) "+" else "-") + money(movement.amount), color = if (movement.income) GreenDark else Red,
            fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}
