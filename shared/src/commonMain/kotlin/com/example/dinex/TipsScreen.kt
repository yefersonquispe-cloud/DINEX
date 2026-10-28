package com.example.dinex

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Tips marked as done stay done while the app runs. */
private val doneTips = mutableStateMapOf<String, Boolean>()

/** Full-screen financial tips built from the person's own data. */
@Composable
internal fun TipsScreen(data: FinancialData, onClose: () -> Unit, onAction: (TipAction) -> Unit) {
    val tips = remember(data) { personalTips(data) }
    val health = remember(data) { financialHealth(data) }
    val monthIncome = remember(data) { data.movements.filter { it.income && it.isInCurrentMonth() }.sumOf { it.amount } }
    var topic by remember { mutableStateOf<TipTopic?>(null) }
    val shown = tips.filter { topic == null || it.topic == topic }.sortedBy { doneTips[it.id] == true }

    BoxWithConstraints(Modifier.fillMaxSize().background(AppBg)) {
        DinexAuroraBackground()
        val wide = maxWidth >= DesktopBreakpoint
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.widthIn(max = 940.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Volver", role = Role.Button, onClick = onClose)
                        .semantics { contentDescription = "Volver" },
                    contentAlignment = Alignment.Center,
                ) { Icon(DinexNavigationIcons.Back, null, tint = Night, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text("Consejos para ti", color = Night, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Basados en tus movimientos de ${currentMonthName().lowercase()}", color = Gray, fontSize = 12.sp)
                }
            }
            LazyColumn(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight().align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = if (wide) 28.dp else 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "daily") { DailyTipCard() }
                item(key = "health") { HealthCard(health) }
                item(key = "personal-title") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TipsSection("Para ti", "${tips.size} ${if (tips.size == 1) "consejo" else "consejos"} según tus números")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TopicChip("Todos (${tips.size})", topic == null) { topic = null }
                            TipTopic.entries.forEach { option ->
                                val count = tips.count { it.topic == option }
                                if (count > 0) TopicChip("${option.label} ($count)", topic == option) { topic = option }
                            }
                        }
                    }
                }
                items(shown, key = { "tip-" + it.id }) { tip ->
                    PersonalTipCard(
                        tip = tip,
                        done = doneTips[tip.id] == true,
                        onDone = { doneTips[tip.id] = doneTips[tip.id] != true },
                        onAction = onAction,
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "learn-title") { TipsSection("Aprende en 1 minuto", "Mueve los controles y mira el resultado") }
                item(key = "budget-rule") { BudgetRuleCard(monthIncome) }
                item(key = "compound") { CompoundCard() }
                item(key = "assistant") {
                    Surface(
                        shape = RoundedCornerShape(RadiusMd), color = Card, border = BorderStroke(1.dp, Border),
                        modifier = Modifier.clip(RoundedCornerShape(RadiusMd)).clickable(onClickLabel = "Preguntar a Dinex IA") { onAction(TipAction.Assistant) },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            TopicBadge(DinexNavigationIcons.Sparkle, Indigo)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("¿Tienes una duda puntual?", color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Pregúntale a Dinex IA con tus datos", color = Gray, fontSize = 12.sp)
                            }
                            Icon(DinexNavigationIcons.Chevron, null, tint = Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item(key = "note") {
                    Text(
                        "Consejos educativos generados con tus datos en este dispositivo. No reemplazan la asesoría de un profesional.",
                        color = Gray, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyTipCard() {
    var offset by remember { mutableIntStateOf(0) }
    Surface(shape = RoundedCornerShape(RadiusLg), color = Color.Transparent, shadowElevation = 6.dp) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF0E5431), Green, Color(0xFF328859))))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(DinexNavigationIcons.Tips, null, tint = Color(0xFFFFE08A), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(if (offset == 0) "CONSEJO DEL DÍA" else "OTRO CONSEJO", color = Color.White.copy(alpha = .8f),
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = offset,
                transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
                label = "daily tip",
            ) { current ->
                Text(tipOfTheDay(offset = current), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 25.sp,
                    modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Otro consejo",
                color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                    .clickable(role = Role.Button, onClickLabel = "Mostrar otro consejo") { offset++ }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun HealthCard(health: FinancialHealth) {
    var expanded by remember { mutableStateOf(false) }
    val tint = when {
        health.score >= 70 -> Lime
        health.score >= 40 -> Orange
        else -> Red
    }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(if (started) health.score / 100f else 0f, tween(if (LocalDinexReducedMotion.current) 0 else 900), label = "score")
    Surface(
        shape = RoundedCornerShape(RadiusMd), color = Card, border = BorderStroke(1.dp, Border),
        modifier = Modifier.clip(RoundedCornerShape(RadiusMd)).clickable(onClickLabel = if (expanded) "Ocultar detalle" else "Ver detalle del puntaje") { expanded = !expanded }
            .animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(86.dp).semantics { contentDescription = "Salud financiera ${health.score} de 100" }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 9.dp.toPx()
                        drawArc(Border, 135f, 270f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(tint, 135f, 270f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Text("${(progress * 100).roundToInt()}", color = Night, fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tu salud financiera", color = Gray, fontSize = 12.sp)
                    Text(health.label, color = tint, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(if (expanded) "Toca para ocultar" else "Toca para ver qué la sube", color = Gray, fontSize = 12.sp)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                health.factors.forEach { factor ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row {
                            Text(factor.label, color = Night, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("${factor.points}/${factor.max}", color = Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(factor.detail, color = Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        DinexProgress(factor.points.toFloat() / factor.max, if (factor.points * 2 >= factor.max) Lime else Orange, height = 6.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalTipCard(tip: FinancialTip, done: Boolean, onDone: () -> Unit, onAction: (TipAction) -> Unit, modifier: Modifier = Modifier) {
    val tint = when (tip.priority) {
        3 -> Red
        2 -> Orange
        1 -> ElectricBlue
        else -> Lime
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RadiusMd),
        color = if (done) CardRaised else Card,
        border = BorderStroke(1.dp, if (done) Border else tint.copy(alpha = .35f)),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(if (done) Border else tint))
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TopicBadge(topicIcon(tip.topic), if (done) Gray else tint)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tip.topic.label.uppercase(), color = if (done) Gray else tint, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                        Text(tip.title, color = if (done) Gray else Night, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            textDecoration = if (done) TextDecoration.LineThrough else null)
                    }
                }
                if (!done) {
                    Spacer(Modifier.height(8.dp))
                    Text(tip.body, color = NightSoft, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!done && tip.action != null) {
                        TextButton(onClick = { onAction(tip.action) }) { Text(tip.action.label, color = GreenDark, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Checkbox, onClickLabel = if (done) "Marcar como pendiente" else "Marcar como hecho", onClick = onDone)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(if (done) Lime else Border.copy(alpha = .6f)),
                            contentAlignment = Alignment.Center,
                        ) { if (done) Icon(DinexNavigationIcons.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                        Spacer(Modifier.width(6.dp))
                        Text(if (done) "Hecho" else "Listo", color = if (done) Lime else Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** 50/30/20 split of a monthly income the person can slide. */
@Composable
private fun BudgetRuleCard(monthIncome: Double) {
    var income by remember { mutableFloatStateOf(monthIncome.takeIf { it in 100.0..20_000.0 }?.toFloat() ?: 1500f) }
    val parts = listOf(
        Triple("Necesidades", .50, Indigo),
        Triple("Gustos", .30, Orange),
        Triple("Ahorro", .20, Lime),
    )
    LearnCard("Regla 50/30/20", "Una forma simple de repartir tu dinero cada mes.") {
        Text("Si ganas ${money(income.toDouble())} al mes:", color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        DinexSlider(income, 300f..10_000f) { income = (it / 50).roundToInt() * 50f }
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))) {
            parts.forEach { (_, share, color) -> Box(Modifier.weight(share.toFloat()).fillMaxHeight().background(color)) }
        }
        Spacer(Modifier.height(10.dp))
        parts.forEach { (label, share, color) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text("$label · ${(share * 100).roundToInt()}%", color = NightSoft, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(money(income * share), color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

/** How a fixed monthly saving grows with time and interest. */
@Composable
private fun CompoundCard() {
    var monthly by remember { mutableFloatStateOf(100f) }
    var years by remember { mutableFloatStateOf(5f) }
    var rate by remember { mutableFloatStateOf(5f) }
    val total = futureValue(monthly.toDouble(), years.roundToInt(), rate / 100.0)
    val deposited = monthly.toDouble() * years.roundToInt() * 12
    LearnCard("Cómo crece tu ahorro", "Guardar seguido y dejar que gane intereses.") {
        Text("Ahorro mensual: ${money(monthly.toDouble())}", color = NightSoft, fontSize = 13.sp)
        DinexSlider(monthly, 10f..1000f) { monthly = (it / 10).roundToInt() * 10f }
        Text("Durante: ${years.roundToInt()} ${if (years.roundToInt() == 1) "año" else "años"}", color = NightSoft, fontSize = 13.sp)
        DinexSlider(years, 1f..30f) { years = it.roundToInt().toFloat() }
        Text("Interés anual: ${rate.roundToInt()}%", color = NightSoft, fontSize = 13.sp)
        DinexSlider(rate, 0f..15f) { rate = it.roundToInt().toFloat() }
        Spacer(Modifier.height(6.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = Lime.copy(alpha = .12f)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Tendrías", color = Gray, fontSize = 12.sp)
                Text(money(total), color = Night, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Text("Pones ${money(deposited)} y ganas ${money((total - deposited).coerceAtLeast(0.0))} en intereses.", color = NightSoft, fontSize = 12.sp)
            }
        }
        Text("Ejemplo con interés fijo; en la vida real las tasas cambian.", color = Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LearnCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(RadiusMd), color = Card, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = Night, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(subtitle, color = Gray, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DinexSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Green, inactiveTrackColor = Border),
    )
}

@Composable
private fun TipsSection(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(title, color = Night, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, color = Gray, fontSize = 12.sp)
    }
}

@Composable
private fun TopicChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Green, selectedLabelColor = Color.White, labelColor = NightSoft),
    )
}

@Composable
private fun TopicBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

private fun topicIcon(topic: TipTopic) = when (topic) {
    TipTopic.Savings -> DinexNavigationIcons.Wallet
    TipTopic.Spending -> DinexNavigationIcons.Analysis
    TipTopic.Goals -> DinexNavigationIcons.Sparkle
    TipTopic.Habits -> DinexNavigationIcons.Check
}
