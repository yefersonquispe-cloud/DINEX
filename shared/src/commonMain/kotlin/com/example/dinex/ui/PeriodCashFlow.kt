package com.example.dinex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════════
// ANÁLISIS · FLUJO DEL PERIODO Y CALENDARIO DE GASTO
// Dos vistas propias de Dinex:
// 1. "Pulso del dinero": ingresos suben y gastos bajan desde una línea central,
//    con el saldo acumulado dibujado encima. Se toca o se desliza para ver un día.
// 2. "Calendario de gasto": el mes como calendario de calor, coloreado según el
//    límite diario (verde dentro, ámbar cerca, rosa pasado).
// ═══════════════════════════════════════════════════════════════════════════

private fun isoToday(): String = civilDateFromEpochDays().let { "${it.year}-${it.month.toString().padStart(2, '0')}-${it.day.toString().padStart(2, '0')}" }

private fun shortDate(iso: String): String {
    val month = iso.drop(5).take(2).toIntOrNull() ?: return iso
    val day = iso.takeLast(2).toIntOrNull() ?: return iso
    return "$day ${currentMonthShort(month).lowercase()}"
}

@Composable
internal fun PeriodCashFlow(movements: List<Movement>, dates: List<String>) {
    val today = isoToday()
    val totals = dates.map { date ->
        val items = movements.filter { it.dateIso() == date }
        items.filter { it.income }.sumOf { it.amount } to items.filterNot { it.income }.sumOf { it.amount }
    }
    // Por defecto se muestra hoy (o el último día con movimientos); nunca un día que aún no llega.
    val initial = dates.lastOrNull { it == today }
        ?: dates.indices.lastOrNull { dates[it] <= today && (totals[it].first > 0 || totals[it].second > 0) }?.let { dates[it] }
        ?: dates.lastOrNull { it <= today } ?: dates.firstOrNull().orEmpty()
    var selected by remember(dates, movements.size) { mutableStateOf(initial) }
    val index = dates.indexOf(selected).coerceAtLeast(0)
    val totalIn = totals.sumOf { it.first }
    val totalOut = totals.sumOf { it.second }
    val running = totals.runningFold(0.0) { acc, day -> acc + day.first - day.second }.drop(1)
    val peak = totals.maxOfOrNull { maxOf(it.first, it.second) }?.takeIf { it > 0 } ?: 1.0
    val runPeak = running.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0 } ?: 1.0
    val still = LocalDinexReducedMotion.current
    val grow = remember(dates) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(dates) { if (!still) grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val incomeColor = Green
    val expenseColor = Red
    val lineColor = Indigo
    val gridColor = Border
    val futureColor = Border.copy(alpha = .5f)

    DinexCard {
        Text("PULSO DEL DINERO", color = GreenDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text("Ingresos y gastos del periodo", color = Night, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        // Totales del periodo en tres píldoras.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowPill("Entró", money(totalIn), incomeColor, Modifier.weight(1f))
            FlowPill("Salió", money(totalOut), expenseColor, Modifier.weight(1f))
            FlowPill("Balance", money(totalIn - totalOut), if (totalIn - totalOut >= 0) GreenDark else expenseColor, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        fun pick(x: Float, width: Float) {
            if (dates.isEmpty() || width <= 0f) return
            val i = ((x / width) * dates.size).toInt().coerceIn(0, dates.lastIndex)
            selected = dates[i]
        }
        Canvas(
            Modifier.fillMaxWidth().height(190.dp)
                .semantics { contentDescription = "Gráfico de ingresos y gastos por día. Toca o desliza para elegir un día." }
                .pointerInput(dates) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
                .pointerInput(dates) { detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width.toFloat()) } },
        ) {
            val n = dates.size.coerceAtLeast(1)
            val slot = size.width / n
            val mid = size.height / 2f
            val reach = mid * .86f
            val barWidth = (slot * .52f).coerceIn(2.dp.toPx(), 14.dp.toPx())
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
            // Línea central y guía del día elegido.
            drawLine(gridColor, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
            val selX = slot * index + slot / 2f
            drawLine(lineColor.copy(alpha = .35f), Offset(selX, 0f), Offset(selX, size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            // Saldo acumulado: área suave detrás de las barras, para leer la tendencia sin tapar los días.
            val lastPast = dates.indexOfLast { it <= today }
            if (lastPast >= 1) {
                fun point(i: Int) = Offset(slot * i + slot / 2f, mid - (running[i] / runPeak * reach * .72f * grow.value).toFloat())
                val path = Path()
                path.moveTo(point(0).x, point(0).y)
                for (i in 1..lastPast) {
                    val a = point(i - 1); val b = point(i)
                    val cx = (a.x + b.x) / 2f
                    path.cubicTo(cx, a.y, cx, b.y, b.x, b.y)
                }
                val area = Path().apply {
                    addPath(path)
                    lineTo(point(lastPast).x, mid); lineTo(point(0).x, mid); close()
                }
                drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = .16f), lineColor.copy(alpha = .02f)), 0f, mid))
                drawPath(path, lineColor.copy(alpha = .75f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
                if (index <= lastPast) {
                    val p = point(index)
                    drawCircle(Color.White, 5.dp.toPx(), p)
                    drawCircle(lineColor, 3.5.dp.toPx(), p)
                }
            }
            dates.forEachIndexed { i, date ->
                val cx = slot * i + slot / 2f
                val future = date > today
                if (future) {
                    drawCircle(futureColor, 1.5.dp.toPx(), Offset(cx, mid))
                    return@forEachIndexed
                }
                val focus = i == index
                val inH = (totals[i].first / peak * reach * grow.value).toFloat()
                val outH = (totals[i].second / peak * reach * grow.value).toFloat()
                val alpha = if (focus) 1f else .8f
                if (inH > 0f) drawRoundRect(
                    Brush.verticalGradient(listOf(incomeColor.copy(alpha = alpha), incomeColor.copy(alpha = alpha * .45f)), mid - inH, mid),
                    Offset(cx - barWidth / 2f, mid - inH - 1.dp.toPx()), Size(barWidth, inH), radius,
                )
                if (outH > 0f) drawRoundRect(
                    Brush.verticalGradient(listOf(expenseColor.copy(alpha = alpha * .45f), expenseColor.copy(alpha = alpha)), mid, mid + outH),
                    Offset(cx - barWidth / 2f, mid + 1.dp.toPx()), Size(barWidth, outH), radius,
                )
                if (inH <= 0f && outH <= 0f) drawCircle(gridColor, 2.dp.toPx(), Offset(cx, mid))
            }
        }
        // Etiquetas de inicio, medio y fin para ubicarse sin saturar.
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOfNotNull(dates.firstOrNull(), dates.getOrNull(dates.size / 2), dates.lastOrNull()).forEachIndexed { i, d ->
                Text(shortDate(d), color = Gray, fontSize = 10.sp, modifier = Modifier.weight(1f),
                    textAlign = when (i) { 0 -> TextAlign.Start; 1 -> TextAlign.Center; else -> TextAlign.End })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(incomeColor, "Ingresos"); LegendDot(expenseColor, "Gastos"); LegendDot(lineColor, "Saldo acumulado")
        }
        Spacer(Modifier.height(14.dp))
        // Detalle del día elegido.
        val day = totals.getOrNull(index) ?: (0.0 to 0.0)
        Surface(shape = RoundedCornerShape(18.dp), color = CardRaised, border = BorderStroke(1.dp, Border)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (selected == today) "Hoy · ${shortDate(selected)}" else shortDate(selected), color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        if (selected > today) "Este día todavía no llega."
                        else "Entró ${money(day.first)} · Salió ${money(day.second)}",
                        color = Gray, fontSize = 12.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Balance", color = Gray, fontSize = 11.sp)
                    val net = day.first - day.second
                    Text((if (net > 0) "+" else "") + money(net), color = if (net >= 0) GreenDark else expenseColor, fontWeight = FontWeight.Black, fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun FlowPill(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .09f)).padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Gray, fontSize = 11.sp, maxLines = 1)
        Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Gray, fontSize = 11.sp)
    }
}

/** Calendario de calor del mes: cada día se colorea según cuánto se gastó frente al límite diario. */
@Composable
internal fun DailyBars(movements: List<Movement>, year: Int = civilDateFromEpochDays().year, month: Int = civilDateFromEpochDays().month, dailyLimit: Double = 0.0) {
    val now = civilDateFromEpochDays()
    val isCurrentMonth = now.year == year && now.month == month
    val days = (1..31).filter { validDate(year, month, it) }
    val values = days.map { day -> movements.filter { !it.income && it.day == day && it.month == month && it.year == year }.sumOf { it.amount } }
    val elapsed = if (isCurrentMonth) now.day else if (year * 12 + month < now.year * 12 + now.month) days.size else 0
    val total = values.sumOf { it }
    val average = if (elapsed > 0) total / elapsed else 0.0
    val peak = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val withinLimit = if (dailyLimit > 0) (0 until elapsed).count { values[it] <= dailyLimit } else 0
    val initialDay = if (isCurrentMonth) now.day else days.getOrElse(values.indices.maxByOrNull { values[it] } ?: 0) { 1 }
    var selectedDay by remember(year, month, movements.size) { mutableIntStateOf(initialDay) }
    val selectedMovements = movements.filter { !it.income && it.day == selectedDay && it.month == month && it.year == year }
    val selectedTotal = selectedMovements.sumOf { it.amount }
    // Lunes como primer día de la semana.
    val firstWeekday = ((epochDaysOf(year, month, 1) + 3) % 7 + 7) % 7

    DinexCard(accent = Indigo) {
        Text("CALENDARIO DE GASTO", color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text("Gasto diario · ${currentMonthName(month)}", color = Night, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowPill("Total", money(total), Night, Modifier.weight(1f))
            FlowPill("Promedio/día", money(average), Indigo, Modifier.weight(1f))
            if (dailyLimit > 0 && elapsed > 0) FlowPill("En tu límite", "$withinLimit de $elapsed días", GreenDark, Modifier.weight(1.2f))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                Text(it, color = Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        val cells = List(firstWeekday) { null } + days
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 7).forEach { col ->
                    val day = week.getOrNull(col)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day != null) {
                            val value = values[day - 1]
                            val future = isCurrentMonth && day > now.day
                            CalendarDay(
                                day = day, value = value, peak = peak, limit = dailyLimit,
                                selected = day == selectedDay, today = isCurrentMonth && day == now.day, future = future,
                                onClick = { if (!future) selectedDay = day },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(Green, if (dailyLimit > 0) "Dentro del límite" else "Poco gasto")
            LegendDot(Orange, "Cerca")
            LegendDot(Red, if (dailyLimit > 0) "Te pasaste" else "Mucho gasto")
        }
        Spacer(Modifier.height(14.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = CardRaised, border = BorderStroke(1.dp, if (selectedMovements.isEmpty()) Border else Indigo.copy(alpha = .3f))) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (isCurrentMonth && selectedDay == now.day) "Hoy · " else "") + "$selectedDay de ${currentMonthName(month).lowercase()}",
                        color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    Text(money(selectedTotal), color = if (selectedTotal > 0) Red else Gray, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                if (dailyLimit > 0 && selectedTotal > 0) {
                    Spacer(Modifier.height(8.dp))
                    DinexProgress((selectedTotal / dailyLimit).toFloat().coerceIn(0f, 1f), dayTone(selectedTotal, dailyLimit, peak), height = 6.dp)
                    Text(
                        if (selectedTotal > dailyLimit) "Te pasaste por ${money(selectedTotal - dailyLimit)} de tu límite de ${money(dailyLimit)}."
                        else "Te quedaron ${money(dailyLimit - selectedTotal)} de tu límite de ${money(dailyLimit)}.",
                        color = Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (selectedMovements.isEmpty()) Text("No registraste gastos este día.", color = Gray, fontSize = 12.sp)
                else selectedMovements.forEach { movement ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(categories.firstOrNull { it.name == movement.category }?.color ?: Gray))
                        Spacer(Modifier.width(8.dp))
                        Text(movement.title, color = Night, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(money(movement.amount), color = Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Verde dentro del límite, ámbar al acercarse (80 %) y rosa al pasarse. Sin límite, según el mayor gasto del mes. */
private fun dayTone(value: Double, limit: Double, peak: Double): Color {
    val ratio = if (limit > 0) value / limit else value / peak
    return when {
        limit > 0 && ratio > 1.0 -> Red
        limit > 0 && ratio >= .8 -> Orange
        limit <= 0 && ratio > .66 -> Red
        limit <= 0 && ratio > .33 -> Orange
        else -> Green
    }
}

@Composable
private fun CalendarDay(day: Int, value: Double, peak: Double, limit: Double, selected: Boolean, today: Boolean, future: Boolean, onClick: () -> Unit) {
    val tone = dayTone(value, limit, peak)
    // La intensidad del color crece con el gasto; un día sin gasto queda casi vacío.
    val intensity = if (value <= 0) 0f else (.22f + .58f * (value / (if (limit > 0) maxOf(limit, peak) else peak)).toFloat()).coerceIn(.22f, .8f)
    val fill by animateFloatAsState(intensity, tween(if (LocalDinexReducedMotion.current) 0 else 500), label = "calor")
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxSize().clip(shape)
            .background(if (future) Color.Transparent else if (value > 0) tone.copy(alpha = fill) else CardRaised)
            .then(
                when {
                    selected -> Modifier.border(2.dp, Night, shape)
                    today -> Modifier.border(1.5.dp, GreenDark, shape)
                    future -> Modifier.border(1.dp, Border.copy(alpha = .6f), shape)
                    else -> Modifier
                },
            )
            .clickable(enabled = !future, onClickLabel = "Ver el $day") { onClick() }
            .semantics { contentDescription = "Día $day: " + if (future) "todavía no llega" else "gastaste ${money(value)}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            color = when { future -> Gray.copy(alpha = .5f); value > 0 && fill > .5f -> Color.White; else -> Night },
            fontSize = 12.sp, fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
