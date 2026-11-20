package com.example.dinex

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_mascot_ai
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.max

// ═══════════════════════════════════════════════════════════════════════════
// DINEX IA
// Floating assistant, chat screen and financial context sent to Gemini.
// ═══════════════════════════════════════════════════════════════════════════
@Composable
internal fun AssistantPage(assistant: GeminiAssistant, model: AssistantViewModel, data: FinancialData) {
    val chat by model.state.collectAsState()
    val messages = chat.messages
    val loading = chat.loading
    val remaining = chat.queriesLeft
    val movements = data.movements
    val streak = data.activeStreak()
    val savings = data.savings
    val chatListState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var reportTarget by remember { mutableStateOf<ChatMessage?>(null) }
    val reducedMotion = LocalDinexReducedMotion.current
    val nanoStatus = assistant.nanoStatus
    val nanoProgress = assistant.nanoProgress

    LaunchedEffect(model) { model.refreshQuota() }
    LaunchedEffect(assistant) { assistant.refreshNanoStatus() }

    LaunchedEffect(messages.size, loading, reducedMotion) {
        if (messages.size <= 1 && !loading) return@LaunchedEffect
        val lastItem = messages.lastIndex + (if (messages.size <= 1) 1 else 0) + (if (loading) 1 else 0)
        if (lastItem >= 0) {
            if (reducedMotion) chatListState.scrollToItem(lastItem) else chatListState.animateScrollToItem(lastItem)
        }
    }

    fun send(question: String) {
        if (loading) return
        model.send(question, assistant, data)
        if (question == draft) draft = ""
    }

    reportTarget?.let { target ->
        AiReportDialog(target, onDismiss = { reportTarget = null }) { reason, comment ->
            model.report(target, reason, comment)
            reportTarget = null
        }
    }
    chat.reportNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = model::clearReportNotice,
            text = { Text(notice, color = Night) },
            confirmButton = { TextButton(onClick = model::clearReportNotice) { Text(t("Entendido")) } },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 940.dp
        Row(Modifier.widthIn(max = 1180.dp).fillMaxSize().padding(if (wide) 24.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (wide) Column(Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                DinexCard(accent = Indigo, vibrant = true) {
                    Image(painterResource(Res.drawable.dinex_mascot_ai), null, Modifier.size(108.dp).align(Alignment.CenterHorizontally), contentScale = ContentScale.Fit)
                    Spacer(Modifier.height(14.dp))
                    Text(t("Un aliado para tus decisiones."), color = Night, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(t("Convierte tus movimientos en una conversación más clara sobre tu dinero."), color = Gray, fontSize = 14.sp, lineHeight = 21.sp)
                }
                DinexCard(accent = ElectricBlue) {
                    Text(t("TU CONTEXTO"), color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(14.dp))
                    val income = movements.filter { it.income }.sumOf { it.amount }
                    val expense = movements.filterNot { it.income }.sumOf { it.amount }
                    AssistantContextLine("Saldo registrado", money(income - expense), Night)
                    AssistantContextLine("Ahorros", money(savings), Lime)
                    AssistantContextLine("Racha", countLabel(streak, "día", "días"), Orange)
                    Spacer(Modifier.height(12.dp))
                    Text(t("Usa tu resumen de Dinex. No accede a tus bancos ni solicita claves."), color = Gray, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(17.dp)).background(CardRaised), contentAlignment = Alignment.Center) {
                        Icon(DinexNavigationIcons.Sparkle, null, tint = GreenDark, modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Dinex IA"), color = Night, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
                        val subtitle = when (nanoStatus) {
                            NanoStatus.AVAILABLE -> "Gemini Nano · funciona sin internet"
                            NanoStatus.DOWNLOADING -> "Descargando Gemini Nano…"
                            NanoStatus.DOWNLOADABLE -> "Gemini Nano disponible en tu teléfono"
                            NanoStatus.CHECKING -> "Buscando Gemini Nano…"
                            NanoStatus.UNAVAILABLE -> if (assistant.isConfigured) "Gemini en la nube · Nano no disponible"
                            else "Configura Gemini para conversar"
                        }
                        val ready = assistant.isConfigured || nanoStatus == NanoStatus.AVAILABLE ||
                            nanoStatus == NanoStatus.DOWNLOADABLE || nanoStatus == NanoStatus.DOWNLOADING
                        Text(subtitle, color = if (ready) GreenDark else Orange, fontSize = 12.sp)
                    }
                }
                if (nanoStatus == NanoStatus.UNAVAILABLE && assistant.nanoDetail != null) {
                    Text(
                        "${assistant.nanoDevice.ifBlank { t("Este dispositivo") }}: ${assistant.nanoDetail?.let { t(it) }.orEmpty()}",
                        color = Gray,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (nanoStatus == NanoStatus.DOWNLOADABLE || nanoStatus == NanoStatus.DOWNLOADING || chat.nanoError != null) {
                    Spacer(Modifier.height(10.dp))
                    NanoDownloadCard(
                        downloading = nanoStatus == NanoStatus.DOWNLOADING || chat.nanoBusy,
                        progress = nanoProgress,
                        error = chat.nanoError,
                        onDownload = { model.prepareNano(assistant) },
                    )
                }
                HorizontalDivider(color = Border.copy(alpha = .65f))
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(), state = chatListState,
                    verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 18.dp),
                ) {
                    if (messages.size <= 1) item {
                        DinexCard(accent = Indigo, vibrant = true) {
                            Text(t("MENOS DUDAS. MÁS CLARIDAD."), color = Indigo, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Text(t("¿Qué quieres descubrir hoy?"), color = Night, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(9.dp))
                            Text(t("Revisa tus gastos, piensa en tu próxima compra o empieza un plan de ahorro."), color = Gray, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                    items(messages) { item ->
                        ChatBubble(item, onReport = if (!item.user && !item.error && item.id > 0) ({ reportTarget = it }) else null)
                    }
                    if (loading) item { AssistantThinking() }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 7.dp)) {
                    items(listOf("¿En qué gasto más?", "Hazme un plan semanal", "¿Puedo gastar S/ 80?", "¿Cómo voy con mi ahorro?")) { suggestion ->
                        SuggestionChip(onClick = { send(suggestion) }, label = { Text(suggestion, fontSize = 12.sp) }, enabled = !loading && remaining > 0,
                            icon = { Icon(DinexNavigationIcons.Sparkle, null, tint = Indigo, modifier = Modifier.size(15.dp)) },
                            shape = RoundedCornerShape(14.dp))
                    }
                }
                AiQuotaNotice(remaining)
                Spacer(Modifier.height(5.dp))
                Surface(shape = RoundedCornerShape(24.dp), color = CardRaised.copy(alpha = .9f), border = androidx.compose.foundation.BorderStroke(1.dp, Indigo.copy(alpha = .35f))) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                        TextField(draft, { if (it.length <= 500) draft = it }, Modifier.weight(1f),
                            placeholder = { Text(if (remaining > 0) t("Escribe tu pregunta…") else t("Vuelve mañana para preguntar"), fontSize = 14.sp) }, maxLines = 4,
                            enabled = remaining > 0,
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(draft) }))
                        Button(onClick = { send(draft) }, enabled = draft.isNotBlank() && !loading && remaining > 0,
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Enviar mensaje" },
                            contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = colors.onPrimary)) {
                            Icon(DinexNavigationIcons.Send, null, modifier = Modifier.size(23.dp))
                        }
                    }
                }
                Text(t("Puede equivocarse. Revisa cualquier decisión financiera."), color = Gray, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

/** Indica cuántas consultas quedan hoy; al llegar a 0 explica cuándo se renuevan. */
@Composable
private fun AiQuotaNotice(remaining: Int) {
    val empty = remaining <= 0
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(DAILY_AI_QUERIES) { index ->
                Box(Modifier.size(width = 14.dp, height = 4.dp).clip(CircleShape).background(if (index < remaining) Green else Border))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                empty -> t("Usaste tus {0} consultas de hoy. Se renuevan mañana.", DAILY_AI_QUERIES)
                remaining == 1 -> "Te queda 1 consulta hoy"
                else -> t("Te quedan {0} de {1} consultas hoy", remaining, DAILY_AI_QUERIES)
            },
            color = if (empty) Orange else Gray, fontSize = 11.sp, fontWeight = if (empty) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AssistantContextLine(label: String, value: String, tint: Color) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(t(label), color = Gray, fontSize = 12.sp)
        Text(value, color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NanoDownloadCard(downloading: Boolean, progress: Float?, error: String?, onDownload: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ElectricBlue.copy(alpha = .10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = .30f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(DinexNavigationIcons.Sparkle, null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Gemini Nano en tu teléfono"), color = Night, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (downloading) "Descargando el modelo para responder sin internet…"
                        else "Responde sin internet y sin enviar tus datos a la nube.",
                        color = Gray, fontSize = 10.sp,
                    )
                }
                if (!downloading) TextButton(onClick = onDownload) {
                    Text(t("Descargar"), color = ElectricBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (downloading) {
                Spacer(Modifier.height(8.dp))
                if (progress != null) {
                    DinexProgress(progress, ElectricBlue, height = 6.dp)
                    Text(t("{0}% descargado", (progress * 100).toInt()), color = Gray, fontSize = 10.sp)
                } else {
                    Text(t("Preparando la descarga…"), color = Gray, fontSize = 10.sp)
                }
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Red, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun AssistantThinking() {
    val reducedMotionForDots = LocalDinexReducedMotion.current
    val phase: State<Float> = if (reducedMotionForDots) remember { mutableStateOf(.5f) }
    else rememberInfiniteTransition(label = "thinking").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "dots")
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(Modifier.padding(end = 5.dp).size(6.dp).graphicsLayer {
                translationY = if (reducedMotionForDots) 0f else -kotlin.math.sin(phase.value * 2 * PI - i).toFloat() * 3.dp.toPx()
            }.clip(CircleShape).background(Indigo))
        }
        Spacer(Modifier.width(7.dp))
        Text(t("Analizando tus movimientos…"), color = Gray, fontSize = 13.sp)
    }
}

@Composable
internal fun ChatBubble(message: ChatMessage, onReport: ((ChatMessage) -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (message.user) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 610.dp).dinexEntrance(),
            shape = RoundedCornerShape(
                topStart = 19.dp,
                topEnd = 19.dp,
                bottomStart = if (message.user) 19.dp else 5.dp,
                bottomEnd = if (message.user) 5.dp else 19.dp,
            ),
            color = when {
                message.user -> Green
                message.error -> Orange.copy(alpha = .12f)
                else -> CardRaised
            },
            border = if (message.user) null else androidx.compose.foundation.BorderStroke(
                1.dp,
                if (message.error) Orange.copy(alpha = .3f) else Border,
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                if (!message.user) {
                    Text(
                        when {
                            message.error -> "DINEX · AVISO"
                            message.engine == GeminiEngine.NANO -> "DINEX IA · NANO"
                            else -> "DINEX IA"
                        },
                        color = if (message.error) Orange else GreenDark,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = .7.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                SelectionContainer { Text(message.text, color = if (message.user) Color.White else Night, fontSize = 14.sp, lineHeight = 22.sp) }
                // Política de Google Play: cada respuesta de la IA se puede reportar.
                if (onReport != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                        if (message.reported) Text(t("Reportada · gracias"), color = Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
                        else TextButton(
                            onClick = { onReport(message) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp).semantics { contentDescription = "Reportar esta respuesta de Dinex IA" },
                        ) {
                            Icon(DinexNavigationIcons.Shield, null, tint = Gray, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("Reportar"), color = Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Reporte de una respuesta de Dinex IA: motivo y comentario opcional. */
@Composable
private fun AiReportDialog(message: ChatMessage, onDismiss: () -> Unit, onSend: (AiReportReason, String) -> Unit) {
    var reason by remember { mutableStateOf<AiReportReason?>(null) }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Reportar respuesta"), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = CardRaised) {
                    Text(message.text, color = Gray, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
                }
                Text(t("¿Qué problema tiene?"), color = Night, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                AiReportReason.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickableRow { reason = option }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(t(option.label), color = Night, fontSize = 14.sp)
                    }
                }
                OutlinedTextField(
                    comment, { if (it.length <= 500) comment = it },
                    label = { Text(t("Comentario (opcional)")) }, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                Text(t("Se enviará el texto de esta respuesta para revisarla. No se envían tus movimientos."), color = Gray, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = { reason?.let { onSend(it, comment) } }, enabled = reason != null, colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                Text(t("Enviar reporte"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancelar")) } },
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)
