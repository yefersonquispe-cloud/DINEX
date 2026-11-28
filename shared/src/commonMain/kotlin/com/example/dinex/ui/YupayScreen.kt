package com.example.dinex

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════
// DINEX YUPAY: la alcancía física (ESP32 → Node-RED).
// Una sola interfaz de ahorro: lo ahorrado es lo que cuentan los sensores y no se
// mezcla con el saldo, los ingresos, los gastos ni las demás metas de DINEX.
// ═══════════════════════════════════════════════════════════════════════════

private val YForest = Color(0xFF0E1E16)
private val YForest2 = Color(0xFF17472F)
private val YLime = Color(0xFFC6F06A)
private val YGold = Color(0xFFE0B64A)
private val YSilver = Color(0xFFC3CCD3)

/** "12.50" o "12,50" en céntimos; null si no es un monto válido. */
internal fun yupayInputCents(input: String): Int? {
    val parts = input.trim().replace(',', '.').split('.')
    if (parts.size !in 1..2 || parts.any { it.isEmpty() || !it.all { c -> c in '0'..'9' } } || (parts.getOrNull(1)?.length ?: 0) > 2) return null
    val whole = parts[0].takeIf { it.length <= 7 }?.toLongOrNull() ?: return null
    val fraction = parts.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0
    return (whole * 100 + fraction).takeIf { it in 1..Int.MAX_VALUE }?.toInt()
}

/** "hace 5 s", "hace 3 min", "Hace 2 h" o "Hace 4 d". */
internal fun yupayAgo(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> t("hace {0} s", seconds)
        seconds < 3_600 -> t("hace {0} min", seconds / 60)
        seconds < 86_400 -> t("Hace {0} h", seconds / 3_600)
        else -> t("Hace {0} d", seconds / 86_400)
    }
}

@Composable
internal fun YupayScreen(model: DinexViewModel, onClose: () -> Unit) {
    val account by model.state.collectAsState()
    val email = account.email
    val settings = account.data.yupay
    val api = remember(model) { YupayApi(model::yupayToken) }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var visible by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> visible = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Todo lo que llega de la alcancía pertenece a la cuenta abierta: al cambiar de cuenta empieza vacío.
    var live by remember(email) { mutableStateOf<YupayLive?>(null) }
    var history by remember(email) { mutableStateOf<YupayHistory?>(null) }
    var liveError by remember(email) { mutableStateOf<String?>(null) }
    var historyError by remember(email) { mutableStateOf<String?>(null) }
    var access by remember(email) { mutableStateOf<YupayAccessException?>(null) }
    var now by remember { mutableLongStateOf(epochMillis()) }
    var refresh by remember { mutableIntStateOf(0) }
    var sending by remember(email) { mutableStateOf<YupayCommand?>(null) }
    var commandResult by remember(email) { mutableStateOf<String?>(null) }
    var confirm by remember(email) { mutableStateOf<YupayCommand?>(null) }
    var unlink by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    fun current() = model.state.value.email == email && model.state.value.data.yupay.linked
    fun denied(error: YupayAccessException) {
        if (!current()) return
        live = null; history = null; liveError = null; historyError = null; access = error
    }
    LaunchedEffect(settings.linked, email) {
        if (!settings.linked) { live = null; history = null; liveError = null; historyError = null; access = null }
    }
    LaunchedEffect(visible) { while (visible) { now = epochMillis(); delay(5_000) } }
    LaunchedEffect(settings.linked, email, visible, refresh) {
        if (!settings.linked || !visible || email == null) return@LaunchedEffect
        var wait = 3_000L
        while (true) {
            try {
                val result = api.live()
                if (current()) { live = result; liveError = null; access = null; now = epochMillis() }
                wait = 3_000L
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: YupayAccessException) { denied(error); return@LaunchedEffect }
            catch (error: Throwable) {
                if (current()) liveError = error.message
                wait = (wait * 2).coerceAtMost(24_000L)
            }
            delay(wait)
        }
    }
    // El historial se pide solo cuando el servidor ya aceptó a esta cuenta.
    val accepted = live != null && access == null
    LaunchedEffect(accepted, settings.linked, email, visible, refresh) {
        if (!accepted || !settings.linked || !visible || email == null) return@LaunchedEffect
        var wait = 8_000L
        while (true) {
            try {
                val result = api.history()
                if (current()) { history = result; historyError = null }
                wait = 8_000L
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: YupayAccessException) { denied(error); return@LaunchedEffect }
            catch (error: Throwable) {
                if (current()) historyError = error.message
                wait = (wait * 2).coerceAtMost(64_000L)
            }
            delay(wait)
        }
    }
    fun send(command: YupayCommand) {
        if (sending != null || !accepted || !settings.linked || email == null) return
        sending = command; commandResult = null
        scope.launch {
            try {
                api.command(command)
                if (current()) commandResult = "Orden enviada a la alcancía."
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: YupayAccessException) { denied(error) }
            catch (error: Throwable) { if (current()) commandResult = error.message }
            finally { sending = null; refresh++ }
        }
    }

    confirm?.let { command ->
        val off = command == YupayCommand.OFF
        YupayDialog(
            if (off) "¿Liberar el cajón?" else "¿Poner el conteo en cero?",
            if (off) "El cajón quedará abierto y dejará de contar hasta que lo actives otra vez." else "El total del cajón volverá a cero. No se puede deshacer.",
            if (off) "Liberar" else "Poner en cero", onDismiss = { confirm = null },
        ) { confirm = null; send(command) }
    }
    if (unlink) YupayDialog("¿Desvincular la alcancía?", "Dejarás de ver sus monedas en esta cuenta. Tu meta se conserva.", "Desvincular", { unlink = false }) {
        model.setYupay(settings.copy(linked = false)); unlink = false
    }

    Surface(Modifier.fillMaxSize(), color = AppBg) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            YupayHeader(settings.linked, live, liveError, access, onClose) { access = null; refresh++ }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 860.dp
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 28.dp else 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 1180.dp).fillMaxWidth().padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val goals = account.data.savingGoals()
                        val selected = goals.firstOrNull { it.id == settings.goalId }
                        val picker: @Composable () -> Unit = { YupayGoalPicker(goals, settings.goalId, onSelect = model::setYupayGoal) }
                        when {
                            !settings.linked -> { YupayIntro { model.setYupay(settings.copy(linked = true)) }; picker() }
                            access != null -> YupayDenied(access!!, onRetry = { access = null; refresh++ }, onUnlink = { unlink = true })
                            else -> {
                                if (liveError != null && live == null) YupayNotice(liveError.orEmpty()) { refresh++ }
                                YupayTabs(tab) { tab = it }
                                if (tab == 0) {
                                    YupayDashboard(live, history, selected, now, liveError != null, sending, commandResult) { command ->
                                        if (command == YupayCommand.OFF || command == YupayCommand.RESET) confirm = command else send(command)
                                    }
                                    picker()
                                } else YupayHistoryTab(history, historyError, wide)
                                TextButton(onClick = { unlink = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                    Text("Desvincular alcancía", color = Gray, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Encabezado y avisos

@Composable
private fun YupayHeader(linked: Boolean, live: YupayLive?, error: String?, access: YupayAccessException?, onClose: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(DinexNavigationIcons.Back, t("Volver"), tint = GreenDark, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text("Dinex YUPAY", color = Night, fontWeight = FontWeight.Black, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Alcancía inteligente conectada a tus metas", color = Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (linked && access == null) {
            val online = live?.connection == "online" && error == null
            val label = when {
                error != null -> "Sin respuesta"
                live == null -> "Conectando…"
                online -> "Conectada"
                else -> "Desconectada"
            }
            val tint = if (online) Color(0xFF2FAE66) else if (live == null && error == null) Gray else Orange
            Row(Modifier.clip(CircleShape).background(tint.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
                Spacer(Modifier.width(7.dp))
                Text(label, color = Night, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onRefresh, border = BorderStroke(1.dp, Border), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Icon(DinexNavigationIcons.Refresh, null, tint = Night, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Actualizar", color = Night, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun YupayDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss, containerColor = Card,
    title = { Text(title, color = Night, fontWeight = FontWeight.Black) },
    text = { Text(body, color = NightSoft) },
    confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)) { Text(action, fontWeight = FontWeight.Bold) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = GreenDark) } },
)

@Composable
private fun YupayPanel(title: String? = null, icon: ImageVector? = null, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).border(1.dp, Border, RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Mint), contentAlignment = Alignment.Center) { Icon(it, null, tint = GreenDark, modifier = Modifier.size(17.dp)) }
                Spacer(Modifier.width(11.dp))
            }
            Text(title, color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
            trailing?.let { Text(it, color = GreenDark, fontWeight = FontWeight.Black, fontSize = 16.sp) }
        }
        content()
    }
}

@Composable
private fun YupayNotice(message: String, onRetry: () -> Unit) = YupayPanel {
    Text(message, color = NightSoft)
    OutlinedButton(onClick = onRetry) { Text("Reintentar", color = GreenDark) }
}

@Composable
private fun YupayIntro(onLink: () -> Unit) {
    YupayHeroBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tu alcancía inteligente", color = YLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Cada moneda cuenta, de verdad.", color = Color.White, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black)
                Text("Los sensores de tu alcancía reconocen cada moneda y aquí ves cuánto llevas ahorrado.", color = Color.White.copy(alpha = .75f), fontSize = 14.sp, lineHeight = 20.sp)
            }
            YupayJar(0f, Modifier.size(110.dp, 132.dp))
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onLink, colors = ButtonDefaults.buttonColors(containerColor = YLime, contentColor = YForest), modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Vincular mi alcancía", fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text("Se abre solo con tu cuenta de DINEX.", color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
    }
}

@Composable
private fun YupayDenied(access: YupayAccessException, onRetry: () -> Unit, onUnlink: () -> Unit) = YupayPanel {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Orange.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Icon(DinexNavigationIcons.Shield, null, tint = Orange, modifier = Modifier.size(30.dp))
        }
        Text(access.message.orEmpty(), color = Night, fontWeight = FontWeight.Black, fontSize = 19.sp, textAlign = TextAlign.Center)
        Text(if (access.forbidden) "Entra con la cuenta dueña de la alcancía o pide que autoricen la tuya." else "Cierra sesión y entra otra vez con tu cuenta.", color = Gray, textAlign = TextAlign.Center)
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White)) { Text("Reintentar") }
        TextButton(onClick = onUnlink) { Text("Desvincular alcancía", color = Gray) }
    }
}

// ---------------------------------------------------------------- Total y alcancía

@Composable
private fun YupayHeroBox(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(YForest, YForest2))).drawBehind {
        drawCircle(Brush.radialGradient(listOf(YLime.copy(alpha = .22f), Color.Transparent), Offset(size.width * .92f, 0f), size.width * .5f), size.width * .5f, Offset(size.width * .92f, 0f))
    }.padding(22.dp),
    content = content,
)

/** Frasco que se llena según la meta, con una ola suave. */
@Composable
private fun YupayJar(fraction: Float, modifier: Modifier) {
    val reduced = LocalDinexReducedMotion.current
    val level by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(if (reduced) 0 else 1200), label = "llenado")
    val moving by rememberInfiniteTransition(label = "ola").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "fase")
    val phase = if (reduced) 0f else moving
    Canvas(modifier) {
        val lid = size.height * .13f
        val body = Rect(size.width * .04f, lid * .9f, size.width * .96f, size.height)
        val glass = Path().apply { addRoundRect(RoundRect(body, CornerRadius(size.width * .24f))) }
        drawPath(glass, Color.White.copy(alpha = .09f))
        if (level > 0f) clipPath(glass) {
            val top = body.bottom - body.height * (.06f + .9f * level)
            fun wave(shift: Float, amplitude: Float) = Path().apply {
                moveTo(body.left, body.bottom); lineTo(body.left, top)
                for (i in 0..24) {
                    val x = body.left + body.width * i / 24
                    lineTo(x, top + (sin((i / 24f + phase + shift) * 2 * PI) * amplitude).toFloat())
                }
                lineTo(body.right, body.bottom); close()
            }
            drawPath(wave(.4f, size.height * .022f), YLime.copy(alpha = .35f))
            drawPath(wave(0f, size.height * .018f), Brush.verticalGradient(listOf(Color(0xFF9BD64E), Color(0xFF2E8B57)), top, body.bottom))
        }
        drawPath(glass, Color.White.copy(alpha = .55f), style = Stroke(2.5.dp.toPx()))
        drawRoundRect(YLime, Offset(size.width * .16f, 0f), Size(size.width * .68f, lid), CornerRadius(lid * .45f))
        drawRoundRect(YForest, Offset(size.width * .38f, lid * .38f), Size(size.width * .24f, lid * .22f), CornerRadius(lid))
        drawLine(Color.White.copy(alpha = .35f), Offset(size.width * .17f, size.height * .36f), Offset(size.width * .17f, size.height * .66f), 4.dp.toPx(), StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- Monedas

/** 0.10, 0.20 y 0.50 son dorados; 1.00 plateado; 2.00 y 5.00 con el anillo plateado. */
private fun yupayCoinTone(cents: Int) = if (cents >= 100) YSilver else YGold

private fun coinText(cents: Int) = if (cents >= 100) "${cents / 100}" else ".${cents.toString().padStart(2, '0')}"

@Composable
private fun YupayCoin(cents: Int, size: Dp) {
    val edge = yupayCoinTone(cents)
    val middle = if (cents >= 200) YGold else edge
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            drawCircle(Brush.linearGradient(listOf(Color.White, edge, edge.copy(red = edge.red * .75f, green = edge.green * .75f, blue = edge.blue * .75f))), r)
            drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = .9f), middle)), r * .72f)
            drawCircle(Color.Black.copy(alpha = .14f), r * .72f, style = Stroke(1.dp.toPx()))
        }
        Text(coinText(cents), color = Color(0xFF3A3222), fontWeight = FontWeight.Black, fontSize = (size.value * .3f).sp)
    }
}

// ---------------------------------------------------------------- Hoy, gráfico e historial

/** (día del mes, céntimos) de los últimos 14 días, del más antiguo a hoy. */
private fun yupayLast14(history: YupayHistory?): List<Pair<Int, Long>> {
    val totals = history?.days.orEmpty().associate { it.day to it.sumCents }
    val today = currentDayIndex()
    return (13 downTo 0).map { back ->
        val date = civilDateFromEpochDays(today - back)
        date.day to (totals["${date.year}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"] ?: 0L)
    }
}

// ---------------------------------------------------------------- Panel principal (resumen, alcancía y cajón)

private val YInk = Color(0xFF0A120E)
private val YRule = Color.White.copy(alpha = .12f)
private val YDanger = Color(0xFFFF9B8A)

@Composable
private fun YupayDashboard(
    live: YupayLive?, history: YupayHistory?, goal: SavingsGoal?, now: Long, failing: Boolean,
    sending: YupayCommand?, result: String?, onCommand: (YupayCommand) -> Unit,
) {
    val device = live?.device
    val online = live?.connection == "online" && !failing
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(YForest, YForest2)))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(28.dp)).drawBehind {
            drawCircle(Brush.radialGradient(listOf(YLime.copy(alpha = .14f), Color.Transparent), Offset(size.width * .55f, size.height * .45f), size.minDimension * .6f),
                size.minDimension * .6f, Offset(size.width * .55f, size.height * .45f))
        },
    ) {
        val width = maxWidth
        when {
            width >= 1040.dp -> Row(Modifier.height(IntrinsicSize.Min)) {
                YupaySummary(live, device, history, goal, now, online, sending, result, false, Modifier.weight(1.6f).padding(28.dp), onCommand)
                Box(Modifier.width(1.dp).fillMaxHeight().background(YRule))
                YupayMachine(device, online, Modifier.weight(.75f).fillMaxHeight().padding(vertical = 24.dp))
                Box(Modifier.width(1.dp).fillMaxHeight().background(YRule))
                YupayDrawer(device, Modifier.weight(.9f).padding(28.dp))
            }
            width >= 640.dp -> Column {
                YupaySummary(live, device, history, goal, now, online, sending, result, false, Modifier.padding(24.dp), onCommand)
                Box(Modifier.fillMaxWidth().height(1.dp).background(YRule))
                Row(Modifier.height(IntrinsicSize.Min)) {
                    YupayMachine(device, online, Modifier.weight(1f).fillMaxHeight().padding(vertical = 22.dp))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(YRule))
                    YupayDrawer(device, Modifier.weight(1.2f).padding(24.dp))
                }
            }
            else -> Column {
                YupaySummary(live, device, history, goal, now, online, sending, result, true, Modifier.padding(20.dp), onCommand)
                Box(Modifier.fillMaxWidth().height(1.dp).background(YRule))
                YupayMachine(device, online, Modifier.fillMaxWidth().padding(vertical = 22.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(YRule))
                YupayDrawer(device, Modifier.padding(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YupaySummary(
    live: YupayLive?, device: YupayDevice?, history: YupayHistory?, goal: SavingsGoal?, now: Long, online: Boolean,
    sending: YupayCommand?, result: String?, compact: Boolean, modifier: Modifier, onCommand: (YupayCommand) -> Unit,
) = Column(modifier) {
    YupayWrap(compact) {
        Text("AHORRADO EN TU ALCANCÍA", color = YForest, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
            modifier = Modifier.clip(CircleShape).background(YLime).padding(horizontal = 12.dp, vertical = 6.dp))
        val tint = if (live == null) Color.White.copy(alpha = .6f) else if (online) Color(0xFF6BE39A) else Color(0xFFF2B35B)
        val label = when {
            live == null -> t("Conectando…")
            online -> live.receivedAt?.let { t("Conectada · dato {0}", yupayAgo(now - it).lowercase()) } ?: t("Conectada")
            else -> live.receivedAt?.let { t("Sin conexión · último dato {0}", yupayAgo(now - it).lowercase()) } ?: t("Sin conexión")
        }
        Row(Modifier.clip(CircleShape).border(1.dp, tint.copy(alpha = .45f), CircleShape).background(tint.copy(alpha = .1f)).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(7.dp))
            Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text("S/", color = Color.White.copy(alpha = .75f), fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = if (compact) 8.dp else 12.dp))
        Spacer(Modifier.width(6.dp))
        val shown = device?.let { yupayMoney(rememberCountUp(it.totalCents.toDouble()).toLong()).removePrefix("S/ ") } ?: "—"
        Text(shown, color = Color.White, fontSize = if (compact) 48.sp else 66.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, maxLines = 1)
    }
    Text(
        buildAnnotatedString {
            if (goal != null) {
                append(t("Destino actual: "))
                withStyle(SpanStyle(color = YLime, fontWeight = FontWeight.Bold)) { append(goal.name) }
                if (device != null && goal.target > 0) append(t(" · aporta el {0} % de tu meta", ((device.totalCents / 100.0) / goal.target * 100).roundToInt()))
            } else append(t("Elige abajo a qué meta va lo que entra en la alcancía."))
        },
        color = Color.White.copy(alpha = .8f), fontSize = 14.sp,
    )
    Spacer(Modifier.height(20.dp))
    val stats = listOf(
        "HOY" to (history?.let { "+${yupayMoney(it.todayCents())}" } ?: "—"),
        "14 DÍAS" to (history?.let { yupayMoney(yupayLast14(it).sumOf { day -> day.second }) } ?: "—"),
        "MONEDAS" to (device?.coins?.toString() ?: "—"),
        "CAJÓN" to when (device?.on) { true -> t("Trabado"); false -> t("Libre"); null -> "—" },
    )
    Column(Modifier.fillMaxWidth().drawBehind {
        drawLine(YRule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        drawLine(YRule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }) {
        stats.chunked(if (compact) 2 else 4).forEachIndexed { r, row ->
            if (r > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(YRule))
            Row(Modifier.height(IntrinsicSize.Min)) {
                row.forEachIndexed { i, (label, value) ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(YRule))
                    Column(Modifier.weight(1f).padding(start = if (i > 0) 14.dp else 0.dp, top = 14.dp, bottom = 14.dp, end = 8.dp)) {
                        Text(t(label), color = Color.White.copy(alpha = .55f), fontSize = 11.sp, letterSpacing = .6.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text(value, color = if (label == "HOY") YLime else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (goal != null) {
        val fraction = if (goal.target > 0) (goal.saved / goal.target).coerceIn(0.0, 1.0).toFloat() else 0f
        val reduced = LocalDinexReducedMotion.current
        val shownFraction by animateFloatAsState(fraction, tween(if (reduced) 0 else 900), label = "meta")
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Meta {0} · {1} de {2}", goal.name, money(goal.saved), money(goal.target)), color = Color.White.copy(alpha = .8f), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${(fraction * 100).roundToInt()} %", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f))) {
            Box(Modifier.fillMaxWidth(shownFraction).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF5FC48A), YLime))))
        }
        Spacer(Modifier.height(8.dp))
        val missing = (goal.target - goal.saved).coerceAtLeast(0.0)
        Text(if (missing <= 0.0) t("¡Meta cumplida!") else t("Te faltan {0}", money(missing)), color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
    }
    Spacer(Modifier.height(22.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YupayHeroButton("Contar y trabar", DinexNavigationIcons.Shield, device?.on == true, sending == null, modifier = Modifier.weight(1f)) { onCommand(YupayCommand.ON) }
            YupayHeroButton("Liberar cajón", DinexNavigationIcons.ArrowUp, device?.on == false, sending == null, modifier = Modifier.weight(1f)) { onCommand(YupayCommand.OFF) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YupayHeroButton("Pedir estado", DinexNavigationIcons.Refresh, false, sending == null, modifier = Modifier.weight(1f)) { onCommand(YupayCommand.STATUS) }
            YupayHeroButton("Vaciar (poner en cero)", DinexNavigationIcons.Close, false, sending == null, danger = true, modifier = Modifier.weight(1f)) { onCommand(YupayCommand.RESET) }
        }
    }
    if (sending != null) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = YLime, trackColor = Color.White.copy(alpha = .12f))
    }
    result?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
    }
    if (live != null && !online) {
        Spacer(Modifier.height(10.dp))
        Text("La alcancía no está conectada ahora. Estas son las últimas cifras que envió.", color = Color(0xFFFFD79A), fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/** En celular las etiquetas bajan de linea; en pantallas anchas van en una fila (el panel mide su alto por intrinsecos). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YupayWrap(wrap: Boolean, content: @Composable () -> Unit) {
    if (wrap) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun YupayHeroButton(label: String, icon: ImageVector, active: Boolean, enabled: Boolean, danger: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val content = if (active) YForest else if (danger) YDanger else Color.White
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), color = if (active) YLime else if (danger) YDanger.copy(alpha = .08f) else Color.Transparent,
        border = if (active) null else BorderStroke(1.dp, if (danger) YDanger.copy(alpha = .45f) else Color.White.copy(alpha = .28f)), modifier = modifier.heightIn(min = 48.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** La alcancía dibujada: pantalla con el total y una ventana con una pila por cada moneda. */
@Composable
private fun YupayMachine(device: YupayDevice?, online: Boolean, modifier: Modifier) {
    val counts = yupayCoins.map { device?.counts?.get(it) ?: 0 }
    val reduced = LocalDinexReducedMotion.current
    val grow by animateFloatAsState(if (device == null) 0f else 1f, tween(if (reduced) 0 else 900), label = "pilas")
    val led = if (device == null) Color.White.copy(alpha = .4f) else if (online) Color(0xFF6BE39A) else Color(0xFFF2B35B)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(200.dp, 290.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                drawOval(Color.Black.copy(alpha = .35f), Offset(w * .08f, h * .93f), Size(w * .84f, h * .06f))
                val body = Rect(w * .06f, h * .02f, w * .94f, h * .95f)
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2C3E35), Color(0xFF121C17)), body.top, body.bottom), body.topLeft, body.size, CornerRadius(w * .26f))
                drawRoundRect(Color.White.copy(alpha = .16f), body.topLeft, body.size, CornerRadius(w * .26f), style = Stroke(1.5.dp.toPx()))
                // ranura con luz
                drawRoundRect(Color.Black, Offset(w * .3f, h * .045f), Size(w * .4f, h * .032f), CornerRadius(h))
                drawRoundRect(YLime, Offset(w * .34f, h * .054f), Size(w * .32f, h * .013f), CornerRadius(h))
                // pantalla
                drawRoundRect(YInk, Offset(w * .17f, h * .105f), Size(w * .66f, h * .115f), CornerRadius(10.dp.toPx()))
                drawRoundRect(YLime.copy(alpha = .25f), Offset(w * .17f, h * .105f), Size(w * .66f, h * .115f), CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()))
                // ventana de vidrio
                val glass = Rect(w * .15f, h * .26f, w * .85f, h * .86f)
                drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color(0xFF0F1A15)), glass.top, glass.bottom), glass.topLeft, glass.size, CornerRadius(w * .13f))
                // pilas de monedas
                val high = counts.max().coerceAtLeast(1)
                val slot = glass.width * .86f / counts.size
                val coinW = slot * .78f
                val layer = 3.2.dp.toPx()
                val floor = glass.bottom - glass.height * .05f
                counts.forEachIndexed { i, count ->
                    val tone = yupayCoinTone(yupayCoins[i])
                    val dark = tone.copy(red = tone.red * .62f, green = tone.green * .62f, blue = tone.blue * .62f)
                    val stack = (glass.height * .8f * count / high * grow).coerceAtLeast(if (count > 0) layer else 0f)
                    val layers = (stack / layer).toInt()
                    val x = glass.left + glass.width * .07f + slot * i + (slot - coinW) / 2
                    for (k in 0 until layers) {
                        val y = floor - (k + 1) * layer
                        drawRoundRect(Brush.horizontalGradient(listOf(dark, tone, Color.White.copy(alpha = .9f), tone, dark), x, x + coinW), Offset(x, y), Size(coinW, layer * .82f), CornerRadius(layer))
                    }
                }
                // reflejo del vidrio
                drawLine(Color.White.copy(alpha = .22f), Offset(glass.left + glass.width * .08f, glass.top + glass.height * .1f),
                    Offset(glass.left + glass.width * .08f, glass.top + glass.height * .5f), 3.dp.toPx(), StrokeCap.Round)
                drawRoundRect(Color.White.copy(alpha = .2f), glass.topLeft, glass.size, CornerRadius(w * .13f), style = Stroke(1.2.dp.toPx()))
                // luz de estado
                drawCircle(led.copy(alpha = .3f), 8.dp.toPx(), Offset(w * .5f, h * .905f))
                drawCircle(led, 3.5.dp.toPx(), Offset(w * .5f, h * .905f))
            }
            Text(device?.let { yupayMoney(it.totalCents) } ?: "S/ —", color = YLime, fontSize = 16.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 39.dp))
        }
        Spacer(Modifier.height(12.dp))
        val wifi = device?.rssi?.let { t("Wi-Fi {0}", t(if (it >= -60) "buena" else if (it >= -75) "regular" else "baja")) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(led))
            Spacer(Modifier.width(6.dp))
            Text(listOfNotNull("ESP32", t("{0} sensores", yupayCoins.size), wifi).joinToString("   ·   "), color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
        }
    }
}

/** Composición del cajón: cuánto hay de cada moneda. */
@Composable
private fun YupayDrawer(device: YupayDevice?, modifier: Modifier) = Column(modifier) {
    val tones = listOf(Color(0xFFE0B64A), Color(0xFFC99A3A), Color(0xFFF0CF72), Color(0xFFB9C3CA), Color(0xFF8FCB6B), YLime)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("COMPOSICIÓN DEL CAJÓN", color = Color.White.copy(alpha = .6f), fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(device?.let { yupayMoney(it.totalCents) } ?: "—", color = Color.White.copy(alpha = .6f), fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = .1f))) {
        if (device != null && device.totalCents > 0) yupayCoins.forEachIndexed { i, cents ->
            val part = (device.counts[cents] ?: 0).toLong() * cents
            if (part > 0) Box(Modifier.weight(part.toFloat()).fillMaxHeight().background(tones[i]))
        }
    }
    Spacer(Modifier.height(8.dp))
    yupayCoins.forEachIndexed { i, cents ->
        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
        val count = device?.counts?.get(cents)
        Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            YupayCoin(cents, 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(yupayMoney(cents.toLong()), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(count?.let { "× " + countLabel(it, "moneda", "monedas") } ?: "—", color = Color.White.copy(alpha = .5f), fontSize = 11.sp)
            }
            Text(count?.let { yupayMoney(it.toLong() * cents) } ?: "—", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ---------------------------------------------------------------- Destino del ahorro (metas de Dinex)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YupayGoalPicker(goals: List<SavingsGoal>, selectedId: Int?, onSelect: (Int?) -> Unit) {
    val selected = goals.firstOrNull { it.id == selectedId }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).border(1.dp, Border, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Mint), contentAlignment = Alignment.Center) {
                    Icon(DinexNavigationIcons.Target, null, tint = GreenDark, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Destino del ahorro", color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("Elige a qué meta de Dinex se suma lo que entra en la alcancía", color = Gray, fontSize = 13.sp)
                }
            }
            if (selected != null) Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Mint.copy(alpha = .6f)).border(1.dp, Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Las monedas nuevas van a ", color = Gray, fontSize = 13.sp)
                Text(selected.name, color = GreenDark, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (goals.isEmpty()) {
            Text("Aún no tienes metas. Crea una en Metas y vuelve aquí para elegirla.", color = Night, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when { maxWidth >= 1000.dp -> 4; maxWidth >= 720.dp -> 3; maxWidth >= 460.dp -> 2; else -> 1 }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                goals.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { goal -> YupayGoalCard(goal, goal.id == selectedId, Modifier.weight(1f)) { onSelect(goal.id) } }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (selected != null) TextButton(onClick = { onSelect(null) }) { Text("No guardar en ninguna meta", color = Gray, fontSize = 13.sp) }
    }
}

@Composable
private fun YupayGoalCard(goal: SavingsGoal, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val percent = if (goal.target > 0) (goal.saved / goal.target).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).border(if (on) 2.dp else 1.dp, if (on) Green else Border, RoundedCornerShape(18.dp))
            .background(if (on) Mint.copy(alpha = .35f) else Card).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row {
            GoalAvatar(goal.name, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(goal.name, color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t("{0} de {1}", money(goal.saved), money(goal.target)), color = Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (on) Text("DESTINO", color = GreenDark, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp,
                modifier = Modifier.clip(CircleShape).background(Mint).padding(horizontal = 9.dp, vertical = 4.dp))
            else Box(Modifier.size(18.dp).clip(CircleShape).border(1.5.dp, Gray.copy(alpha = .6f), CircleShape))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Mint)) {
                Box(Modifier.fillMaxWidth(percent).fillMaxHeight().clip(CircleShape).background(Green))
            }
            Spacer(Modifier.width(10.dp))
            Text("${(percent * 100).roundToInt()} %", color = Night, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ---------------------------------------------------------------- Pestañas: Alcancía | Historial

@Composable
private fun YupayTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(CircleShape).background(CardRaised).border(1.dp, Border, CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Alcancía" to DinexNavigationIcons.Wallet, "Historial" to DinexNavigationIcons.History).forEachIndexed { i, (label, icon) ->
            val on = i == selected
            Row(
                Modifier.clip(CircleShape).background(if (on) YForest2 else Color.Transparent).clickable { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (on) YLime else Gray, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(t(label), color = if (on) Color.White else Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------- Historial

@Composable
private fun YupayHistoryTab(history: YupayHistory?, error: String?, wide: Boolean) {
    if (history == null) {
        YupayPanel { Text(error ?: t("Cargando el historial de tu alcancía…"), color = Gray) }
        return
    }
    val slots = yupayLast14(history)
    val total = slots.sumOf { it.second }
    val activeDays = slots.count { it.second > 0 }
    val best = slots.maxByOrNull { it.second }
    val stats: @Composable (Modifier) -> Unit = { m ->
        YupayStatCard(t("Últimos 14 días"), yupayMoney(total), countLabel(activeDays, "día con monedas", "días con monedas"), m)
    }
    val average: @Composable (Modifier) -> Unit = { m ->
        YupayStatCard(t("Promedio por día"), yupayMoney(if (activeDays > 0) total / activeDays else 0), t("contando solo los días con monedas"), m)
    }
    val bestDay: @Composable (Modifier) -> Unit = { m ->
        YupayStatCard(t("Mejor día"), if (best != null && best.second > 0) yupayMoney(best.second) else "—",
            if (best != null && best.second > 0) t("el día {0}", best.first) else t("aún sin monedas"), m)
    }
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        stats(Modifier.weight(1f)); average(Modifier.weight(1f)); bestDay(Modifier.weight(1f))
    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { average(Modifier.weight(1f)); bestDay(Modifier.weight(1f)) }
    }
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(1.15f)) { YupayActivity(slots) }
        Box(Modifier.weight(1f)) { YupayMovements(history.recent) }
    } else {
        YupayActivity(slots)
        YupayMovements(history.recent)
    }
}

@Composable
private fun YupayStatCard(label: String, value: String, note: String, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Border, RoundedCornerShape(20.dp)).padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(label.uppercase(), color = Gray, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    Text(value, color = Night, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
    Text(note, color = Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Barras de 14 días: hoy resaltado y el monto encima de los días con monedas. */
@Composable
private fun YupayActivity(slots: List<Pair<Int, Long>>) = YupayPanel("Actividad de 14 días", DinexNavigationIcons.Analysis) {
    val high = slots.maxOf { it.second }.coerceAtLeast(1)
    val reduced = LocalDinexReducedMotion.current
    val grow by animateFloatAsState(1f, tween(if (reduced) 0 else 700), label = "barras")
    Row(Modifier.fillMaxWidth().height(210.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        slots.forEachIndexed { i, (day, cents) ->
            val today = i == slots.lastIndex
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val fraction = if (cents > 0) (cents.toFloat() / high * grow).coerceIn(.04f, 1f) else 0f
                Spacer(Modifier.weight(1f - fraction + .0001f))
                if (cents > 0) Text(
                    if (cents >= 10_000) "${cents / 100}" else yupayMoney(cents).removePrefix("S/ "),
                    color = if (today) GreenDark else Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().weight(fraction.coerceAtLeast(.0001f)).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(
                            when {
                                cents == 0L -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                                today -> Brush.verticalGradient(listOf(Color(0xFF9BD64E), Green))
                                else -> Brush.verticalGradient(listOf(Green.copy(alpha = .85f), GreenDark))
                            },
                        ),
                )
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(if (cents == 0L) Border else Color.Transparent))
                Spacer(Modifier.height(6.dp))
                Text(if (today) t("Hoy") else "$day", color = if (today) Night else Gray, fontSize = 10.sp,
                    fontWeight = if (today) FontWeight.Black else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

/** Monedas recientes agrupadas por día, sin el total acumulado (que confundía). */
@Composable
private fun YupayMovements(recent: List<YupayRecent>) = YupayPanel("Monedas recientes", DinexNavigationIcons.History) {
    if (recent.isEmpty()) {
        Text("Todavía no se registran monedas.", color = Gray)
        return@YupayPanel
    }
    var all by remember { mutableStateOf(false) }
    val shown = if (all) recent else recent.take(12)
    shown.groupBy { it.hour.substringBefore(' ') }.forEach { (date, items) ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardRaised).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(date, color = NightSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(t("{0} · {1}", countLabel(items.sumOf { it.quantity }, "moneda", "monedas"), yupayMoney(items.sumOf { it.coinCents.toLong() * it.quantity })),
                color = Gray, fontSize = 12.sp)
        }
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                YupayCoin(item.coinCents, 30.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Moneda de {0}", yupayMoney(item.coinCents.toLong())) + if (item.quantity > 1) " ×${item.quantity}" else "",
                        color = Night, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.hour.substringAfter(' ').take(5), color = Gray, fontSize = 12.sp)
                }
                Text("+${yupayMoney(item.coinCents.toLong() * item.quantity)}", color = GreenDark, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
    }
    if (recent.size > 12) TextButton(onClick = { all = !all }) {
        Text(if (all) t("Ver menos") else t("Ver las {0} monedas", recent.size), color = GreenDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
