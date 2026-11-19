package com.example.dinex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_mascot_ai
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════════════════
// GUÍA DE BIENVENIDA
// Se abre al crear la cuenta y desde Perfil → Ver guía. Es un recorrido corto:
// cada paso tiene una demostración que se puede tocar para entender la función.
// ═══════════════════════════════════════════════════════════════════════════
internal enum class GuideAction { Automation, Market, Assistant }

private class GuideCard(
    val tag: String,
    val title: String,
    val body: String,
    val icon: ImageVector,
    val accent: Color,
    val where: String,
    val points: List<String>,
    val action: Pair<String, GuideAction>? = null,
    val demo: @Composable () -> Unit,
)

@Composable
internal fun WelcomeGuideDialog(name: String, onDismiss: () -> Unit, onAction: (GuideAction) -> Unit) {
    var legalTab by remember { mutableStateOf<Int?>(null) }
    val firstName = name.trim().substringBefore(' ')
    val web = LocalWebLayout.current
    val cards = remember(firstName, web) { guideCards(firstName, web) { legalTab = it } }
    val pager = rememberPagerState(pageCount = { cards.size })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == cards.lastIndex
    val accent by animateColorAsState(cards[page].accent, tween(350), label = "color de la guía")

    Dialog(onDismissRequest = onDismiss, properties = fullScreenDialogProperties()) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 560.dp
            Surface(
                modifier = if (compact) Modifier.fillMaxSize() else Modifier.widthIn(max = 500.dp).heightIn(max = 800.dp).fillMaxHeight(.94f),
                shape = RoundedCornerShape(if (compact) 0.dp else 28.dp),
                color = AppBg,
            ) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${page + 1} de ${cards.size}", color = Gray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (!last) TextButton(onClick = onDismiss) { Text("Saltar", color = Gray, fontWeight = FontWeight.SemiBold) }
                        else Spacer(Modifier.height(48.dp))
                    }
                    // Progreso continuo: avanza junto con el dedo al deslizar.
                    Box(Modifier.padding(horizontal = 22.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Border)) {
                        val progress = ((page + pager.currentPageOffsetFraction + 1) / cards.size).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(accent))
                    }
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                        val offset = (page - index) + pager.currentPageOffsetFraction
                        GuidePage(cards[index], offset, onAction)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AnimatedVisibility(page > 0, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                            OutlinedIconButton(
                                onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                                modifier = Modifier.size(54.dp),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, Border),
                            ) { Icon(DinexNavigationIcons.Back, "Atrás", tint = Night, modifier = Modifier.size(20.dp)) }
                        }
                        val interaction = remember { MutableInteractionSource() }
                        Button(
                            onClick = { if (last) onDismiss() else scope.launch { pager.animateScrollToPage(page + 1) } },
                            interactionSource = interaction,
                            modifier = Modifier.weight(1f).height(54.dp).dinexPress(interaction),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                        ) {
                            Text(if (last) "Empezar a usar Dinex" else if (page == 0) "Comenzar" else "Siguiente", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
    legalTab?.let { LegalDialog(it) { legalTab = null } }
}

@Composable
private fun GuidePage(card: GuideCard, offset: Float, onAction: (GuideAction) -> Unit) {
    val distance = abs(offset).coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Escenario con la demostración: se mueve más lento que el texto al deslizar.
        Box(
            Modifier.fillMaxWidth().heightIn(min = 230.dp)
                .graphicsLayer {
                    translationX = offset * size.width * .22f
                    val scale = 1f - distance * .08f
                    scaleX = scale; scaleY = scale; alpha = 1f - distance * .5f
                }
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(card.accent.copy(alpha = .20f), card.accent.copy(alpha = .05f))))
                .border(1.dp, card.accent.copy(alpha = .18f), RoundedCornerShape(26.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) { card.demo() }
        Column(
            Modifier.graphicsLayer { translationX = offset * size.width * -.12f; alpha = 1f - distance },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(card.icon, null, tint = readableAccent(card.accent), modifier = Modifier.size(18.dp))
                Text(card.tag.uppercase(), color = readableAccent(card.accent), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
            Text(card.title, color = Night, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 33.sp, letterSpacing = (-.5).sp)
            Text(card.body, color = NightSoft, fontSize = 15.sp, lineHeight = 22.sp)
            card.points.forEach { point ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(DinexNavigationIcons.Check, null, tint = readableAccent(card.accent), modifier = Modifier.padding(top = 2.dp).size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(point, color = Night, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            if (card.where.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(DinexNavigationIcons.MapPin, null, tint = Gray, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(card.where, color = Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            card.action?.let { (label, action) ->
                OutlinedButton(
                    onClick = { onAction(action) },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, card.accent.copy(alpha = .6f)),
                ) { Text(label, color = readableAccent(card.accent), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

private fun guideCards(firstName: String, web: Boolean, openLegal: (Int) -> Unit): List<GuideCard> = listOf(
    GuideCard(
        "Bienvenida", if (firstName.isBlank()) "Te damos la bienvenida a Dinex" else "Hola, $firstName. Bienvenido a Dinex",
        "En menos de un minuto verás cómo ordenar tu dinero, ahorrar para tus metas y entender tus gastos.",
        DinexNavigationIcons.Sparkle, Color(0xFF2B8456), "",
        emptyList(),
    ) { MascotDemo() },
    GuideCard(
        "Registrar", "Anota cada movimiento en segundos",
        "Elige gasto o ingreso, escribe el monto y listo. Funciona con Yape, Plin, efectivo, tarjeta o transferencia.",
        DinexNavigationIcons.Add, Color(0xFF176B43), if (web) "Botón Nuevo movimiento" else "Botón + de la barra inferior",
        listOf("Importa o exporta tus movimientos en Excel o CSV."),
    ) { RegisterDemo() },
    GuideCard(
        "Registro rápido", if (web) "Escanea tus comprobantes" else "Escanea boletas o usa tu voz",
        if (web) "Sube la foto de una boleta y Dinex lee el total por ti. Siempre revisas antes de guardar."
        else "Toma una foto de la boleta o di \"registra 20 soles en comida\". También puede anotar tus avisos de Yape y Plin si le das permiso.",
        DinexNavigationIcons.ScanReceipt, Color(0xFF326D91), if (web) "Nuevo movimiento → Escanear comprobante" else "Inicio → Escanear o Voz",
        if (web) emptyList() else listOf("Solo detecta avisos de pago; no lee tus chats.", "Puedes apagarlo cuando quieras."),
        action = if (web) null else "Activar registro automático" to GuideAction.Automation,
    ) { VoiceDemo() },
    GuideCard(
        "Metas y racha", "Ahorra con un propósito",
        "Crea metas como un viaje o un celular y separa dinero de tu saldo. Cada día que ahorras sube tu racha y ganas medallas.",
        DinexNavigationIcons.Target, Color(0xFFA66822), if (web) "Menú → Metas" else "Inicio → Metas",
        listOf("Un recordatorio diario te ayuda a mantener la racha."),
    ) { GoalDemo() },
    GuideCard(
        "Presupuesto", "Pon un límite y Dinex te avisa",
        "Elige cuánto quieres gastar al día y por categoría. Si te pasas, te avisamos antes de guardar el gasto.",
        DinexNavigationIcons.Plan, Color(0xFFB84754), if (web) "Menú → Presupuesto" else "Barra inferior → Plan",
        listOf("También guarda recordatorios de pagos como luz, agua o internet."),
    ) { LimitDemo() },
    GuideCard(
        "Análisis", "Mira a dónde se va tu dinero",
        "Revisa gráficos por categoría y por mes, y un mapa con los lugares donde más gastas. Además, consulta el dólar y otras monedas.",
        DinexNavigationIcons.Analysis, Color(0xFFD9480F), if (web) "Menú → Análisis" else "Barra inferior → Análisis",
        emptyList(),
        action = "Abrir el mercado" to GuideAction.Market,
    ) { HeatmapDemo() },
    GuideCard(
        "Dinex IA", "Pregunta lo que quieras",
        "Dinex IA responde con tus propios números y te da consejos prácticos. Tienes $DAILY_AI_QUERIES consultas al día.",
        DinexNavigationIcons.Tips, Color(0xFF438A53), "",
        listOf("Es una orientación: la decisión siempre es tuya."),
        action = "Probar Dinex IA" to GuideAction.Assistant,
    ) { ChatDemo() },
    GuideCard(
        "Privacidad", "Tus datos son tuyos",
        "Solo tú puedes ver tu información. Cada permiso es opcional y puedes eliminar tu cuenta cuando quieras.",
        DinexNavigationIcons.Shield, Color(0xFF115B38), "",
        emptyList(),
    ) { PrivacyDemo(openLegal) },
)

// ── Demostraciones ────────────────────────────────────────────────────────────
@Composable
private fun MascotDemo() {
    val still = LocalDinexReducedMotion.current
    val bounce = rememberInfiniteTransition(label = "mascota")
    val y by bounce.animateFloat(0f, if (still) 0f else -10f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "salto")
    var waves by remember { mutableIntStateOf(0) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(Res.drawable.dinex_mascot_ai), "Mascota de Dinex",
            Modifier.size(132.dp).offset(y = y.dp).clickable(onClickLabel = "Saludar") { waves++ },
            contentScale = ContentScale.Fit,
        )
        Text(
            when (waves % 3) { 0 -> "¡Tócame!"; 1 -> "¡Hola! Vamos a ahorrar juntos."; else -> "Decide mejor. Ahorra más." },
            color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        )
    }
}

@Composable
private fun RegisterDemo() {
    var income by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf("Yape") }
    val tone by animateColorAsState(if (income) Green else Red, label = "tipo")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!income, { income = false }, { Text("Gasto") })
            FilterChip(income, { income = true }, { Text("Ingreso") })
        }
        Surface(shape = RoundedCornerShape(20.dp), color = Card, border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(tone.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(if (income) DinexNavigationIcons.ArrowUp else DinexNavigationIcons.ArrowDown, null, tint = tone, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (income) "Pago de clases" else "Almuerzo", color = Night, fontWeight = FontWeight.Bold)
                    Text("${if (income) "Ingreso" else "Comida"} · $method", color = Gray, fontSize = 12.sp)
                }
                Text((if (income) "+ " else "- ") + "S/ ${if (income) "120.00" else "18.50"}", color = tone, fontWeight = FontWeight.Black)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Yape", "Plin", "Efectivo").forEach { option ->
                AssistChip(onClick = { method = option }, label = { Text(option, fontSize = 11.sp, fontWeight = if (method == option) FontWeight.Bold else FontWeight.Normal) })
            }
        }
    }
}

@Composable
private fun VoiceDemo() {
    val phrase = "Registra 20 soles en comida"
    var listening by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        shown = 0
        while (shown < phrase.length) { delay(45); shown++ }
        delay(500)
        listening = false
    }
    val still = LocalDinexReducedMotion.current
    val pulse = rememberInfiniteTransition(label = "voz")
    val scale by pulse.animateFloat(1f, if (listening && !still) 1.18f else 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulso")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(64.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(ElectricBlue).clickable(onClickLabel = "Probar la voz") { listening = true },
            contentAlignment = Alignment.Center,
        ) { Icon(DinexNavigationIcons.Microphone, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Text(
            if (shown == 0 && !listening) "Toca el micrófono" else "\"${phrase.take(shown)}\"",
            color = Night, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        AnimatedVisibility(shown == phrase.length && !listening, enter = fadeIn() + slideInVertically { it / 2 }) {
            Surface(shape = RoundedCornerShape(14.dp), color = Mint) {
                Text("Gasto guardado: Comida · S/ 20.00", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun GoalDemo() {
    var saved by remember { mutableFloatStateOf(150f) }
    val target = 500f
    val progress by animateFloatAsState(saved / target, label = "meta")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Viaje a Cusco", color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("S/ ${saved.toInt()} de S/ ${target.toInt()}", color = Gray, fontSize = 12.sp)
            }
            Surface(shape = RoundedCornerShape(12.dp), color = Orange.copy(alpha = .18f)) {
                Text("Racha ${(saved / 50).toInt()} días", color = readableAccent(Orange), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Border)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(Brush.horizontalGradient(listOf(Orange, Green))))
        }
        Slider(saved, { saved = it }, valueRange = 0f..target, steps = 9)
        Text(if (saved >= target) "¡Meta cumplida!" else "Mueve la barra para ahorrar", color = GreenDark, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun LimitDemo() {
    var limit by remember { mutableFloatStateOf(45f) }
    val spent = 52f
    val over = spent > limit
    val tone by animateColorAsState(if (over) Red else Green, label = "limite")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Límite diario: S/ ${limit.toInt()}", color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Border)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((spent / limit).coerceAtMost(1f)).clip(CircleShape).background(tone))
        }
        Text("Hoy gastaste S/ ${spent.toInt()}", color = Gray, fontSize = 12.sp)
        Slider(limit, { limit = it }, valueRange = 20f..120f)
        Surface(shape = RoundedCornerShape(14.dp), color = tone.copy(alpha = .12f), modifier = Modifier.fillMaxWidth()) {
            Text(
                if (over) "Te pasaste por S/ ${(spent - limit).toInt()}. Dinex te avisaría ahora." else "Vas bien: te quedan S/ ${(limit - spent).toInt()} para hoy.",
                color = tone, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun HeatmapDemo() {
    val spots = remember { mutableStateListOf(Offset(.3f, .4f), Offset(.62f, .55f), Offset(.48f, .3f), Offset(.66f, .62f)) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE9EEE7))
                .pointerInput(Unit) { detectTapGestures { tap -> spots.add(Offset(tap.x / size.width, tap.y / size.height)) } },
        ) {
            // Calles de fondo para que parezca un mapa.
            val street = Color.White
            for (i in 1..4) {
                drawLine(street, Offset(0f, size.height * i / 5f), Offset(size.width, size.height * i / 5f - 18f), 7f)
                drawLine(street, Offset(size.width * i / 5f, 0f), Offset(size.width * i / 5f + 22f, size.height), 7f)
            }
            spots.forEach { spot ->
                val center = Offset(spot.x * size.width, spot.y * size.height)
                drawCircle(Brush.radialGradient(listOf(Color(0xCCE03131), Color(0x66F59F00), Color.Transparent), center, 64f), 64f, center)
            }
        }
        Text("Toca el mapa para agregar un gasto (${spots.size} lugares)", color = Gray, fontSize = 12.sp)
    }
}

@Composable
private fun ChatDemo() {
    var asked by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), color = Green,
            modifier = Modifier.align(Alignment.End).clickable(onClickLabel = "Preguntar") { asked = true },
        ) { Text("¿En qué gasté más esta semana?", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
        if (!asked) Text("Toca la pregunta", color = Gray, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
        AnimatedVisibility(asked, enter = fadeIn() + slideInVertically { it / 2 }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Image(painterResource(Res.drawable.dinex_mascot_ai), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp), color = Card) {
                    Text("En Comida: S/ 96. Si llevas almuerzo 2 días, ahorras unos S/ 30", color = Night, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun PrivacyDemo(openLegal: (Int) -> Unit) {
    val permissions = listOf(
        Triple(DinexNavigationIcons.Camera, "Cámara", "Escanear boletas"),
        Triple(DinexNavigationIcons.Microphone, "Micrófono", "Registrar con la voz"),
        Triple(DinexNavigationIcons.MapPin, "Ubicación", "Mapa de calor"),
        Triple(DinexNavigationIcons.Bell, "Notificaciones", "Alertas y Yape/Plin"),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        permissions.forEach { (icon, title, use) ->
            var allowed by remember { mutableStateOf(true) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = GreenDark, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Night, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(use, color = Gray, fontSize = 11.sp)
                }
                Switch(allowed, { allowed = it }, modifier = Modifier.height(30.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            LegalLink("Ver términos") { openLegal(0) }
            LegalLink("Ver privacidad") { openLegal(1) }
        }
    }
}
