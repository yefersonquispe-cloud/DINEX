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
                        Text(t("{0} de {1}", page + 1, cards.size), color = Gray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (!last) TextButton(onClick = onDismiss) { Text(t("Saltar"), color = Gray, fontWeight = FontWeight.SemiBold) }
                        else Spacer(Modifier.height(48.dp))
                    }
                    // Progreso continuo: avanza junto con el dedo al deslizar.
                    Box(Modifier.padding(horizontal = 22.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Border)) {
                        val progress = ((page + pager.currentPageOffsetFraction + 1) / cards.size).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(accent))
                    }
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                        val offset = (page - index) + pager.currentPageOffsetFraction
                        CompositionLocalProvider(LocalGuideActive provides (page == index)) {
                            GuidePage(cards[index], offset, onAction)
                        }
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
                            Text(if (last) t("Empezar a usar Dinex") else if (page == 0) t("Comenzar") else t("Siguiente"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // En pantallas altas el escenario ocupa todo el espacio libre, así no quedan huecos.
        // En pantallas bajas la página se desplaza y el escenario tiene una altura fija.
        val tall = maxHeight >= 560.dp
        Column(
            Modifier.fillMaxSize()
                .then(if (tall) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            GuideStage(
                card,
                Modifier.fillMaxWidth()
                    .then(if (tall) Modifier.weight(1f).heightIn(min = 220.dp) else Modifier.height(250.dp))
                    .graphicsLayer {
                        translationX = offset * size.width * .22f
                        val scale = 1f - distance * .08f
                        scaleX = scale; scaleY = scale; alpha = 1f - distance * .5f
                    },
            )
            GuideText(card, onAction, Modifier.graphicsLayer { translationX = offset * size.width * -.12f; alpha = 1f - distance })
        }
    }
}

// Indica si la página está a la vista, para que las demostraciones arranquen solas solo ahí.
private val LocalGuideActive = compositionLocalOf { true }

/** Avance de 0 a 1 para que cada elemento entre en orden al llegar a la página. */
@Composable
private fun rememberEntrance(order: Int): Float {
    val active = LocalGuideActive.current
    val still = LocalDinexReducedMotion.current
    val progress = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(active, still) {
        if (still) progress.snapTo(1f)
        else if (active) { delay(90L * order); progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) }
    }
    return progress.value
}

private fun Modifier.entrance(progress: Float) = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 28.dp.toPx()
}

@Composable
private fun GuideStage(card: GuideCard, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    val still = LocalDinexReducedMotion.current
    val drift = rememberInfiniteTransition(label = "fondo de la guía")
    val angle by drift.animateFloat(0f, if (still) 0f else 6.2832f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "giro")
    val breath by drift.animateFloat(1f, if (still) 1f else 1.08f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "respiro")
    val pop = rememberEntrance(0)
    Box(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(card.accent.copy(alpha = .16f), card.accent.copy(alpha = .05f))))
            .border(1.dp, card.accent.copy(alpha = .16f), shape),
        contentAlignment = Alignment.Center,
    ) {
        // Círculos suaves que flotan despacio para dar vida al fondo sin distraer.
        Canvas(Modifier.matchParentSize()) {
            val dx = kotlin.math.cos(angle) * size.minDimension * .05f
            val dy = kotlin.math.sin(angle) * size.minDimension * .05f
            drawCircle(card.accent.copy(alpha = .07f), size.minDimension * .62f, Offset(size.width * .92f + dx, size.height * .06f + dy))
            drawCircle(card.accent.copy(alpha = .06f), size.minDimension * .42f, Offset(size.width * .04f - dx, size.height * .98f - dy))
            drawCircle(Color.White.copy(alpha = .35f), size.minDimension * .34f * breath, Offset(size.width * .5f, size.height * .5f))
            for (i in 0 until 6) {
                val a = angle + i * 1.047f
                val r = size.minDimension * .40f
                drawCircle(card.accent.copy(alpha = .22f), 3.dp.toPx(), Offset(size.width * .5f + kotlin.math.cos(a) * r, size.height * .5f + kotlin.math.sin(a) * r * .8f))
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(20.dp).graphicsLayer {
                val s = .88f + .12f * pop
                scaleX = s; scaleY = s; alpha = pop
            },
            contentAlignment = Alignment.Center,
        ) { card.demo() }
    }
}

@Composable
private fun GuideText(card: GuideCard, onAction: (GuideAction) -> Unit, modifier: Modifier) {
    val tone = readableAccent(card.accent)
    val step = List(5) { rememberEntrance(it + 1) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CircleShape, color = card.accent.copy(alpha = .12f), modifier = Modifier.entrance(step[0])) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(card.icon, null, tint = tone, modifier = Modifier.size(14.dp))
                Text(t(card.tag).uppercase(), color = tone, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            }
        }
        Text(t(card.title), color = Night, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-.5).sp, modifier = Modifier.entrance(step[1]))
        Text(t(card.body), color = NightSoft, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.entrance(step[2]))
        // Datos extra y ubicación de la función, agrupados en una tarjeta.
        if (card.points.isNotEmpty() || card.where.isNotBlank()) {
            Surface(shape = RoundedCornerShape(18.dp), color = Card, border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth().padding(top = 4.dp).entrance(step[3])) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    card.points.forEach { point ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(card.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                                Icon(DinexNavigationIcons.Check, null, tint = tone, modifier = Modifier.size(12.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(t(point), color = Night, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                    if (card.where.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Border.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                            Icon(DinexNavigationIcons.MapPin, null, tint = Gray, modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(t(card.where), color = Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        card.action?.let { (label, action) ->
            OutlinedButton(
                onClick = { onAction(action) },
                shape = CircleShape,
                border = BorderStroke(1.dp, card.accent.copy(alpha = .6f)),
                modifier = Modifier.fillMaxWidth().height(46.dp).entrance(step[4]),
            ) { Text(t(label), color = tone, fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun guideCards(firstName: String, web: Boolean, openLegal: (Int) -> Unit): List<GuideCard> = listOf(
    GuideCard(
        "Bienvenida", if (firstName.isBlank()) "Te damos la bienvenida a Dinex" else t("Hola, {0}. Bienvenido a Dinex", firstName),
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
    val tilt by bounce.animateFloat(-4f, if (still) -4f else 4f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "balanceo")
    var waves by remember { mutableIntStateOf(0) }
    val wave = remember { Animatable(0f) }
    LaunchedEffect(waves) {
        if (waves > 0 && !still) { wave.snapTo(0f); wave.animateTo(1f, tween(500)) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(Res.drawable.dinex_mascot_ai), "Mascota de Dinex",
            Modifier.size(176.dp).offset(y = y.dp)
                .graphicsLayer {
                    rotationZ = (if (still) 0f else tilt) + kotlin.math.sin(wave.value * 18.85f) * 10f * (1f - wave.value)
                    val pop = 1f + kotlin.math.sin(wave.value * 3.1416f) * .12f
                    scaleX = pop; scaleY = pop
                }
                .clickable(onClickLabel = "Saludar") { waves++ },
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(10.dp))
        Surface(shape = CircleShape, color = Card, border = BorderStroke(1.dp, Border)) {
            Text(
                when (waves % 3) { 0 -> "¡Tócame!"; 1 -> "¡Hola! Vamos a ahorrar juntos."; else -> "Tu dinero, con propósito." },
                color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun RegisterDemo() {
    var income by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf("Yape") }
    var touched by remember { mutableStateOf(false) }
    val active = LocalGuideActive.current
    val still = LocalDinexReducedMotion.current
    // Se muestra sola alternando gasto, ingreso y método hasta que la persona la toque.
    LaunchedEffect(active, touched) {
        val methods = listOf("Yape", "Plin", "Efectivo")
        while (active && !touched && !still) {
            delay(1900)
            if (method == methods.last()) income = !income
            method = methods[(methods.indexOf(method) + 1) % methods.size]
        }
    }
    val tone by animateColorAsState(if (income) Green else Red, label = "tipo")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Selector tipo segmento, con los colores de Dinex.
        Row(Modifier.clip(CircleShape).background(Card).border(1.dp, Border, CircleShape).padding(4.dp)) {
            listOf(false to "Gasto", true to "Ingreso").forEach { (value, label) ->
                val selected = income == value
                Box(
                    Modifier.clip(CircleShape).background(if (selected) Green else Color.Transparent)
                        .clickable { touched = true; income = value }.padding(horizontal = 20.dp, vertical = 8.dp),
                ) { Text(t(label), color = if (selected) Color.White else Gray, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = Card, border = BorderStroke(1.dp, Border), shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(tone.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(if (income) DinexNavigationIcons.ArrowUp else DinexNavigationIcons.ArrowDown, null, tint = tone, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (income) t("Pago de clases") else t("Almuerzo"), color = Night, fontWeight = FontWeight.Bold)
                    Text("${if (income) t("Ingreso") else t("Comida")} · $method", color = Gray, fontSize = 12.sp)
                }
                Text((if (income) "+ " else "- ") + "S/ ${if (income) "120.00" else "18.50"}", color = tone, fontWeight = FontWeight.Black)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Yape", "Plin", "Efectivo").forEach { option ->
                val selected = method == option
                Surface(
                    onClick = { touched = true; method = option }, shape = CircleShape,
                    color = if (selected) Mint else Card,
                    border = BorderStroke(1.dp, if (selected) Green.copy(alpha = .5f) else Border),
                ) {
                    Text(
                        option, color = if (selected) GreenDark else Gray, fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
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
    val active = LocalGuideActive.current
    // Al llegar a la página, el micrófono se activa solo una vez para mostrar el ejemplo.
    LaunchedEffect(active) {
        if (active && shown == 0 && !listening) { delay(700); listening = true }
    }
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
                Text(t("Gasto guardado: Comida · S/ 20.00"), color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun GoalDemo() {
    var saved by remember { mutableFloatStateOf(150f) }
    val target = 500f
    val active = LocalGuideActive.current
    val still = LocalDinexReducedMotion.current
    var played by remember { mutableStateOf(false) }
    // La meta se llena sola la primera vez que se ve la página.
    LaunchedEffect(active) {
        if (active && !played && !still) {
            played = true
            animate(0f, 300f, animationSpec = tween(1400, easing = FastOutSlowInEasing)) { value, _ -> saved = value }
        }
    }
    val progress by animateFloatAsState(saved / target, label = "meta")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Viaje a Cusco"), color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("S/ ${saved.toInt()} de S/ ${target.toInt()}", color = Gray, fontSize = 12.sp)
            }
            Surface(shape = RoundedCornerShape(12.dp), color = Orange.copy(alpha = .18f)) {
                Text("Racha ${(saved / 50).toInt()} días", color = readableAccent(Orange), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Border)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(Brush.horizontalGradient(listOf(Orange, Green))))
        }
        Slider(saved, { saved = it }, valueRange = 0f..target, steps = 9, colors = guideSliderColors())
        Text(if (saved >= target) t("¡Meta cumplida!") else t("Mueve la barra para ahorrar"), color = GreenDark, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun LimitDemo() {
    var limit by remember { mutableFloatStateOf(45f) }
    val spent = 52f
    val active = LocalGuideActive.current
    val still = LocalDinexReducedMotion.current
    var played by remember { mutableStateOf(false) }
    // El límite baja solo hasta pasarse, para que se vea el aviso en rojo.
    LaunchedEffect(active) {
        if (active && !played && !still) {
            played = true
            animate(90f, 45f, animationSpec = tween(1600, delayMillis = 300, easing = FastOutSlowInEasing)) { value, _ -> limit = value }
        }
    }
    val over = spent > limit
    val tone by animateColorAsState(if (over) Red else Green, label = "limite")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Límite diario: S/ ${limit.toInt()}", color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Border)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((spent / limit).coerceAtMost(1f)).clip(CircleShape).background(tone))
        }
        Text("Hoy gastaste S/ ${spent.toInt()}", color = Gray, fontSize = 12.sp)
        Slider(limit, { limit = it }, valueRange = 20f..120f, colors = guideSliderColors())
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
    val start = remember { listOf(Offset(.3f, .4f), Offset(.62f, .55f), Offset(.48f, .3f), Offset(.66f, .62f)) }
    val active = LocalGuideActive.current
    val still = LocalDinexReducedMotion.current
    val spots = remember { mutableStateListOf<Offset>().apply { if (still) addAll(start) } }
    // Los puntos de gasto aparecen uno por uno y el calor late suave.
    LaunchedEffect(active) {
        if (active && spots.isEmpty()) start.forEach { delay(350); spots.add(it) }
    }
    val heat by rememberInfiniteTransition(label = "calor").animateFloat(
        1f, if (still) 1f else 1.15f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "latido",
    )
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
                val radius = 64f * heat
                drawCircle(Brush.radialGradient(listOf(Color(0xCCE03131), Color(0x66F59F00), Color.Transparent), center, radius), radius, center)
            }
        }
        Text("Toca el mapa para agregar un gasto (${spots.size} lugares)", color = Gray, fontSize = 12.sp)
    }
}

@Composable
private fun ChatDemo() {
    var asked by remember { mutableStateOf(false) }
    val active = LocalGuideActive.current
    // La pregunta se envía sola al llegar a la página.
    LaunchedEffect(active) {
        if (active && !asked) { delay(1100); asked = true }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), color = Green,
            modifier = Modifier.align(Alignment.End).clickable(onClickLabel = "Preguntar") { asked = true },
        ) { Text(t("¿En qué gasté más esta semana?"), color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
        if (!asked) Text(t("Toca la pregunta"), color = Gray, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
        AnimatedVisibility(asked, enter = fadeIn() + slideInVertically { it / 2 }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Image(painterResource(Res.drawable.dinex_mascot_ai), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp), color = Card) {
                    Text(t("En Comida: S/ 96. Si llevas almuerzo 2 días, ahorras unos S/ 30"), color = Night, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
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
        permissions.forEachIndexed { index, (icon, title, use) ->
            var allowed by remember { mutableStateOf(true) }
            Row(Modifier.entrance(rememberEntrance(index + 1)), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun guideSliderColors() = SliderDefaults.colors(
    thumbColor = Green, activeTrackColor = Green, inactiveTrackColor = Border,
    activeTickColor = Color.White.copy(alpha = .6f), inactiveTickColor = Green.copy(alpha = .35f),
)
