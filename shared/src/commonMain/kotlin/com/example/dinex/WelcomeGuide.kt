package com.example.dinex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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

// ═══════════════════════════════════════════════════════════════════════════
// GUÍA DE BIENVENIDA
// Se abre al crear la cuenta y desde Perfil → Ver guía. Cada cartilla tiene una
// pequeña demostración que se puede tocar para entender la función.
// ═══════════════════════════════════════════════════════════════════════════
internal enum class GuideAction { Automation, Market, Tips, Assistant }

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
    val firstName = name.trim().substringBefore(' ').ifBlank { "" }
    val cards = remember(firstName) { guideCards(firstName) { legalTab = it } }
    val pager = rememberPagerState(pageCount = { cards.size })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == cards.lastIndex

    Dialog(onDismissRequest = onDismiss, properties = fullScreenDialogProperties()) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)).statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 560.dp
            Surface(
                modifier = Modifier
                    .then(if (compact) Modifier.fillMaxSize() else Modifier.widthIn(max = 520.dp).heightIn(max = 820.dp).fillMaxHeight(.94f))
                    .padding(if (compact) 0.dp else 8.dp),
                shape = RoundedCornerShape(if (compact) 0.dp else 32.dp),
                color = AppBg,
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Barra de progreso por cartillas + saltar.
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            cards.indices.forEach { index ->
                                val fill by animateFloatAsState(if (index <= page) 1f else 0f, tween(300), label = "progreso")
                                Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(Border)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(cards[page].accent))
                                }
                            }
                        }
                        TextButton(onClick = onDismiss) { Text(if (last) "Cerrar" else "Saltar", color = Gray, fontWeight = FontWeight.SemiBold) }
                    }
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index -> GuidePage(cards[index], index, cards.size, onAction) }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (page > 0) OutlinedButton(
                            onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(RadiusMd),
                            border = BorderStroke(1.dp, Border),
                        ) { Icon(DinexNavigationIcons.Back, "Atrás", tint = Night, modifier = Modifier.size(20.dp)) }
                        Button(
                            onClick = { if (last) onDismiss() else scope.launch { pager.animateScrollToPage(page + 1) } },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(RadiusMd),
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                        ) {
                            Text(if (last) "Empezar a usar Dinex" else if (page == 0) "Comenzar recorrido" else "Siguiente", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
    legalTab?.let { LegalDialog(it) { legalTab = null } }
}

@Composable
private fun GuidePage(card: GuideCard, index: Int, total: Int, onAction: (GuideAction) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Escenario con la demostración interactiva.
        Box(
            Modifier.fillMaxWidth().heightIn(min = 210.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(card.accent.copy(alpha = .22f), card.accent.copy(alpha = .06f))))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) { card.demo() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = card.accent.copy(alpha = .16f)) {
                Icon(card.icon, null, tint = readableAccent(card.accent), modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Column {
                Text(card.tag.uppercase(), color = readableAccent(card.accent), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text("${index + 1} de $total", color = Gray, fontSize = 11.sp)
            }
        }
        Text(card.title, color = Night, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 31.sp)
        Text(card.body, color = NightSoft, fontSize = 14.sp, lineHeight = 21.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            card.points.forEach { point ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 2.dp).size(18.dp).clip(CircleShape).background(card.accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                        Icon(DinexNavigationIcons.Check, null, tint = readableAccent(card.accent), modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(point, color = Night, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        if (card.where.isNotBlank()) Surface(shape = RoundedCornerShape(RadiusSm), color = Card, border = BorderStroke(1.dp, Border)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(DinexNavigationIcons.MapPin, null, tint = Gray, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(card.where, color = Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        card.action?.let { (label, action) ->
            OutlinedButton(
                onClick = { onAction(action) },
                shape = RoundedCornerShape(RadiusMd),
                border = BorderStroke(1.dp, card.accent.copy(alpha = .6f)),
            ) { Text(label, color = readableAccent(card.accent), fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun guideCards(firstName: String, openLegal: (Int) -> Unit): List<GuideCard> = listOf(
    GuideCard(
        "Bienvenida", if (firstName.isBlank()) "¡Hola! Te presento Dinex" else "¡Hola, $firstName! Te presento Dinex",
        "Soy tu compañero para ordenar tu dinero. En un minuto te enseño todo lo que puedes hacer. Desliza o toca Siguiente.",
        DinexNavigationIcons.Sparkle, Color(0xFF2B8456), "",
        listOf("Registra lo que gastas y lo que ganas.", "Ahorra para metas reales con tu racha diaria.", "Entiende a dónde se va tu dinero."),
    ) { MascotDemo() },
    GuideCard(
        "Registrar", "Anota un gasto en segundos",
        "Toca el botón + y elige si es gasto o ingreso. Pon el monto, la categoría y cómo pagaste: Yape, Plin, efectivo, tarjeta o transferencia.",
        DinexNavigationIcons.Add, Color(0xFF176B43), "Botón + en el centro de la barra de abajo",
        listOf("Cada gasto guarda el lugar para tu mapa de calor.", "Puedes editar o borrar cualquier movimiento.", "Importa o exporta tus movimientos en Excel o CSV."),
    ) { RegisterDemo() },
    GuideCard(
        "Escanear y voz", "Escanea boletas o dilo con tu voz",
        "Con la cámara, Dinex lee el total de una boleta. Con el micrófono, solo di lo que gastaste y se registra solo.",
        DinexNavigationIcons.Microphone, Color(0xFF326D91), "Inicio → Escanear o Voz",
        listOf("Ejemplo: \"registra 20 soles en comida\".", "También crea recordatorios: \"recuérdame pagar la luz el 15\".", "Tú revisas antes de guardar."),
    ) { VoiceDemo() },
    GuideCard(
        "Registro automático", "Dinex anota tus Yape y Plin por ti",
        "Si das permiso, Dinex lee solo los avisos de pago de Yape, Plin y tu banco, y los convierte en movimientos. También puedes decir \"Oye Dinex\".",
        DinexNavigationIcons.Bell, Color(0xFF6959A5), "Perfil → Voz y notificaciones",
        listOf("Solo detecta montos de pagos; no lee tus chats.", "Todo se procesa dentro de tu teléfono.", "Puedes apagarlo cuando quieras."),
        action = "Activar registro automático" to GuideAction.Automation,
    ) { NotificationDemo() },
    GuideCard(
        "Metas y racha", "Ahorra con propósito",
        "Crea metas como un viaje o un celular y separa dinero de tu saldo. Cada día que ahorras sube tu racha y ganas medallas.",
        DinexNavigationIcons.Wallet, Color(0xFFA66822), "Inicio → Metas",
        listOf("Dinex IA crea una ilustración para cada meta.", "Te celebra cuando llegas a tu objetivo.", "Un recordatorio diario te ayuda a no romper la racha."),
    ) { GoalDemo() },
    GuideCard(
        "Tu plan", "Pon un límite y Dinex te avisa",
        "Elige cuánto quieres gastar al día y cuánto por categoría. Si te pasas, Dinex te avisa antes de guardar el gasto.",
        DinexNavigationIcons.Plan, Color(0xFFB84754), "Barra de abajo → Plan",
        listOf("Presupuestos por categoría sugeridos o a tu medida.", "Recordatorios de pagos: luz, agua, internet.", "Alertas en tu celular cuando superas el límite."),
    ) { LimitDemo() },
    GuideCard(
        "Análisis", "Mira a dónde se va tu dinero",
        "Gráficos por categoría y por mes, tu salud financiera y un mapa de calor con los lugares donde más gastas.",
        DinexNavigationIcons.Analysis, Color(0xFFD9480F), "Barra de abajo → Análisis",
        listOf("El mapa se llena solo con la ubicación de cada gasto.", "Los puntos más intensos son donde más gastas.", "Descarga un reporte cuando lo necesites."),
    ) { HeatmapDemo() },
    GuideCard(
        "Mercado en vivo", "Dólar, euro y cripto al día",
        "Consulta el tipo de cambio y el precio de las criptomonedas en soles, con gráficos y un convertidor rápido.",
        DinexNavigationIcons.Market, Color(0xFF0B7285), "Icono de tendencia arriba en Inicio",
        listOf("Más de 10 monedas y criptomonedas.", "Convierte montos al instante.", "Precios referenciales, no para operar."),
        action = "Abrir el mercado" to GuideAction.Market,
    ) { MarketDemo() },
    GuideCard(
        "Consejos y Dinex IA", "Consejos hechos para ti",
        "Dinex revisa tus gastos y te da consejos personalizados. Pregúntale a Dinex IA lo que quieras sobre tu dinero.",
        DinexNavigationIcons.Tips, Color(0xFF438A53), "Foco de ideas arriba · Mascota abajo a la derecha",
        listOf("Ejemplo: \"¿en qué gasté más esta semana?\".", "Funciona con Gemini Nano dentro del teléfono si es compatible.", "Es una guía; tú decides."),
        action = "Probar Dinex IA" to GuideAction.Assistant,
    ) { ChatDemo() },
    GuideCard(
        "Privacidad", "Tus datos, protegidos",
        "Tu cuenta está en Firebase de Google y tus datos se sincronizan entre tu celular y la web. Solo tú puedes verlos.",
        DinexNavigationIcons.Shield, Color(0xFF115B38), "Perfil → Términos y privacidad",
        listOf(
            "Cada permiso (cámara, micrófono, ubicación, notificaciones) es opcional.",
            "Tu contraseña nunca se guarda en el teléfono.",
            "Puedes eliminar tu cuenta y tus datos cuando quieras (Ley N.° 29733).",
        ),
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
                    Icon(if (income) DinexNavigationIcons.ArrowDown else DinexNavigationIcons.ArrowUp, null, tint = tone, modifier = Modifier.size(20.dp))
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
    val pulse = rememberInfiniteTransition(label = "voz")
    val scale by pulse.animateFloat(1f, if (listening) 1.18f else 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulso")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size((64 * scale).dp).clip(CircleShape).background(ElectricBlue).clickable(onClickLabel = "Probar la voz") { listening = true },
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
private fun NotificationDemo() {
    var captured by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = Card, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF6C1D82)), contentAlignment = Alignment.Center) {
                    Text("Y", color = Color.White, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Yape", color = Night, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Recibiste S/ 50.00 de Ana", color = Gray, fontSize = 12.sp)
                }
            }
        }
        Button(
            onClick = { captured = !captured },
            colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Color.White),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text(if (captured) "Ver otra vez" else "Ver cómo se registra") }
        AnimatedVisibility(captured, enter = fadeIn() + slideInVertically { it / 2 }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Mint, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DinexNavigationIcons.Check, null, tint = GreenDark, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ingreso de S/ 50.00 guardado · Yape", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
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
private fun MarketDemo() {
    val quotes = listOf(Triple("Dólar", "S/ 3.74", true), Triple("Euro", "S/ 4.31", false), Triple("Bitcoin", "S/ 412,900", true))
    var amount by remember { mutableIntStateOf(100) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        quotes.forEach { (label, price, up) ->
            Surface(shape = RoundedCornerShape(14.dp), color = Card, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Night, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(price, color = Night, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Icon(if (up) DinexNavigationIcons.ArrowUp else DinexNavigationIcons.ArrowDown, null, tint = if (up) Green else Red, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 100, 500).forEach { value -> FilterChip(amount == value, { amount = value }, { Text("$$value") }) }
            Text("= S/ ${format2(amount * 3.74)}", color = GreenDark, fontWeight = FontWeight.Black)
        }
        Text("Precios de ejemplo", color = Gray, fontSize = 10.sp)
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
