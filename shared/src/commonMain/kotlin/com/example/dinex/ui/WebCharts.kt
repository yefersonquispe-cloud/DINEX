package com.example.dinex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════════════════
// Gráficos de la web. Todos crecen al aparecer, muestran un globo con datos al
// pasar el mouse y respetan "reducir movimiento" (rememberGrow).
// ═══════════════════════════════════════════════════════════════════════════

internal class ChartSeries(val label: String, val values: List<Double>, val color: Color, val fill: Boolean = true, val dashed: Boolean = false)

/** Tope del eje con 4 divisiones de números redondos (0, 20, 40, 60, 80…). */
internal fun niceTop(value: Double): Double {
    val raw = (value / 4).coerceAtLeast(1.0)
    var magnitude = 1.0
    while (magnitude * 10 <= raw) magnitude *= 10
    while (magnitude > raw && magnitude > 1.0) magnitude /= 10
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    return step * 4
}

/** Curva suave que pasa por todos los puntos sin pasarse de largo entre ellos. */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val a = points[i - 1]; val b = points[i]
        val mid = (a.x + b.x) / 2
        cubicTo(mid, a.y, mid, b.y, b.x, b.y)
    }
}

private class ChartText(val measurer: TextMeasurer, val family: FontFamily?)

@Composable
private fun rememberChartText(): ChartText {
    val measurer = rememberTextMeasurer()
    val family = MaterialTheme.typography.bodySmall.fontFamily
    return remember(measurer, family) { ChartText(measurer, family) }
}

private fun DrawScope.label(text: ChartText, value: String, color: Color, at: Offset, size: Int = 10, bold: Boolean = false, alignRight: Boolean = false, center: Boolean = false) {
    val layout = text.measurer.measure(value, TextStyle(color = color, fontSize = size.sp, fontFamily = text.family, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium))
    val x = when { alignRight -> at.x - layout.size.width; center -> at.x - layout.size.width / 2f; else -> at.x }
    drawText(layout, topLeft = Offset(x, at.y - layout.size.height / 2f))
}

/** Globo con un título y filas de datos (cada fila con su punto de color). Se acomoda para no salirse. */
private fun DrawScope.tooltip(text: ChartText, title: String, rows: List<Pair<Color?, String>>, anchor: Offset, background: Color, ink: Color) {
    val titleLayout = text.measurer.measure(title, TextStyle(color = ink.copy(alpha = .7f), fontSize = 11.sp, fontFamily = text.family, fontWeight = FontWeight.SemiBold))
    val rowLayouts = rows.map { text.measurer.measure(it.second, TextStyle(color = ink, fontSize = 12.sp, fontFamily = text.family, fontWeight = FontWeight.Bold)) }
    val pad = 10.dp.toPx(); val dot = 7.dp.toPx(); val gap = 6.dp.toPx(); val line = 4.dp.toPx()
    val width = pad * 2 + max(titleLayout.size.width.toFloat(), (rowLayouts.maxOfOrNull { it.size.width } ?: 0) + dot + gap)
    val height = pad * 2 + titleLayout.size.height + rowLayouts.sumOf { it.size.height } + line * rowLayouts.size
    var x = anchor.x + 14.dp.toPx()
    if (x + width > size.width) x = anchor.x - 14.dp.toPx() - width
    x = x.coerceAtLeast(0f)
    val y = (anchor.y - height / 2).coerceIn(0f, max(0f, size.height - height))
    drawRoundRect(Color.Black.copy(alpha = .12f), Offset(x, y + 3.dp.toPx()), Size(width, height), CornerRadius(11.dp.toPx()))
    drawRoundRect(background, Offset(x, y), Size(width, height), CornerRadius(11.dp.toPx()))
    drawText(titleLayout, topLeft = Offset(x + pad, y + pad))
    var cy = y + pad + titleLayout.size.height + line
    rows.forEachIndexed { i, (color, _) ->
        val layout = rowLayouts[i]
        var tx = x + pad
        if (color != null) { drawCircle(color, dot / 2, Offset(tx + dot / 2, cy + layout.size.height / 2f)); tx += dot + gap }
        drawText(layout, topLeft = Offset(tx, cy))
        cy += layout.size.height + line
    }
}

/** Sigue la posición del mouse sobre un gráfico; null cuando sale. */
private fun Modifier.trackHover(key: Any?, onMove: PointerInputScope.(Offset?) -> Unit, onClick: (PointerInputScope.(Offset) -> Unit)? = null): Modifier = pointerInput(key) {
    val scope = this
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val position = event.changes.firstOrNull()?.position
            when (event.type) {
                PointerEventType.Exit -> scope.onMove(null)
                PointerEventType.Release -> if (position != null) { scope.onMove(position); onClick?.invoke(scope, position) }
                else -> scope.onMove(position)
            }
        }
    }
}

// ---------------------------------------------------------------- Líneas y áreas

/**
 * Gráfico de líneas con área degradada, eje opcional y globo de datos al pasar el mouse.
 * Con [onDark] usa colores para fondos oscuros (tarjeta principal).
 */
@Composable
internal fun LineAreaChart(
    series: List<ChartSeries>,
    labels: List<String>,
    modifier: Modifier,
    onDark: Boolean = false,
    showAxis: Boolean = true,
    reference: Double? = null,
    referenceLabel: String = "",
    tooltipTitle: (Int) -> String = { labels.getOrElse(it) { "" } },
    format: (Double) -> String = ::moneyGrouped,
) {
    val count = series.maxOfOrNull { it.values.size } ?: 0
    var hover by remember { mutableStateOf<Int?>(null) }
    val grow = rememberGrow(count to series.sumOf { it.values.sum() }, 1100)
    val text = rememberChartText()
    val axis = if (onDark) Color.White.copy(alpha = .45f) else W.ink3
    val grid = if (onDark) Color.White.copy(alpha = .07f) else W.line
    val tipBg = if (onDark) Color.White else W.forest
    val tipInk = if (onDark) W.forest else Color.White
    val surface = if (onDark) W.forest else W.surface
    val leftPad = if (showAxis) 48.dp else 0.dp
    Canvas(
        modifier.pointerHoverIcon(PointerIcon.Crosshair).trackHover(count, { pos ->
            hover = pos?.let {
                val left = leftPad.toPx()
                if (count < 2 || it.x < left - 6) null else (((it.x - left) / ((size.width - left) / (count - 1))).roundToInt()).coerceIn(0, count - 1)
            }
        }),
    ) {
        if (count == 0) return@Canvas
        val left = leftPad.toPx()
        val bottom = size.height - if (showAxis && labels.isNotEmpty()) 22.dp.toPx() else 6.dp.toPx()
        val top = 12.dp.toPx()
        val values = series.flatMap { it.values } + listOfNotNull(reference)
        val minValue = values.minOrNull() ?: 0.0
        val maxValue = values.maxOrNull() ?: 1.0
        val lo: Double
        val hi: Double
        if (showAxis) { lo = min(0.0, minValue); hi = niceTop(max(maxValue, 1.0)) } else {
            val span = (maxValue - minValue).takeIf { it > 0 } ?: max(1.0, kotlin.math.abs(maxValue) * .1)
            lo = minValue - span * .12; hi = maxValue + span * .18
        }
        fun x(i: Int) = if (count == 1) (left + size.width) / 2 else left + (size.width - left) * i / (count - 1)
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
        if (showAxis) for (i in 0..4) {
            val v = lo + (hi - lo) * i / 4
            val gy = y(v)
            drawLine(grid, Offset(left, gy), Offset(size.width, gy), 1f, pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            label(text, shortMoney(v), axis, Offset(left - 8.dp.toPx(), gy), alignRight = true)
        }
        if (showAxis && labels.isNotEmpty()) {
            val step = max(1, (count / 6.0).roundToInt())
            labels.forEachIndexed { i, l -> if (i % step == 0 || i == count - 1) label(text, l, axis, Offset(x(i), bottom + 12.dp.toPx()), center = true) }
        }
        reference?.let { ref ->
            val ry = y(ref)
            drawLine(axis.copy(alpha = .8f), Offset(left, ry), Offset(size.width, ry), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f)))
            if (referenceLabel.isNotEmpty()) label(text, referenceLabel, axis, Offset(size.width - 14.dp.toPx(), ry - 12.dp.toPx()), alignRight = true, bold = true)
        }
        clipRect(right = left + (size.width - left) * grow + 8.dp.toPx()) {
            series.forEach { s ->
                if (s.values.isEmpty()) return@forEach
                val points = s.values.mapIndexed { i, v -> Offset(x(i), y(v)) }
                val line = smoothPath(points)
                if (s.fill && points.size > 1) {
                    val area = Path().apply { addPath(line); lineTo(points.last().x, bottom); lineTo(points.first().x, bottom); close() }
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = if (onDark) .32f else .22f), s.color.copy(alpha = 0f)), top, bottom))
                }
                drawPath(line, s.color, style = Stroke(if (s.dashed) 2.dp.toPx() else 2.6.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(7f, 7f)) else null))
            }
        }
        val h = hover
        if (h != null) {
            val hx = x(h)
            drawLine(axis.copy(alpha = .6f), Offset(hx, top), Offset(hx, bottom), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            series.forEach { s -> s.values.getOrNull(h)?.let { v ->
                drawCircle(surface, 6.dp.toPx(), Offset(hx, y(v)))
                drawCircle(s.color, 4.dp.toPx(), Offset(hx, y(v)))
            } }
            val rows = series.mapNotNull { s -> s.values.getOrNull(h)?.let { s.color to "${s.label}: ${format(it)}" } }
            val anchorY = series.firstNotNullOfOrNull { it.values.getOrNull(h) }?.let(::y) ?: (top + bottom) / 2
            tooltip(text, tooltipTitle(h), rows, Offset(hx, anchorY), tipBg, tipInk)
        } else if (grow >= 1f) {
            series.firstOrNull { !it.dashed }?.let { s ->
                s.values.lastOrNull()?.let { v ->
                    val p = Offset(x(s.values.lastIndex), y(v))
                    drawCircle(s.color.copy(alpha = .25f), 9.dp.toPx(), p)
                    drawCircle(s.color, 4.5.dp.toPx(), p)
                }
            }
        }
    }
}

/** Línea pequeña sin ejes para las tarjetas de datos. */
@Composable
internal fun MiniSpark(values: List<Double>, color: Color, modifier: Modifier) {
    val grow = rememberGrow(values.size to values.sum(), 1000)
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max(); val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val points = values.mapIndexed { i, v -> Offset(size.width * i / (values.size - 1), size.height - ((v - lo) / span * size.height * .8 + size.height * .1).toFloat()) }
        val line = smoothPath(points)
        clipRect(right = size.width * grow) {
            drawPath(Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() },
                Brush.verticalGradient(listOf(color.copy(alpha = .25f), Color.Transparent)))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        if (grow >= 1f) drawCircle(color, 3.dp.toPx(), points.last())
    }
}

// ---------------------------------------------------------------- Dona interactiva

/** Dona por categorías: al pasar el mouse sobre una parte se separa y el centro muestra su detalle. */
@Composable
internal fun InteractiveDonut(slices: List<Pair<String, Double>>, colors: List<Color>, modifier: Modifier, selected: Int? = null, onHover: (Int?) -> Unit = {}) {
    val total = slices.sumOf { it.second }
    var hover by remember { mutableStateOf<Int?>(null) }
    val active = hover ?: selected
    val grow = rememberGrow(slices.size to total, 1000)
    val surface = W.surface
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Hand).trackHover(slices, { pos ->
                hover = pos?.let { p ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val dx = p.x - c.x; val dy = p.y - c.y
                    val r = sqrt(dx * dx + dy * dy); val outer = min(size.width, size.height) / 2f
                    if (r < outer * .55f || r > outer + 4) null else {
                        var angle = (atan2(dy, dx) * 180 / PI).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        var acc = 0f
                        slices.indices.firstOrNull { i -> acc += (slices[i].second / total * 360).toFloat(); angle <= acc }
                    }
                }
                onHover(hover)
            }),
        ) {
            if (total <= 0) return@Canvas
            val outer = min(size.width, size.height) / 2f
            val stroke = outer * .30f
            var start = -90f
            slices.forEachIndexed { i, (_, value) ->
                val sweep = (value / total * 360).toFloat() * grow
                val focused = active == i
                val extra = if (focused) 6.dp.toPx() else 0f
                val r = outer - stroke / 2 - 6.dp.toPx() + extra / 2
                val color = if (active == null || focused) colors[i] else colors[i].copy(alpha = .35f)
                drawArc(color, start + .9f, (sweep - 1.8f).coerceAtLeast(.5f), false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2),
                    style = Stroke(stroke + extra, cap = StrokeCap.Butt))
                start += sweep
            }
            drawCircle(surface, outer - stroke - 6.dp.toPx() - 1, center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 30.dp)) {
            val focus = active?.let { slices.getOrNull(it) }
            Text(focus?.first ?: "Total", color = W.ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(moneyGrouped(focus?.second ?: total), color = W.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(if (focus != null) percentLabel(focus.second, total) else "${slices.size} categorías", color = active?.let { colors.getOrNull(it) } ?: W.ink3,
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- Medidor de salud financiera

/** Medio círculo de 0 a 100 con zonas roja, ámbar y verde y una aguja con el puntaje. */
@Composable
internal fun HealthGauge(score: Int, modifier: Modifier) {
    val grow = rememberGrow(score, 1400)
    val track = W.line
    val ink = W.ink
    val text = rememberChartText()
    val muted = W.ink3
    Canvas(modifier) {
        val stroke = 16.dp.toPx()
        val radius = min(size.width / 2f, size.height - 8.dp.toPx()) - stroke / 2
        val c = Offset(size.width / 2f, size.height - 8.dp.toPx())
        val topLeft = Offset(c.x - radius, c.y - radius)
        val arc = Size(radius * 2, radius * 2)
        drawArc(track, 180f, 180f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        val zones = listOf(0f to 40f, 40f to 70f, 70f to 100f)
        val colors = listOf(Color(0xFFF08B7B), Color(0xFFF2C46B), Color(0xFF5ED49B))
        val current = score * grow
        zones.forEachIndexed { i, (a, b) ->
            val end = min(b, current)
            if (end > a) drawArc(colors[i], 180f + a * 1.8f, (end - a) * 1.8f, false, topLeft, arc, style = Stroke(stroke, cap = if (i == 0) StrokeCap.Round else StrokeCap.Butt))
        }
        // Marcas cada 10 puntos.
        for (k in 0..10) {
            val angle = (180 + k * 18) * PI / 180
            val r1 = radius - stroke / 2 - 6.dp.toPx(); val r2 = r1 - if (k % 5 == 0) 7.dp.toPx() else 4.dp.toPx()
            drawLine(muted.copy(alpha = .5f), Offset(c.x + (r1 * cos(angle)).toFloat(), c.y + (r1 * sin(angle)).toFloat()),
                Offset(c.x + (r2 * cos(angle)).toFloat(), c.y + (r2 * sin(angle)).toFloat()), 1.5f)
        }
        val needle = (180 + current * 1.8) * PI / 180
        val tip = Offset(c.x + ((radius - stroke) * .78f * cos(needle)).toFloat(), c.y + ((radius - stroke) * .78f * sin(needle)).toFloat())
        drawLine(ink, c, tip, 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(ink, 7.dp.toPx(), c)
        drawCircle(W.surface, 3.dp.toPx(), c)
        val knob = Offset(c.x + (radius * cos(needle)).toFloat(), c.y + (radius * sin(needle)).toFloat())
        drawCircle(W.surface, stroke / 2 + 2.dp.toPx(), knob)
        drawCircle(healthColor(score), stroke / 2 - 2.dp.toPx(), knob)
        label(text, "0", muted, Offset(c.x - radius, c.y + 14.dp.toPx()), center = true)
        label(text, "100", muted, Offset(c.x + radius, c.y + 14.dp.toPx()), center = true)
    }
}

// ---------------------------------------------------------------- Calendario de calor

/** Cuadrícula tipo calendario: cada cuadro es un día y su color, cuánto gastaste. */
@Composable
internal fun CalendarHeatmap(weeks: List<List<HeatDay>>, modifier: Modifier, today: Int = currentDayIndex()) {
    val peak = weeks.flatten().filterNot { it.future }.maxOfOrNull { it.amount }?.takeIf { it > 0 } ?: 1.0
    var hover by remember { mutableStateOf<HeatDay?>(null) }
    val grow = rememberGrow(weeks.size to peak, 900)
    val text = rememberChartText()
    val brand = W.brand; val empty = W.line.copy(alpha = .6f); val muted = W.ink3; val ink = W.ink
    val leftPad = 30.dp; val topPad = 18.dp
    Canvas(
        modifier.trackHover(weeks, { pos ->
            hover = pos?.let { p ->
                val left = leftPad.toPx(); val top = topPad.toPx()
                val cell = min((size.width - left) / weeks.size, (size.height - top) / 7f)
                val col = ((p.x - left) / cell).toInt(); val row = ((p.y - top) / cell).toInt()
                if (p.x < left || p.y < top || col !in weeks.indices || row !in 0..6) null else weeks[col][row].takeIf { !it.future }
            }
        }),
    ) {
        if (weeks.isEmpty()) return@Canvas
        val left = leftPad.toPx(); val top = topPad.toPx()
        val cell = min((size.width - left) / weeks.size, (size.height - top) / 7f)
        val gap = cell * .16f
        listOf(0 to "Lun", 2 to "Mié", 4 to "Vie", 6 to "Dom").forEach { (row, name) ->
            label(text, name, muted, Offset(0f, top + row * cell + (cell - gap) / 2))
        }
        var lastMonth = -1
        weeks.forEachIndexed { col, week ->
            val first = civilDateFromEpochDays(week.first().epochDay)
            if (first.month != lastMonth) {
                if (col > 0 || first.day <= 7) label(text, reportMonthShort(first.month).replaceFirstChar { it.uppercase() }, muted, Offset(left + col * cell, top - 10.dp.toPx()))
                lastMonth = first.month
            }
            week.forEachIndexed { row, day ->
                val origin = Offset(left + col * cell, top + row * cell)
                val sizeCell = Size(cell - gap, cell - gap)
                val radius = CornerRadius(cell * .22f)
                if (day.future) { drawRoundRect(empty.copy(alpha = .25f), origin, sizeCell, radius); return@forEachIndexed }
                val level = if (day.amount <= 0) 0f else (.25f + .75f * sqrt(day.amount / peak).toFloat()) * grow
                drawRoundRect(if (level == 0f) empty else lerp(empty, brand, level), origin, sizeCell, radius)
                if (day.epochDay == today) drawRoundRect(ink, origin, sizeCell, radius, style = Stroke(1.5.dp.toPx()))
                if (hover?.epochDay == day.epochDay) drawRoundRect(W.lime, origin, sizeCell, radius, style = Stroke(2.dp.toPx()))
            }
        }
        hover?.let { day ->
            val col = weeks.indexOfFirst { w -> w.any { it.epochDay == day.epochDay } }
            val row = weekdayIndex(day.epochDay)
            val date = civilDateFromEpochDays(day.epochDay)
            tooltip(text, "${webWeekdaysLong[row]} ${date.day} de ${currentMonthName(date.month).lowercase()}",
                listOf(brand to if (day.amount > 0) moneyGrouped(day.amount) else "Sin gastos"),
                Offset(left + col * cell + cell / 2, top + row * cell + cell / 2), W.forest, Color.White)
        }
    }
}

@Composable
internal fun CalendarLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Menos", color = W.ink3, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        val empty = W.line.copy(alpha = .6f)
        val brand = W.brand
        listOf(0f, .3f, .55f, .8f, 1f).forEach { level ->
            Canvas(Modifier.padding(horizontal = 2.dp).size(11.dp)) { drawRoundRect(lerp(empty, brand, level), cornerRadius = CornerRadius(3.dp.toPx())) }
        }
        Spacer(Modifier.width(6.dp))
        Text("Más", color = W.ink3, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- Barras por mes

/** Ingresos y gastos de varios meses con la tasa de ahorro encima; al hacer clic se elige el mes. */
@Composable
internal fun MonthBarsChart(months: List<MonthTotals>, selectedKey: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    var hover by remember { mutableStateOf<Int?>(null) }
    val grow = rememberGrow(months.map { it.key }, 1000)
    val text = rememberChartText()
    val incomeColor = W.brand; val expenseColor = W.negative; val grid = W.line; val axis = W.ink3; val rateColor = W.warning; val surface = W.surface
    val leftPad = 48.dp
    Canvas(
        modifier.pointerHoverIcon(PointerIcon.Hand).trackHover(months, { pos ->
            hover = pos?.let { p -> val left = leftPad.toPx(); if (p.x < left) null else ((p.x - left) / ((size.width - left) / months.size)).toInt().coerceIn(0, months.lastIndex) }
        }, onClick = { p ->
            val left = leftPad.toPx()
            if (p.x >= left && months.isNotEmpty()) onSelect(months[((p.x - left) / ((size.width - left) / months.size)).toInt().coerceIn(0, months.lastIndex)].key)
        }),
    ) {
        if (months.isEmpty()) return@Canvas
        val left = leftPad.toPx(); val bottom = size.height - 24.dp.toPx(); val top = 14.dp.toPx()
        val topValue = niceTop(max(months.maxOf { max(it.income, it.expense) }, 1.0))
        for (i in 0..4) {
            val y = bottom - (bottom - top) * i / 4
            drawLine(grid, Offset(left, y), Offset(size.width, y), 1f, pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            label(text, shortMoney(topValue * i / 4), axis, Offset(left - 8.dp.toPx(), y), alignRight = true)
        }
        val slot = (size.width - left) / months.size
        val barW = min(slot * .24f, 24.dp.toPx())
        val ratePoints = mutableListOf<Offset>()
        months.forEachIndexed { i, m ->
            val cx = left + slot * i + slot / 2
            val selected = m.key == selectedKey
            if (selected || hover == i) drawRoundRect(if (selected) W.brand.copy(alpha = .08f) else grid.copy(alpha = .5f),
                Offset(cx - slot / 2 + 4.dp.toPx(), top - 6.dp.toPx()), Size(slot - 8.dp.toPx(), bottom - top + 6.dp.toPx()), CornerRadius(12.dp.toPx()))
            listOf(m.income to incomeColor, m.expense to expenseColor).forEachIndexed { j, (value, color) ->
                val h = ((bottom - top) * value / topValue).toFloat() * grow
                val x = if (j == 0) cx - barW - 2.dp.toPx() else cx + 2.dp.toPx()
                if (h > 0) drawRoundRect(Brush.verticalGradient(listOf(color, color.copy(alpha = .65f)), bottom - h, bottom),
                    Offset(x, bottom - h), Size(barW, h), CornerRadius(6.dp.toPx()))
            }
            label(text, webMonthShort(m.key), if (selected) W.ink else axis, Offset(cx, bottom + 13.dp.toPx()), center = true, bold = selected)
            if (m.income > 0) ratePoints += Offset(cx, (bottom - (bottom - top) * m.savingRate.coerceIn(0.0, 1.0)).toFloat())
        }
        if (ratePoints.size > 1) clipRect(right = left + (size.width - left) * grow) {
            drawPath(smoothPath(ratePoints), rateColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
            ratePoints.forEach { drawCircle(surface, 5.dp.toPx(), it); drawCircle(rateColor, 3.5.dp.toPx(), it) }
        }
        hover?.let { i ->
            val m = months[i]
            tooltip(text, webMonthLabel(m.key), listOf(
                incomeColor to "Ingresos: ${moneyGrouped(m.income)}",
                expenseColor to "Gastos: ${moneyGrouped(m.expense)}",
                rateColor to "Ahorro: ${if (m.income > 0) "${(m.savingRate * 100).roundToInt()}%" else "—"}",
            ), Offset(left + slot * i + slot / 2, top + (bottom - top) / 3), W.forest, Color.White)
        }
    }
}

// ---------------------------------------------------------------- Barras simples

/** Barras con etiquetas y una línea de límite opcional (por ejemplo, el límite diario). */
@Composable
internal fun SimpleBars(values: List<Double>, labels: List<String>, modifier: Modifier, highlight: Int? = null, limit: Double? = null, tooltipTitle: (Int) -> String = { labels[it] }) {
    var hover by remember { mutableStateOf<Int?>(null) }
    val grow = rememberGrow(values.size to values.sum(), 900)
    val text = rememberChartText()
    val brand = W.brand; val over = W.negative; val axis = W.ink3; val lime = W.lime; val track = W.line.copy(alpha = .5f)
    Canvas(modifier.trackHover(values, { pos -> hover = pos?.let { p -> (p.x / (size.width / values.size)).toInt().coerceIn(0, values.lastIndex) } })) {
        if (values.isEmpty()) return@Canvas
        val bottom = size.height - 22.dp.toPx(); val top = 16.dp.toPx()
        val topValue = max(values.maxOrNull() ?: 0.0, limit ?: 0.0).coerceAtLeast(1.0) * 1.12
        val slot = size.width / values.size
        val barW = min(slot * .56f, 34.dp.toPx())
        values.forEachIndexed { i, v ->
            val x = slot * i + (slot - barW) / 2
            drawRoundRect(track, Offset(x, top), Size(barW, bottom - top), CornerRadius(8.dp.toPx()))
            val h = ((bottom - top) * v / topValue).toFloat() * grow
            val color = when { limit != null && v > limit -> over; i == highlight -> lime; else -> brand }
            if (h > 0) drawRoundRect(color.copy(alpha = if (hover == null || hover == i) 1f else .55f), Offset(x, bottom - h), Size(barW, h), CornerRadius(8.dp.toPx()))
            label(text, labels[i], if (i == highlight) W.ink else axis, Offset(slot * i + slot / 2, bottom + 12.dp.toPx()), center = true, bold = i == highlight)
        }
        limit?.let {
            val y = (bottom - (bottom - top) * it / topValue).toFloat()
            drawLine(over.copy(alpha = .8f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
            label(text, "Límite ${shortMoney(it)}", over, Offset(size.width, y - 8.dp.toPx()), alignRight = true, bold = true)
        }
        hover?.let { i -> tooltip(text, tooltipTitle(i), listOf(brand to moneyGrouped(values[i])), Offset(slot * i + slot / 2, top + 20.dp.toPx()), W.forest, Color.White) }
    }
}

// ---------------------------------------------------------------- Radial por día de la semana

/** Pétalos por día de la semana: mientras más largo, más gastas ese día. */
@Composable
internal fun WeekdayRadial(totals: DoubleArray, modifier: Modifier) {
    var hover by remember { mutableStateOf<Int?>(null) }
    val peak = totals.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val busiest = totals.indices.maxByOrNull { totals[it] } ?: 0
    val grow = rememberGrow(totals.sum(), 1100)
    val text = rememberChartText()
    val brand = W.brand; val ring = W.line; val axis = W.ink3; val lime = W.lime
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().trackHover(totals.sum(), { pos ->
            hover = pos?.let { p ->
                val dx = p.x - size.width / 2f; val dy = p.y - size.height / 2f
                if (sqrt(dx * dx + dy * dy) < 26.dp.toPx()) null else {
                    var angle = (atan2(dy, dx) * 180 / PI).toFloat() + 90f + 360f / 14
                    while (angle < 0) angle += 360f
                    ((angle % 360f) / (360f / 7)).toInt().coerceIn(0, 6)
                }
            }
        })) {
            val outer = min(size.width, size.height) / 2f - 22.dp.toPx()
            val inner = 26.dp.toPx()
            for (k in 1..3) drawCircle(ring, inner + (outer - inner) * k / 3, center, style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))))
            val wedge = 360f / 7
            for (i in 0..6) {
                val r = inner + (outer - inner) * sqrt(totals[i] / peak).toFloat() * grow
                val start = -90f - wedge / 2 + i * wedge + 2.5f
                val color = when { hover == i -> lime; i == busiest && totals[i] > 0 -> brand; else -> brand.copy(alpha = .42f) }
                if (totals[i] > 0) drawArc(color, start, wedge - 5f, true, Offset(center.x - r, center.y - r), Size(r * 2, r * 2))
                val mid = (start + (wedge - 5f) / 2) * PI / 180
                label(text, webWeekdaysShort[i], if (i == busiest) W.ink else axis,
                    Offset(center.x + ((outer + 12.dp.toPx()) * cos(mid)).toFloat(), center.y + ((outer + 12.dp.toPx()) * sin(mid)).toFloat()), center = true, bold = i == busiest)
            }
            drawCircle(W.surface, inner, center)
        }
        val focus = hover ?: busiest
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(webWeekdaysShort[focus], color = W.ink3, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(shortMoney(totals[focus]), color = W.ink, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ---------------------------------------------------------------- Mapa de categorías (treemap)

/** Rectángulos proporcionales a lo que gastas en cada categoría. */
@Composable
internal fun CategoryTreemap(groups: List<Pair<String, Double>>, modifier: Modifier) {
    var hover by remember { mutableStateOf<Int?>(null) }
    val total = groups.sumOf { it.second }
    val grow = rememberGrow(groups.size to total, 900)
    val text = rememberChartText()
    var layout by remember { mutableStateOf<List<TreeRect>>(emptyList()) }
    Canvas(modifier.pointerHoverIcon(PointerIcon.Hand).trackHover(groups, { pos ->
        hover = pos?.let { p -> layout.firstOrNull { p.x >= it.x && p.x <= it.x + it.width && p.y >= it.y && p.y <= it.y + it.height }?.index }
    })) {
        val rects = treemapLayout(groups.map { it.second }, size.width, size.height)
        if (rects != layout) layout = rects
        val gap = 3.dp.toPx()
        rects.forEach { r ->
            val (name, value) = groups[r.index]
            val color = categoryColor(name, r.index)
            val focused = hover == r.index
            val scale = .9f + .1f * grow
            val w = (r.width - gap) * scale; val h = (r.height - gap) * scale
            val origin = Offset(r.x + gap / 2 + (r.width - gap - w) / 2, r.y + gap / 2 + (r.height - gap - h) / 2)
            drawRoundRect(
                Brush.linearGradient(listOf(color, lerp(color, Color.Black, .18f)), origin, Offset(origin.x + w, origin.y + h)),
                origin, Size(w, h), CornerRadius(12.dp.toPx()), alpha = (if (hover == null || focused) 1f else .45f) * grow,
            )
            if (w > 70.dp.toPx() && h > 46.dp.toPx()) {
                label(text, name, Color.White, Offset(origin.x + 12.dp.toPx(), origin.y + 18.dp.toPx()), size = 13, bold = true)
                label(text, "${percentLabel(value, total)} · ${shortMoney(value)}", Color.White.copy(alpha = .85f), Offset(origin.x + 12.dp.toPx(), origin.y + 36.dp.toPx()), size = 11)
            } else if (w > 34.dp.toPx() && h > 24.dp.toPx()) {
                label(text, percentLabel(value, total), Color.White, Offset(origin.x + 8.dp.toPx(), origin.y + 14.dp.toPx()), size = 10, bold = true)
            }
        }
        hover?.let { i ->
            val r = rects.firstOrNull { it.index == i } ?: return@let
            val (name, value) = groups[i]
            tooltip(text, name, listOf(categoryColor(name, i) to moneyGrouped(value), null to "${percentLabel(value, total)} de tus gastos"),
                Offset(r.x + r.width / 2, r.y + r.height / 2), W.forest, Color.White)
        }
    }
}

/** Texto de ayuda pequeño bajo un gráfico. */
@Composable
internal fun ChartNote(text: String) {
    Text(text, color = W.ink3, fontSize = 12.sp, textAlign = TextAlign.Start)
}
