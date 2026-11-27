package com.example.dinex

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import kotlin.math.sin
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_mascot_ai

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
    locationOn: Boolean = false,
    onLocation: () -> Unit = {},
    onMore: () -> Unit = {},
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
                // Accesos rápidos arriba de los ahorros: Escanear, Voz, Ubicación y Más.
                val quick: @Composable () -> Unit = {
                    QuickActions(onScan = onScan, onVoice = onVoice, locationOn = locationOn, onLocation = onLocation, onMore = onMore, wide = desktop)
                }
                if (desktop) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    quick()
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) { BalanceStrip(balance, income, expenses, savingRate) }
                        Box(Modifier.weight(1f)) { SavingsHero(savings, goals, onClick = onGoalDetails, onOpenGoal = onOpenGoal) }
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BalanceStrip(balance, income, expenses, savingRate)
                    quick()
                    SavingsHero(savings, goals, onClick = onGoalDetails, onOpenGoal = onOpenGoal)
                }
            }
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
                Text(t("Activa tus alertas"), color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(t("Te avisamos si superas tu presupuesto diario y te recordamos ahorrar antes de que termine el día."),
                    color = Gray, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
            ) { Text(t("Activar"), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun QuickActions(
    onScan: () -> Unit,
    onVoice: () -> Unit,
    locationOn: Boolean,
    onLocation: () -> Unit,
    onMore: () -> Unit,
    wide: Boolean = false,
) {
    // Los cuatro con la misma forma: ícono, nombre y una línea corta debajo, así quedan alineados.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 10.dp)) {
        QuickActionTile("Escanear", "Boleta o foto", DinexNavigationIcons.ScanReceipt, GreenDark, onScan, wide, Modifier.weight(1f))
        QuickActionTile("Voz", "Oye Dinex", DinexNavigationIcons.Microphone, ElectricBlue, onVoice, wide, Modifier.weight(1f))
        QuickActionTile(
            "Ubicación", if (locationOn) "Activa" else "Apagada", DinexNavigationIcons.MapPin,
            if (locationOn) Green else Gray, onLocation, wide, Modifier.weight(1f),
            toggle = locationOn,
        )
        QuickActionTile("Más", "Automático", DinexNavigationIcons.More, Orange, onMore, wide, Modifier.weight(1f))
    }
}

/**
 * Acceso rápido del inicio. [toggle] no nulo lo convierte en interruptor: encendido se pinta
 * relleno con el color de marca y muestra un punto verde; apagado queda en gris.
 */
@Composable
private fun QuickActionTile(
    label: String,
    caption: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
    toggle: Boolean? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val on = toggle == true
    Surface(
        modifier = modifier.fillMaxHeight().clip(RoundedCornerShape(RadiusMd)).hoverable(interaction)
            .clickable(onClickLabel = label, onClick = onClick),
        shape = RoundedCornerShape(RadiusMd),
        color = when {
            on -> Mint
            hovered -> SurfaceHover
            else -> Card
        },
        border = BorderStroke(1.dp, if (on || hovered) accent.copy(alpha = .45f) else Border),
    ) {
        val badge: @Composable () -> Unit = {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) accent else accent.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (on) Color.White else accent, modifier = Modifier.size(22.dp))
            }
        }
        val captionRow: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (toggle != null) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Lime else Gray.copy(alpha = .55f)))
                    Spacer(Modifier.width(4.dp))
                }
                Text(caption, color = if (on) GreenDark else Gray, fontSize = if (wide) 11.sp else 10.sp,
                    fontWeight = if (toggle != null) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (wide) {
            // Escritorio: una fila compacta con la descripción al lado.
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                badge()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    captionRow()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                badge()
                Spacer(Modifier.height(8.dp))
                Text(label, color = Night, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                captionRow()
            }
        }
    }
}

/**
 * Tarjeta Dinex del inicio: esmeralda intensa en modo claro y zafiro en modo oscuro, con luces de color,
 * filo dorado, chip, logo DINEX con la mascota, líneas finas como las de un billete, una chakana andina
 * de marca de agua y una franja de rombos como el tejido del chullo. Muestra el saldo, cuánto de los
 * ingresos ya se usó y los totales del mes.
 */
@Composable
internal fun BalanceStrip(balance: Double, income: Double, expenses: Double, savingRate: Int) {
    val dark = DinexThemeState.dark
    // Colores propios de la tarjeta (no cambian el resto de la app).
    val base = if (dark) listOf(Color(0xFF0E1C38), Color(0xFF173E68), Color(0xFF1C6A93))
        else listOf(Color(0xFF032E22), Color(0xFF075B40), Color(0xFF0B8258))
    val aurora = if (dark) Color(0xFF4FD8F0) else Color(0xFF3EE6C1)  // luz de arriba
    val warm = if (dark) Color(0xFFA493FF) else Color(0xFF2BC4E8)    // luz de abajo
    val barStart = if (dark) Color(0xFFA493FF) else Color(0xFFD8F26A)
    val gold = Color(0xFFF2D48A)
    val spentShare = if (income > 0) (expenses / income).toFloat().coerceIn(0f, 1f) else 0f
    val spentAnim by animateFloatAsState(spentShare, tween(900, easing = FastOutSlowInEasing))
    val overspent = income > 0 && expenses > income
    val shape = RoundedCornerShape(RadiusLg)
    Box(
        Modifier.fillMaxWidth().dinexEntrance()
            .shadow(18.dp, shape, ambientColor = base[1].copy(alpha = .45f), spotColor = base[2].copy(alpha = .75f))
            .clip(shape)
            .background(Brush.linearGradient(base))
            .border(1.dp, Brush.linearGradient(listOf(gold.copy(alpha = .8f), Color.White.copy(alpha = .06f), gold.copy(alpha = .5f))), shape)
            .drawBehind {
                val w = size.width
                val h = size.height
                // Luces de color: una arriba a la derecha y otra abajo a la izquierda.
                val top = Offset(w * .95f, -h * .08f)
                drawCircle(Brush.radialGradient(listOf(aurora.copy(alpha = if (dark) .46f else .36f), aurora.copy(alpha = 0f)), center = top, radius = w * .7f),
                    radius = w * .7f, center = top)
                val bottom = Offset(w * .02f, h * 1.08f)
                drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = .34f), warm.copy(alpha = 0f)), center = bottom, radius = w * .62f),
                    radius = w * .62f, center = bottom)
                // Líneas onduladas finas, como las de seguridad de un billete; se desvanecen hacia el texto.
                val ink = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = .15f)), startX = w * .3f, endX = w)
                val lineWidth = .8.dp.toPx()
                for (k in 0 until 9) {
                    val wave = Path()
                    val amplitude = h * (.06f + k * .011f)
                    val middle = h * (.34f + k * .036f)
                    var x = 0f
                    wave.moveTo(0f, middle)
                    while (x <= w) {
                        wave.lineTo(x, middle + amplitude * sin(x / w * 2f * PI.toFloat() * 1.3f + k * .42f))
                        x += 6f
                    }
                    drawPath(wave, ink, style = Stroke(lineWidth))
                }
                drawChakana(Offset(w * .63f, h * .41f), 7.5.dp.toPx(), Color.White.copy(alpha = .14f))
                // Reflejo diagonal, como el brillo de una tarjeta plastificada.
                drawRect(Brush.linearGradient(
                    0f to Color.Transparent, .40f to Color.Transparent, .49f to Color.White.copy(alpha = .10f),
                    .58f to Color.Transparent, 1f to Color.Transparent,
                    start = Offset.Zero, end = Offset(w, h)))
                drawAndeanBand(gold, aurora)
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CardChip()
                Spacer(Modifier.width(8.dp))
                ContactlessMark(Modifier.size(width = 12.dp, height = 18.dp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color.White).border(1.5.dp, gold, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Image(painterResource(Res.drawable.dinex_mascot_ai), contentDescription = null, modifier = Modifier.size(23.dp)) }
                Spacer(Modifier.width(7.dp))
                Text("DINEX", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 2.8.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t("SALDO DISPONIBLE"), color = Color.White.copy(alpha = .95f), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (income > 0) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.clip(CircleShape).background(if (overspent) Color(0x40FF8FA3) else Color.White.copy(alpha = .14f))
                            .border(1.dp, if (overspent) Color(0x80FFC0CB) else gold.copy(alpha = .45f), CircleShape)
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (overspent) Color(0xFFFFB4BE) else gold))
                        Spacer(Modifier.width(5.dp))
                        Text(if (overspent) t("Gastas de más") else t("Ahorras {0}%", savingRate.coerceAtLeast(0)),
                            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
            Text(
                moneyGrouped(rememberCountUp(balance)), color = Color.White, fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp, letterSpacing = (-1).sp, maxLines = 1,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .28f), Offset(0f, 3f), 10f)),
            )
            if (income > 0) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .15f))) {
                        Box(
                            Modifier.fillMaxWidth(spentAnim).fillMaxHeight().clip(CircleShape)
                                .background(
                                    if (spentShare >= .8f) Brush.horizontalGradient(listOf(Color(0xFFFF8FA3), Color(0xFFFFC2CB)))
                                    else Brush.horizontalGradient(listOf(barStart, aurora)),
                                ),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(t("Usaste {0}%", ((expenses / income) * 100).toInt()), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .35f), Offset(0f, 1f), 4f)))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF02140F).copy(alpha = if (dark) .34f else .30f))
                    .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BalanceStat("Ingresos", "+ ${moneyGrouped(income)}", Modifier.weight(1f), expense = false)
                Box(Modifier.width(1.dp).height(30.dp).background(Brush.verticalGradient(listOf(Color.Transparent, gold.copy(alpha = .6f), Color.Transparent))))
                Spacer(Modifier.width(10.dp))
                BalanceStat("Gastos", "− ${moneyGrouped(expenses)}", Modifier.weight(1f), expense = true)
            }
        }
    }
}

/** Chakana (cruz andina escalonada) en línea fina, doble y con su círculo al centro. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChakana(center: Offset, unit: Float, color: Color) {
    val corners = listOf(-1 to -3, 1 to -3, 1 to -2, 2 to -2, 2 to -1, 3 to -1, 3 to 1, 2 to 1, 2 to 2, 1 to 2,
        1 to 3, -1 to 3, -1 to 2, -2 to 2, -2 to 1, -3 to 1, -3 to -1, -2 to -1, -2 to -2, -1 to -2)
    val stroke = Stroke(1.2.dp.toPx())
    for (scale in listOf(1f, .7f)) {
        val shape = Path()
        corners.forEachIndexed { i, (x, y) ->
            val px = center.x + x * unit * scale
            val py = center.y + y * unit * scale
            if (i == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
        }
        shape.close()
        drawPath(shape, color, style = stroke)
    }
    drawCircle(color, radius = unit * .7f, center = center, style = stroke)
}

/** Franja andina al pie de la tarjeta: rombos dorados y de color, como el tejido del chullo de la mascota. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAndeanBand(gold: Color, accent: Color) {
    val unit = 7.dp.toPx()
    val y = size.height - unit * 1.35f
    drawLine(gold.copy(alpha = .35f), Offset(0f, y - unit * .95f), Offset(size.width, y - unit * .95f), 1.dp.toPx())
    var x = unit
    var i = 0
    while (x < size.width) {
        val color = if (i % 2 == 0) gold.copy(alpha = .8f) else accent.copy(alpha = .75f)
        val half = unit * .6f
        val diamond = Path().apply {
            moveTo(x, y - half); lineTo(x + half, y); lineTo(x, y + half); lineTo(x - half, y); close()
        }
        drawPath(diamond, color)
        drawCircle(Color.White.copy(alpha = .35f), radius = 1.1.dp.toPx(), center = Offset(x + unit, y))
        x += unit * 2f
        i++
    }
}

/** Chip dorado de tarjeta dibujado a mano. */
@Composable
private fun CardChip() {
    Canvas(Modifier.size(width = 34.dp, height = 25.dp)) {
        val r = CornerRadius(6.dp.toPx())
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFFEDB0), Color(0xFFE2BC5F), Color(0xFFB98F38), Color(0xFFEBCB7A)),
            start = Offset.Zero, end = Offset(size.width, size.height)), cornerRadius = r)
        val line = Color(0xFF7E5E1E).copy(alpha = .55f)
        val w = 1.dp.toPx()
        drawLine(line, Offset(size.width * .34f, 0f), Offset(size.width * .34f, size.height), w)
        drawLine(line, Offset(size.width * .66f, 0f), Offset(size.width * .66f, size.height), w)
        drawLine(line, Offset(0f, size.height * .5f), Offset(size.width * .34f, size.height * .5f), w)
        drawLine(line, Offset(size.width * .66f, size.height * .5f), Offset(size.width, size.height * .5f), w)
        drawRoundRect(line, topLeft = Offset(size.width * .34f, size.height * .28f),
            size = androidx.compose.ui.geometry.Size(size.width * .32f, size.height * .44f), cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(w))
        drawRoundRect(Color.White.copy(alpha = .45f), cornerRadius = r, style = Stroke(w))
    }
}

/** Ondas de pago sin contacto. */
@Composable
private fun ContactlessMark(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
        for (k in 0..2) {
            val radius = size.height * (.22f + k * .17f)
            drawArc(Color.White.copy(alpha = .9f - k * .15f), startAngle = -50f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(-radius + 2.dp.toPx(), size.height / 2 - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = stroke)
        }
    }
}

@Composable
private fun BalanceStat(label: String, value: String, modifier: Modifier, expense: Boolean) {
    // Verde y coral intensos sobre la franja oscura: se leen bien en modo claro y oscuro.
    val amountColor = if (expense) Color(0xFFFF7D96) else Color(0xFF4DFFA0)
    val badge = if (expense) Brush.linearGradient(listOf(Color(0xFFFF6B8B), Color(0xFFE8335A)))
        else Brush.linearGradient(listOf(Color(0xFF3BF59A), Color(0xFF10B866)))
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(badge).border(1.dp, Color.White.copy(alpha = .35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (expense) DinexNavigationIcons.ArrowDown else DinexNavigationIcons.ArrowUp, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(t(label), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(value, color = amountColor, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .35f), Offset(0f, 1f), 4f)))
        }
    }
}

@Composable
internal fun MiniStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(RadiusSm), color = Card.copy(alpha = .65f), border = BorderStroke(1.dp, tint.copy(alpha = .24f))) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (label == "Ingresos" || label == "Gastos") Icon(if (label == "Ingresos") DinexNavigationIcons.ArrowUp else DinexNavigationIcons.ArrowDown, null, tint = tint, modifier = Modifier.size(14.dp))
                Text(t(label), color = Gray, fontSize = 12.sp)
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
                Text(t("Mis ahorros"), color = Night, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(t("Cada sol te acerca a tu meta"), color = Gray, fontSize = 12.sp)
            }
            Text(
                when (goals.size) { 0 -> t("Sin metas"); 1 -> t("1 meta"); else -> t("{0} metas", goals.size) },
                color = NightSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.border(1.dp, Border, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // Total ahorrado y avance general
        Spacer(Modifier.height(18.dp))
        Text(t("Total ahorrado"), color = Gray, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(moneyGrouped(savings), color = Night, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
            if (totalTarget > 0) Text(savingsPercent(progress), color = GreenDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (totalTarget > 0) {
            Spacer(Modifier.height(10.dp))
            SavingsProgress(progress, Green, 12.dp)
            Spacer(Modifier.height(8.dp))
            Row {
                Text(t("Meta total {0}", moneyGrouped(totalTarget)), color = Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(if (missing > 0) t("Faltan {0}", moneyGrouped(missing)) else t("¡Metas cumplidas!"), color = Gray, fontSize = 11.sp)
            }
        }

        // Metas
        Spacer(Modifier.height(18.dp))
        if (goals.isEmpty()) {
            Text(t("Crea una meta para darle un propósito a tu ahorro."), color = Gray, fontSize = 13.sp)
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).border(1.dp, Border.copy(alpha = .8f), RoundedCornerShape(14.dp))) {
                // Se muestran primero las metas con más avance, sin alterar sus importes.
                val shown = goals.sortedByDescending { if (it.target > 0) it.saved / it.target else 0.0 }.take(3)
                shown.forEachIndexed { index, goal ->
                    SavingsGoalRow(goal) { onOpenGoal(goal.id) }
                    if (index < shown.lastIndex) HorizontalDivider(color = Border.copy(alpha = .7f), modifier = Modifier.padding(horizontal = 14.dp))
                }
            }
            if (goals.size > 3) {
                Spacer(Modifier.height(8.dp))
                Text(t("Y {0}", countLabel(goals.size - 3, "meta más", "metas más")), color = Gray, fontSize = 11.sp)
            }
        }

        if (onClick != null) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (goals.isEmpty()) t("Crear mi primera meta") else t("Ver todas mis metas"),
                    color = GreenDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(DinexNavigationIcons.Chevron, null, tint = GreenDark, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Una meta en una fila: nombre, avance en porcentaje, cuánto lleva de cuánto y su barra. */
@Composable
private fun SavingsGoalRow(goal: SavingsGoal, onOpen: () -> Unit) {
    val name = displayGoalName(goal.name)
    val goalProgress = if (goal.target > 0) (goal.saved / goal.target).toFloat().coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = t("Abrir meta {0}", name), onClick = onOpen).padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (goal.target > 0) Text(
                savingsPercent(goalProgress),
                color = if (goalProgress >= 1f) Color.White else GreenDark, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.clip(CircleShape).background(if (goalProgress >= 1f) Green else Green.copy(alpha = if (DinexThemeState.dark) .22f else .10f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(moneyGrouped(goal.saved), color = Night, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (goal.target > 0) Text(t(" de {0}", moneyGrouped(goal.target)), color = Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Text(t(" ahorrados"), color = Gray, fontSize = 12.sp)
        }
        if (goal.target > 0) {
            Spacer(Modifier.height(10.dp))
            SavingsProgress(goalProgress, Green, 10.dp)
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

/**
 * Barra de ahorro de Dinex: degradado verde que avanza animado, hitos al 25, 50 y 75 %
 * que se encienden al pasarlos, un brillo que recorre el avance y una perla en la punta.
 */
@Composable
private fun SavingsProgress(progress: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
    val still = LocalDinexReducedMotion.current
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (still) 0 else 900, easing = FastOutSlowInEasing), label = "avance de ahorro")
    val shine by rememberInfiniteTransition(label = "brillo de ahorro").animateFloat(
        0f, if (still) 0f else 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "brillo",
    )
    val track = if (DinexThemeState.dark) CardRaised else Color(0xFFE6EFE9)
    val dark = Color(0xFF0E5A36)
    val lime = Color(0xFF7BE0A0)
    val tick = if (DinexThemeState.dark) Color.White.copy(alpha = .18f) else Color(0xFFC9DACF)
    Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = t("Avance {0}", savingsPercent(progress)) }) {
        val h = size.height
        val r = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(track, size = size, cornerRadius = r)
        // Hitos: se ven sobre la pista y se encienden cuando el avance los pasa.
        listOf(.25f, .5f, .75f).forEach { mark ->
            val x = size.width * mark
            val reached = animated >= mark
            drawCircle(if (reached) Color.White.copy(alpha = .85f) else tick, h * .16f, Offset(x, h / 2f))
        }
        if (progress <= 0f) return@Canvas
        val w = maxOf(size.width * animated, h)
        drawRoundRect(
            Brush.horizontalGradient(listOf(dark, color, lime), startX = 0f, endX = maxOf(w, 1f)),
            size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = r,
        )
        // Brillo que recorre la parte avanzada.
        if (!still && w > h * 2) {
            val band = w * .35f
            val x = -band + (w + band) * shine
            clipRect(right = w) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .38f), Color.Transparent), startX = x, endX = x + band),
                    size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = r,
                )
            }
        }
        // Hitos ya alcanzados, en blanco sobre el verde.
        listOf(.25f, .5f, .75f).filter { it * size.width < w - h / 2f }.forEach { mark ->
            drawCircle(Color.White.copy(alpha = .8f), h * .16f, Offset(size.width * mark, h / 2f))
        }
        // Perla en la punta del avance.
        drawCircle(Color.White, h * .36f, Offset(w - h / 2f, h / 2f))
        drawCircle(color, h * .2f, Offset(w - h / 2f, h / 2f))
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
                Text(t("Racha actual"), color = Gray, fontSize = 10.sp)
                Text(countLabel(streak, "día", "días"), color = Night, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(t("Nivel {0}", t(level.name).uppercase()), color = readableAccent(level.color), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (next != null) Text(t("Progreso hacia {0}", t(next.name)), color = Gray, fontSize = 10.sp)
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
            ) { Text(if (savedToday) t("Registrado") else t("Ahorré hoy"), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        if (!savedToday) {
            Spacer(Modifier.height(8.dp))
            Text(t("Ahorra hoy y suma +1 a tu racha. Aunque sea S/ 1, cuenta."), color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        Text(t("Camino de niveles"), color = Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
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
            Text(t("Tu presupuesto"), color = Night, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (onAdjustCategories != null) {
                TextButton(onClick = onAdjustCategories) { Text(t("Ajustar categorías"), color = GreenDark, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Diario", money(limit.toDouble()), GreenDark, Modifier.weight(1f))
            MiniStat("Gastado", money(today), if (warning) Red else Night, Modifier.weight(1f))
            MiniStat("Disponible", money(remaining), if (warning) Orange else Lime, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        DinexProgress(ratio, if (warning) Red else Green, height = 8.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            if (warning) t("Estás cerca del límite de hoy") else t("Aún tienes {0} hoy", money(remaining)),
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
                Text(t("¿A dónde se fue tu dinero?"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Night)
                Text(if (selected == null) t("Toca una porción para filtrar") else t("Filtro activo"), color = Gray, fontSize = 11.sp)
            }
            Text(money(realTotal), fontWeight = FontWeight.Black, color = Night, fontSize = 14.sp)
        }
        Spacer(Modifier.height(14.dp))
        if (realTotal <= 0) {
            Text(t("Todavía no hay gastos registrados este mes."), color = Gray, fontSize = 11.sp)
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
                .clickable(onClickLabel = t("Filtrar {0}", category.name)) { onCategory(category.name) }.padding(horizontal = 9.dp, vertical = 5.dp),
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
        if (selected != null) TextButton(onClick = onClear) { Text(t("Ver todas"), color = GreenDark) }
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
            Text(movement.displayTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Night, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodMark(movement.method, tint)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${t(movement.category)} · ${t(movement.method)} · ${movement.dateLabel()}",
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
