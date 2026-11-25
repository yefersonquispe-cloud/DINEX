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
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    var deleteGoal by remember { mutableStateOf(false) }
    var unlink by remember { mutableStateOf(false) }

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
    if (deleteGoal) YupayDialog("¿Eliminar tu meta?", "Se borrará solo la meta. Las monedas de tu alcancía no cambian.", "Eliminar", { deleteGoal = false }) {
        model.setYupay(settings.copy(goal = null)); deleteGoal = false
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
                        val goalPanel: @Composable () -> Unit = {
                            YupayGoalPanel(settings.goal, email.orEmpty(), onSave = { model.setYupay(settings.copy(goal = it)) }, onDelete = { deleteGoal = true })
                        }
                        when {
                            !settings.linked -> { YupayIntro { model.setYupay(settings.copy(linked = true)) }; goalPanel() }
                            access != null -> YupayDenied(access!!, onRetry = { access = null; refresh++ }, onUnlink = { unlink = true })
                            else -> {
                                YupayHero(live, settings.goal, now, liveError != null, wide)
                                if (liveError != null && live == null) YupayNotice(liveError.orEmpty()) { refresh++ }
                                val device = live?.device
                                val controls: @Composable () -> Unit = {
                                    if (accepted) YupayControls(device, sending, commandResult) { command ->
                                        if (command == YupayCommand.OFF || command == YupayCommand.RESET) confirm = command else send(command)
                                    }
                                }
                                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        goalPanel(); YupayCoins(device); controls()
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        YupayToday(history, historyError); YupayDays(history); YupayLineChart(history); YupayTimeline(history, historyError)
                                    }
                                } else {
                                    goalPanel(); YupayToday(history, historyError); YupayCoins(device); YupayDays(history); YupayLineChart(history)
                                    YupayTimeline(history, historyError); controls()
                                }
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
            Text("Tu alcancía inteligente", color = Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            IconButton(onClick = onRefresh) { Icon(DinexNavigationIcons.Refresh, t("Actualizar"), tint = GreenDark, modifier = Modifier.size(20.dp)) }
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

@Composable
private fun YupayHero(live: YupayLive?, goal: YupayGoal?, now: Long, failing: Boolean, wide: Boolean) {
    val device = live?.device
    val fraction = yupayPercent(device?.totalCents ?: 0, goal)
    val missing = goal?.let { (it.targetCents - (device?.totalCents ?: 0)).coerceAtLeast(0) }
    YupayHeroBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AHORRADO EN TU ALCANCÍA", color = YLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(device?.let { yupayMoney((rememberCountUp(it.totalCents.toDouble())).toLong()) } ?: "S/ —", color = Color.White,
                    fontSize = if (wide) 54.sp else 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, maxLines = 1)
                Text(device?.let { countLabel(it.coins, "moneda", "monedas") } ?: "Esperando a tu alcancía…", color = Color.White.copy(alpha = .8f), fontSize = 15.sp)
                if (goal != null && device != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(if (missing == 0L) t("¡Cumpliste «{0}»!", goal.name) else t("Te faltan {0} para «{1}»", yupayMoney(missing ?: 0), goal.name),
                        color = YLime, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(contentAlignment = Alignment.Center) {
                YupayJar(fraction, if (wide) Modifier.size(150.dp, 180.dp) else Modifier.size(104.dp, 128.dp))
                if (goal != null && device != null) Text("${(fraction * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Black, fontSize = if (wide) 26.sp else 20.sp, modifier = Modifier.padding(top = 18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            live?.receivedAt?.let { YupayChip(t("Dato de {0}", yupayAgo(now - it).lowercase())) }
            device?.on?.let { YupayChip(if (it) "Contando · cajón trabado" else "Cajón libre") }
            device?.rssi?.let { YupayChip(t("Wi-Fi {0}", t(if (it >= -60) "buena" else if (it >= -75) "regular" else "baja"))) }
        }
        if (live != null && (live.connection != "online" || failing)) {
            Spacer(Modifier.height(12.dp))
            Text("La alcancía no está conectada ahora. Estas son las últimas cifras que envió.", color = Color(0xFFFFD79A), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun YupayChip(text: String) = Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 6.dp))

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

// ---------------------------------------------------------------- Meta única

@Composable
private fun YupayGoalPanel(goal: YupayGoal?, accountKey: String, onSave: (YupayGoal) -> Unit, onDelete: () -> Unit) {
    var editing by remember(accountKey) { mutableStateOf(false) }
    var name by remember(accountKey, goal) { mutableStateOf(goal?.name.orEmpty()) }
    var amount by remember(accountKey, goal) { mutableStateOf(goal?.targetCents?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" }.orEmpty()) }
    val cents = yupayInputCents(amount)
    YupayPanel("Mi meta de ahorro", DinexNavigationIcons.Target) {
        if (goal != null && !editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(goal.name, color = Night, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(yupayMoney(goal.targetCents.toLong()), color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                TextButton(onClick = { editing = true }) { Text("Editar", color = GreenDark, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDelete) { Text("Eliminar", color = Red, fontWeight = FontWeight.Bold) }
            }
        } else {
            if (goal == null) Text("¿Para qué estás ahorrando? Tu alcancía se llenará hacia esta meta.", color = Gray, fontSize = 14.sp)
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nombre de la meta") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp))
            OutlinedTextField(amount, { amount = it.filter { c -> c in '0'..'9' || c == '.' || c == ',' }.take(10) }, label = { Text("¿Cuánto quieres juntar? (S/)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onSave(YupayGoal(name.trim(), cents ?: return@Button)); editing = false }, enabled = name.isNotBlank() && cents != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White), modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(if (goal == null) "Crear meta" else "Guardar", fontWeight = FontWeight.Bold)
                }
                if (goal != null) OutlinedButton(onClick = { editing = false; name = goal.name }, modifier = Modifier.height(48.dp)) { Text("Cancelar", color = GreenDark) }
            }
        }
    }
}

// ---------------------------------------------------------------- Monedas

private fun coinText(cents: Int) = if (cents >= 100) "${cents / 100}" else ".${cents.toString().padStart(2, '0')}"

@Composable
private fun YupayCoin(cents: Int, size: Dp) {
    val edge = if (cents >= 200) YSilver else if (cents >= 50) YSilver else YGold
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

@Composable
private fun YupayCoins(device: YupayDevice?) = YupayPanel("Monedas por valor", DinexNavigationIcons.Wallet) {
    yupayCoins.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { cents ->
                val count = device?.counts?.get(cents)
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(CardRaised).padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    YupayCoin(cents, 46.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(count?.let { "× $it" } ?: "—", color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text(count?.let { yupayMoney(it.toLong() * cents) } ?: "—", color = Gray, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
    if (device != null && device.totalCents > 0) {
        val tones = listOf(Color(0xFFE0B64A), Color(0xFFC98F3A), Color(0xFF9BB4A4), Color(0xFF5FA37C), Color(0xFF2E8B57), Color(0xFF115B38))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
            yupayCoins.forEachIndexed { i, cents ->
                val part = (device.counts[cents] ?: 0).toLong() * cents
                if (part > 0) Box(Modifier.weight(part.toFloat()).fillMaxHeight().background(tones[i]))
            }
        }
    }
}

// ---------------------------------------------------------------- Hoy, gráfico e historial

@Composable
private fun YupayToday(history: YupayHistory?, error: String?) = YupayPanel("Hoy", DinexNavigationIcons.Sparkle, history?.let { yupayMoney(it.todayCents()) }) {
    when {
        history == null -> Text(error ?: "Esperando datos de la alcancía…", color = Gray)
        history.today.isEmpty() -> Text("Hoy todavía no entraron monedas.", color = Gray)
        else -> history.today.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                YupayCoin(item.coinCents, 32.dp)
                Spacer(Modifier.width(11.dp))
                Text(countLabel(item.quantity, "moneda", "monedas"), color = NightSoft, modifier = Modifier.weight(1f))
                Text("+ ${yupayMoney(item.sumCents)}", color = GreenDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Barras de los últimos 14 días; los días sin monedas se muestran vacíos. */
@Composable
private fun YupayDays(history: YupayHistory?) {
    val days = history?.days.orEmpty()
    if (days.isEmpty()) return
    val totals = days.associate { it.day to it.sumCents }
    val today = currentDayIndex()
    val slots = (13 downTo 0).map { back ->
        val date = civilDateFromEpochDays(today - back)
        date.day to (totals["${date.year}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"] ?: 0L)
    }
    val high = slots.maxOf { it.second }.coerceAtLeast(1)
    YupayPanel("Últimos 14 días", DinexNavigationIcons.Analysis, yupayMoney(slots.sumOf { it.second })) {
        Row(Modifier.fillMaxWidth().height(128.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            slots.forEachIndexed { i, (day, cents) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height((6 + 92f * cents / high).dp).clip(RoundedCornerShape(7.dp))
                        .background(if (cents == 0L) CardRaised else if (i == 13) Lime else Green))
                    Spacer(Modifier.height(5.dp))
                    Text("$day", color = if (i == 13) Night else Gray, fontSize = 10.sp, fontWeight = if (i == 13) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun YupayLineChart(history: YupayHistory?) {
    val recent = history?.recent ?: return
    if (recent.size < 2) return
    val count = recent.size
    val segments = yupayLineSegments(recent)
    val high = recent.maxOf { it.totalCents }.coerceAtLeast(1)
    val line = GreenDark; val grid = Border; val fill = Lime
    YupayPanel("Cómo va creciendo", DinexNavigationIcons.Analysis, yupayMoney(recent.first().totalCents)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            for (i in 0..3) drawLine(grid, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1f)
            fun at(i: Int, cents: Long) = Offset(6.dp.toPx() + (size.width - 12.dp.toPx()) * i / (count - 1), size.height - 6.dp.toPx() - (size.height - 14.dp.toPx()) * cents / high)
            var start = 0
            segments.forEach { segment ->
                val points = segment.mapIndexed { i, item -> at(start + i, item.totalCents) }
                start += segment.size
                if (points.size > 1) {
                    val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
                    drawPath(Path().apply { addPath(path); lineTo(points.last().x, size.height); lineTo(points.first().x, size.height); close() },
                        Brush.verticalGradient(listOf(fill.copy(alpha = .28f), Color.Transparent)))
                    drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
                points.forEach { drawCircle(line, 3.dp.toPx(), it) }
            }
        }
        if (segments.size > 1) Text("Cada corte es una vez que el conteo volvió a empezar.", color = Gray, fontSize = 12.sp)
    }
}

@Composable
private fun YupayTimeline(history: YupayHistory?, error: String?) = YupayPanel("Últimas monedas", DinexNavigationIcons.History) {
    when {
        history == null -> Text(error ?: "Esperando datos de la alcancía…", color = Gray)
        history.recent.isEmpty() -> Text("Todavía no se registran monedas.", color = Gray)
        else -> history.recent.take(10).forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = Border)
            Row(verticalAlignment = Alignment.CenterVertically) {
                YupayCoin(item.coinCents, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("+ ${yupayMoney(item.coinCents.toLong())}", color = Night, fontWeight = FontWeight.Bold)
                    Text(item.hour, color = Gray, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(yupayMoney(item.totalCents), color = GreenDark, fontWeight = FontWeight.Bold)
                    Text("en el cajón", color = Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Control del cajón

@Composable
private fun YupayControls(device: YupayDevice?, sending: YupayCommand?, result: String?, onCommand: (YupayCommand) -> Unit) = YupayPanel("Control del cajón", DinexNavigationIcons.Settings) {
    Text("Estas órdenes mueven tu alcancía de verdad.", color = Gray, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        YupayAction("Contar y trabar", DinexNavigationIcons.Shield, YupayCommand.ON, sending, device?.on == true, Modifier.weight(1f), onCommand)
        YupayAction("Liberar cajón", DinexNavigationIcons.ArrowUp, YupayCommand.OFF, sending, device?.on == false, Modifier.weight(1f), onCommand)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        YupayAction("Pedir estado", DinexNavigationIcons.Refresh, YupayCommand.STATUS, sending, false, Modifier.weight(1f), onCommand)
        YupayAction("Poner en cero", DinexNavigationIcons.Close, YupayCommand.RESET, sending, false, Modifier.weight(1f), onCommand)
    }
    if (sending != null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green, trackColor = Mint)
    result?.let { Text(it, color = NightSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun YupayAction(label: String, icon: ImageVector, command: YupayCommand, sending: YupayCommand?, active: Boolean, modifier: Modifier, onCommand: (YupayCommand) -> Unit) {
    val danger = command == YupayCommand.RESET
    val tint = if (danger) Red else if (active) Color.White else GreenDark
    Surface(onClick = { onCommand(command) }, enabled = sending == null, modifier = modifier.height(64.dp), shape = RoundedCornerShape(18.dp),
        color = if (active) Green else if (danger) Red.copy(alpha = .08f) else CardRaised, border = BorderStroke(1.dp, if (danger) Red.copy(alpha = .35f) else Border)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text(label, color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp)
        }
    }
}
