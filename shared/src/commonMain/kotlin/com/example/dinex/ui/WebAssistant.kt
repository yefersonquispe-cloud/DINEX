package com.example.dinex

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_app_icon
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════
// DINEX IA (web) y panel de marca de la pantalla de acceso.
// ═══════════════════════════════════════════════════════════════════════════

// ---------------------------------------------------------------- Acceso (panel de marca)

/** Panel izquierdo de la pantalla de acceso en la web: titular, beneficios y tarjetas flotantes. */
@Composable
internal fun WebAuthShowcase(modifier: Modifier = Modifier) {
    val reduced = LocalDinexReducedMotion.current
    val transition = rememberInfiniteTransition(label = "vitrina")
    val float by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "flotar")
    val phase = if (reduced) 0f else float
    BoxWithConstraints(
        modifier.background(Brush.linearGradient(listOf(W.forestDeep, W.forest, W.forest2))).drawBehind {
            val step = 34.dp.toPx()
            var x = step
            while (x < size.width) { drawLine(Color.White.copy(alpha = .03f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = step
            while (y < size.height) { drawLine(Color.White.copy(alpha = .03f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            val glow = Offset(size.width * (.78f + .05f * sin(phase * 2 * PI).toFloat()), size.height * .6f)
            drawCircle(Brush.radialGradient(listOf(W.lime.copy(alpha = .18f), Color.Transparent), glow, size.minDimension * .6f), size.minDimension * .6f, glow)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2E8B57).copy(alpha = .3f), Color.Transparent), Offset(0f, 0f), size.minDimension * .5f), size.minDimension * .5f, Offset(0f, 0f))
        },
    ) {
        val tall = maxHeight >= 760.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 44.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(Res.drawable.dinex_app_icon), "DINEX", Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Text("DINEX", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.4.sp)
                Spacer(Modifier.width(10.dp))
                Text("WEB", color = W.forest, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
                    modifier = Modifier.clip(CircleShape).background(W.lime).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    append("Tu dinero,\ncon ")
                    withStyle(SpanStyle(color = W.lime)) { append("propósito.") }
                },
                color = Color.White, fontSize = 50.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Registra tus gastos en segundos, ahorra para tus metas y entiende a dónde va cada sol, con gráficos claros y consejos con IA. En el celular y en la web, siempre sincronizado.",
                color = Color.White.copy(alpha = .7f), fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.widthIn(max = 500.dp),
            )
            if (tall) {
                Spacer(Modifier.height(34.dp))
                ShowcasePreview(phase)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(DinexNavigationIcons.Shield to "Datos cifrados", DinexNavigationIcons.Refresh to "Android y web", DinexNavigationIcons.Sparkle to "Consejos con IA",
                    DinexNavigationIcons.Analysis to "Gráficos interactivos").forEach { (icon, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = W.lime, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(label, color = Color.White.copy(alpha = .7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Composición "flotante" con piezas reales del producto: saldo, meta, salud financiera y medallas. */
@Composable
private fun ShowcasePreview(phase: Float) {
    val glass = Color.White.copy(alpha = .07f)
    val edge = Color.White.copy(alpha = .14f)
    fun bob(shift: Float) = sin((phase + shift) * 2 * PI).toFloat()
    Box(Modifier.widthIn(max = 540.dp).fillMaxWidth().height(270.dp)) {
        Column(
            Modifier.fillMaxWidth(.8f).graphicsLayer { translationY = bob(0f) * 4.dp.toPx() }
                .clip(RoundedCornerShape(20.dp)).background(glass).border(1.dp, edge, RoundedCornerShape(20.dp)).padding(22.dp),
        ) {
            Text("SALDO DISPONIBLE", color = W.lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(6.dp))
            Text("S/ 2,480.50", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
            Spacer(Modifier.height(8.dp))
            LineAreaChart(
                listOf(ChartSeries("Saldo", listOf(1820.0, 1790.0, 1930.0, 1880.0, 2040.0, 1995.0, 2150.0, 2110.0, 2290.0, 2260.0, 2480.0), W.lime)),
                labels = emptyList(), modifier = Modifier.fillMaxWidth().height(60.dp), onDark = true, showAxis = false, tooltipTitle = { "Día ${it + 1}" },
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Ingresos", color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
                    Text("+ S/ 3,200.00", color = W.mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Gastos", color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
                    Text("- S/ 719.50", color = W.coral, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Meta en progreso.
        Column(
            Modifier.align(Alignment.BottomEnd).offset(y = 8.dp).graphicsLayer { translationY = bob(.33f) * 6.dp.toPx() }.width(224.dp)
                .shadow(24.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE1F0E6)), contentAlignment = Alignment.Center) {
                    Icon(DinexNavigationIcons.Target, null, tint = Color(0xFF0D6A43), modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Meta: Laptop", color = Color(0xFF101B15), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("S/ 1,984 de S/ 3,200", color = Color(0xFF8A978E), fontSize = 11.sp)
                }
                Text("62%", color = Color(0xFF0D6A43), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFFE2E7E1))) {
                Box(Modifier.fillMaxWidth(.62f).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF5ED49B), Color(0xFF0D6A43)))))
            }
        }
        // Salud financiera.
        Row(
            Modifier.align(Alignment.CenterEnd).offset(x = 10.dp, y = (-24).dp).graphicsLayer { translationY = bob(.66f) * 5.dp.toPx() }
                .clip(RoundedCornerShape(16.dp)).background(W.forest).border(1.dp, edge, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RingProgress(.78f, W.lime, 36.dp, 4.dp, track = Color.White.copy(alpha = .12f)) {
                Text("78", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Salud financiera", color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
                Text("Saludable", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = (-16).dp), horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            listOf(2, 4, 14).forEach { index -> StreakBadge(streakLevels[index], 50.dp) }
        }
    }
}

// ---------------------------------------------------------------- Dinex IA (web)

/** Chat de Dinex IA para la web: conversación a la izquierda y contexto y consultas a la derecha. */
@Composable
internal fun WebAssistantPage(assistant: GeminiAssistant, model: AssistantViewModel, data: FinancialData) {
    val chat by model.state.collectAsState()
    val messages = chat.messages
    val loading = chat.loading
    val remaining = chat.queriesLeft
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(model) { model.refreshQuota() }
    LaunchedEffect(messages.size, loading) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1 + if (loading) 1 else 0) }
    val movements = data.movements
    val streak = data.activeStreak()

    fun send(question: String) {
        if (loading) return
        model.send(question, assistant, data)
        if (question == draft) draft = ""
    }

    PageFrame(scroll = false) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val side = maxWidth >= 980.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().dinexEntrance().clip(WebCardShape).background(W.surface).border(1.dp, W.line, WebCardShape)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistantAvatar(40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Dinex IA", color = W.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(W.positive))
                                Spacer(Modifier.width(6.dp))
                                Text("En línea · responde con tus datos de DINEX", color = W.ink3, fontSize = 12.sp)
                            }
                        }
                        QuotaPill(remaining)
                    }
                    HorizontalDivider(color = W.line)
                    Box(Modifier.weight(1f).fillMaxWidth().background(W.surfaceAlt.copy(alpha = .5f))) {
                        if (messages.none { it.user } && !loading) {
                            AssistantWelcome(remaining > 0) { send(it) }
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(), state = listState,
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(messages) { ChatRow(it) }
                                if (loading) item { ThinkingRow() }
                            }
                        }
                    }
                    HorizontalDivider(color = W.line)
                    ChatInput(draft, { if (it.length <= 500) draft = it }, enabled = remaining > 0 && !loading, onSend = { send(draft) })
                }
                if (side) Column(Modifier.width(310.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    val monthExpense = movements.filter { !it.income && it.isInCurrentMonth() }.sumOf { it.amount }
                    WCard(Modifier.fillMaxWidth()) {
                        CardTitle("Lo que Dinex IA sabe", "Solo tu resumen de DINEX", DinexNavigationIcons.Sparkle)
                        Spacer(Modifier.height(12.dp))
                        ContextLine("Saldo disponible", moneyGrouped(data.availableBalance()), W.ink)
                        ContextLine("Gastos del mes", moneyGrouped(monthExpense), W.negative)
                        ContextLine("Ahorrado", moneyGrouped(data.savings), W.brand)
                        ContextLine("Racha", "$streak ${if (streak == 1) "día" else "días"}", W.ink)
                        ContextLine("Límite diario", moneyGrouped(data.dailyLimit.toDouble()), W.ink)
                    }
                    WCard(Modifier.fillMaxWidth()) {
                        CardTitle("Consultas de hoy", "Se renuevan cada día")
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RingProgress(remaining.toFloat() / DAILY_AI_QUERIES, W.brand, 64.dp, 7.dp) {
                                Text("$remaining", color = W.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text("de $DAILY_AI_QUERIES consultas disponibles hoy", color = W.ink2, fontSize = 13.sp)
                        }
                    }
                    WCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(DinexNavigationIcons.Shield, null, tint = W.brand, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Dinex IA no accede a tus bancos ni pide claves. Puede equivocarse: revisa cualquier decisión importante.",
                                color = W.ink2, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantAvatar(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * .32f)).background(Brush.linearGradient(listOf(W.forest2, W.forest))),
        contentAlignment = Alignment.Center,
    ) { Icon(DinexNavigationIcons.Sparkle, null, tint = W.lime, modifier = Modifier.size(size * .5f)) }
}

@Composable
private fun QuotaPill(remaining: Int) {
    Row(Modifier.clip(CircleShape).background(W.surfaceAlt).border(1.dp, W.line, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(DAILY_AI_QUERIES) { i ->
            Box(Modifier.padding(end = 3.dp).size(width = 12.dp, height = 5.dp).clip(CircleShape).background(if (i < remaining) W.brand else W.line))
        }
        Spacer(Modifier.width(6.dp))
        Text(if (remaining > 0) "$remaining de $DAILY_AI_QUERIES hoy" else "Sin consultas hoy", color = if (remaining > 0) W.ink2 else W.negative, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AssistantWelcome(enabled: Boolean, onAsk: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AssistantAvatar(68.dp)
        Spacer(Modifier.height(18.dp))
        Text("¿En qué te ayudo hoy?", color = W.ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
        Spacer(Modifier.height(6.dp))
        Text("Pregunta sobre tus gastos, una compra o tu plan de ahorro. Respondo con tus números reales.",
            color = W.ink2, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 460.dp))
        Spacer(Modifier.height(26.dp))
        val prompts = listOf(
            Triple(DinexNavigationIcons.Analysis, "¿En qué gasto más?", "Compara tus categorías del mes"),
            Triple(DinexNavigationIcons.Plan, "Hazme un plan semanal", "Tres pasos medibles para esta semana"),
            Triple(DinexNavigationIcons.Wallet, "¿Puedo gastar S/ 80?", "Calcula cómo queda tu saldo"),
            Triple(DinexNavigationIcons.Target, "¿Cómo voy con mi ahorro?", "Avance de tus metas y racha"),
        )
        Column(Modifier.widthIn(max = 580.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            prompts.chunked(2).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (icon, title, hint) ->
                        val shape = RoundedCornerShape(16.dp)
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        Row(
                            Modifier.weight(1f).fillMaxHeight().clip(shape).background(if (hovered) W.brandSoft else W.surface).border(1.dp, if (hovered) W.brand else W.line, shape)
                                .hoverable(interaction).clickable(enabled = enabled) { onAsk(title) }.pointerHoverIcon(PointerIcon.Hand).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(W.brandSoft), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = W.brand, modifier = Modifier.size(17.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(title, color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(hint, color = W.ink3, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        if (!enabled) {
            Spacer(Modifier.height(18.dp))
            Text("Usaste tus $DAILY_AI_QUERIES consultas de hoy. Vuelve mañana.", color = W.negative, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChatRow(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.user) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!message.user) { AssistantAvatar(30.dp); Spacer(Modifier.width(10.dp)) }
        val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (message.user) 18.dp else 4.dp, bottomEnd = if (message.user) 4.dp else 18.dp)
        SelectionContainer {
            Text(
                message.text,
                color = when { message.user -> Color.White; message.error -> W.negative; else -> W.ink },
                fontSize = 14.sp, lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = 580.dp).clip(shape)
                    .background(when {
                        message.user -> Brush.linearGradient(listOf(W.brand, W.forest2))
                        message.error -> SolidColor(W.negative.copy(alpha = .08f))
                        else -> SolidColor(W.surface)
                    })
                    .then(if (message.user) Modifier else Modifier.border(1.dp, W.line, shape))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun ThinkingRow() {
    val transition = rememberInfiniteTransition(label = "pensando")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "puntos")
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistantAvatar(30.dp)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.clip(RoundedCornerShape(18.dp)).background(W.surface).border(1.dp, W.line, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
            repeat(3) { i ->
                val alpha = ((sin((phase * 2 * PI) - i * .9) + 1) / 2).toFloat() * .7f + .3f
                Box(Modifier.padding(end = 5.dp).size(7.dp).clip(CircleShape).background(W.brand.copy(alpha = alpha)))
            }
            Spacer(Modifier.width(4.dp))
            Text("Revisando tus números…", color = W.ink3, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ChatInput(value: String, onChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(16.dp)
        Box(Modifier.weight(1f).heightIn(min = 50.dp).clip(shape).background(W.surfaceAlt).border(1.dp, W.line, shape).padding(horizontal = 16.dp, vertical = 15.dp)) {
            if (value.isEmpty()) Text(if (enabled) "Escribe tu pregunta y presiona Enter…" else "Vuelve mañana para preguntar", color = W.ink3, fontSize = 14.sp)
            BasicTextField(
                value, onChange, enabled = enabled, maxLines = 4,
                textStyle = TextStyle(color = W.ink, fontSize = 14.sp, fontFamily = MaterialTheme.typography.bodyMedium.fontFamily),
                cursorBrush = SolidColor(W.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) { onSend(); true } else false
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        val active = enabled && value.isNotBlank()
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(if (active) W.brand else W.line)
                .clickable(enabled = active, onClickLabel = "Enviar", onClick = onSend).pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center,
        ) { Icon(DinexNavigationIcons.Send, "Enviar", tint = if (active) Color.White else W.ink3, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun ContextLine(label: String, value: String, tint: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = W.ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
