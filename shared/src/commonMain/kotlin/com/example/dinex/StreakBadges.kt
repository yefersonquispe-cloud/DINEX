package com.example.dinex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Material de cada medalla: brillo, color base, sombra y color del emblema. */
private class MedalMaterial(val light: Color, val base: Color, val dark: Color, val emblem: Color, val ribbon: Color)

private fun medalMaterial(level: StreakLevel): MedalMaterial = when (level.name) {
    "Plata" -> MedalMaterial(Color(0xFFF7F9FB), Color(0xFFBCC7D3), Color(0xFF6D7A89), Color(0xFF2B3542), Color(0xFF4A6FA5))
    "Oro" -> MedalMaterial(Color(0xFFFFEDB0), Color(0xFFF0B429), Color(0xFF9A6708), Color(0xFF6B4504), Color(0xFFC0392B))
    "Platino" -> MedalMaterial(Color(0xFFEFFCFF), Color(0xFFA6DCEC), Color(0xFF4F8EA3), Color(0xFF235566), Color(0xFF1F4E79))
    "Diamante" -> MedalMaterial(Color(0xFFE6F3FF), Color(0xFF6FB4FF), Color(0xFF2559A8), Color(0xFFFFFFFF), Color(0xFF1B3A6B))
    "Esmeralda" -> MedalMaterial(Color(0xFFCDF8E1), Color(0xFF34C77E), Color(0xFF0E6E3E), Color(0xFFFFFFFF), Color(0xFF0B4D2C))
    "Rubí" -> MedalMaterial(Color(0xFFFFC6CF), Color(0xFFF0405D), Color(0xFF8E1028), Color(0xFFFFFFFF), Color(0xFF5E0A1A))
    "Amatista" -> MedalMaterial(Color(0xFFEDD0FF), Color(0xFFA855F7), Color(0xFF581C99), Color(0xFFFFFFFF), Color(0xFF3B1066))
    "Obsidiana" -> MedalMaterial(Color(0xFF6E737D), Color(0xFF2B2E35), Color(0xFF0D0E11), Color(0xFFFF7A45), Color(0xFFFF6B36))
    "Perla" -> MedalMaterial(Color(0xFFFFFFFF), Color(0xFFEFE6E0), Color(0xFFB3A398), Color(0xFF5C463A), Color(0xFFC9A9A6))
    "Madera" -> MedalMaterial(Color(0xFFEBC594), Color(0xFFC08549), Color(0xFF6E431C), Color(0xFFFFF4E6), Color(0xFF4E2E12))
    "Piedra" -> MedalMaterial(Color(0xFFD6DBE1), Color(0xFF959FAB), Color(0xFF515964), Color(0xFFFFFFFF), Color(0xFF3B424B))
    "Acero" -> MedalMaterial(Color(0xFFF3F6FA), Color(0xFFC6D0DC), Color(0xFF5E6B7C), Color(0xFF1E293B), Color(0xFF1E293B))
    "Cristal" -> MedalMaterial(Color(0xFFDCFCFF), Color(0xFF2ED8F2), Color(0xFF0A7F99), Color(0xFFFFFFFF), Color(0xFF075E70))
    "Leyenda" -> MedalMaterial(Color(0xFFFFF3BF), Color(0xFFFFC93C), Color(0xFFA86F00), Color(0xFF6B4300), Color(0xFF7A1F1F))
    else -> MedalMaterial(Color(0xFFF5B98F), Color(0xFFC97843), Color(0xFF773C19), Color(0xFFFFF1E6), Color(0xFF8B2E1D)) // Cobre
}

private fun Color.grayscale(): Color {
    val g = red * .3f + green * .59f + blue * .11f
    return Color(g, g, g, alpha)
}

/** Forma de la medalla según el grupo del nivel: metales, gemas, elementos y Leyenda. */
private enum class MedalShape { HEXAGON, GEM, SHIELD, STAR }

private fun medalShape(level: StreakLevel): MedalShape = when (streakLevels.indexOf(level)) {
    in 0..3 -> MedalShape.HEXAGON      // Cobre, Plata, Oro, Platino
    in 4..7 -> MedalShape.GEM          // Diamante, Esmeralda, Rubí, Amatista
    in 8..13 -> MedalShape.SHIELD      // Obsidiana, Perla, Madera, Piedra, Acero, Cristal
    else -> MedalShape.STAR            // Leyenda
}

/**
 * Medalla de racha dibujada con vectores, al estilo de un logro: forma facetada con bisel
 * iluminado desde arriba a la izquierda, cara con degradado y, al centro, los días del nivel
 * (o una llama en tamaños pequeños; corona en Leyenda). Fondo transparente y nítida a cualquier
 * tamaño en Android, iOS y la web. Si aún no se gana, se ve en gris y apagada.
 */
@Composable
internal fun StreakBadge(level: StreakLevel, size: Dp, locked: Boolean = false, modifier: Modifier = Modifier) {
    val material = remember(level) { medalMaterial(level) }
    val shape = remember(level) { medalShape(level) }
    val measurer = rememberTextMeasurer()
    val showText = size >= 40.dp
    Canvas(modifier.size(size).graphicsLayer { alpha = if (locked) .5f else 1f }) {
        fun c(color: Color) = if (locked) color.grayscale() else color
        drawAchievement(material, shape, level, if (showText) measurer else null, ::c)
    }
}

/** Vértices de la forma, centrados, con radio r. */
private fun medalOutline(shape: MedalShape, center: Offset, r: Float): List<Offset> {
    fun polygon(sides: Int, rotationDeg: Float, radius: (Int) -> Float = { r }) = (0 until sides).map { i ->
        val angle = (rotationDeg + i * 360f / sides) * PI / 180
        center + Offset((cos(angle) * radius(i)).toFloat(), (sin(angle) * radius(i)).toFloat())
    }
    return when (shape) {
        MedalShape.HEXAGON -> polygon(6, -90f)
        MedalShape.GEM -> polygon(8, -112.5f)
        MedalShape.SHIELD -> listOf(
            center + Offset(-r * .86f, -r * .78f), center + Offset(0f, -r * .98f), center + Offset(r * .86f, -r * .78f),
            center + Offset(r * .86f, r * .12f), center + Offset(0f, r * 1.0f), center + Offset(-r * .86f, r * .12f),
        )
        MedalShape.STAR -> polygon(24, -90f) { if (it % 2 == 0) r else r * .84f }
    }
}

private fun DrawScope.drawAchievement(m: MedalMaterial, shape: MedalShape, level: StreakLevel, measurer: TextMeasurer?, c: (Color) -> Color) {
    val w = size.width
    val center = Offset(w / 2, w / 2)
    val r = w * .46f
    val outer = medalOutline(shape, center, r)
    val inner = medalOutline(shape, center, r * .76f)
    val light = -2.36 // luz desde arriba a la izquierda

    // Sombra suave bajo la medalla.
    drawOval(c(Color.Black.copy(alpha = .16f)), center + Offset(-r * .7f, r * .86f), Size(r * 1.4f, r * .22f))

    // Bisel facetado: cada lado se ilumina según hacia dónde mira.
    outer.indices.forEach { i ->
        val j = (i + 1) % outer.size
        val mid = (outer[i] + outer[j]) / 2f - center
        val shade = ((cos(kotlin.math.atan2(mid.y, mid.x) - light) + 1) / 2).toFloat()
        val facet = Path().apply {
            moveTo(outer[i].x, outer[i].y); lineTo(outer[j].x, outer[j].y); lineTo(inner[j].x, inner[j].y); lineTo(inner[i].x, inner[i].y); close()
        }
        drawPath(facet, c(lerp(m.dark, m.light, shade)))
    }
    // Cara: facetada en las gemas, lisa con degradado en el resto.
    if (shape == MedalShape.GEM) {
        inner.indices.forEach { i ->
            val j = (i + 1) % inner.size
            val mid = (inner[i] + inner[j]) / 2f - center
            val shade = ((cos(kotlin.math.atan2(mid.y, mid.x) - light + PI) + 1) / 2).toFloat()
            val facet = Path().apply { moveTo(center.x, center.y); lineTo(inner[i].x, inner[i].y); lineTo(inner[j].x, inner[j].y); close() }
            drawPath(facet, c(lerp(lerp(m.base, m.dark, .45f), m.base, shade)))
        }
    } else {
        val face = Path().apply { moveTo(inner[0].x, inner[0].y); inner.drop(1).forEach { lineTo(it.x, it.y) }; close() }
        drawPath(face, Brush.linearGradient(listOf(c(lerp(m.base, m.dark, .5f)), c(m.base), c(lerp(m.base, m.light, .35f))),
            center + Offset(r, r), center - Offset(r, r)))
    }
    // Filo de luz entre bisel y cara.
    val edge = Path().apply { moveTo(inner[0].x, inner[0].y); inner.drop(1).forEach { lineTo(it.x, it.y) }; close() }
    drawPath(edge, c(m.light.copy(alpha = .45f)), style = Stroke(w * .012f))

    // Emblema de ahorro propio de cada nivel.
    val box = r * 1.08f
    translate(center.x - box / 2, center.y - box / 2) {
        drawSavingsEmblem(streakLevels.indexOf(level).coerceAtLeast(0), box, c(m.emblem), c(lerp(m.base, m.dark, .55f)), measurer)
    }

    // Reflejo diagonal sobre la cara.
    val gloss = Path().apply {
        moveTo(inner[0].x, inner[0].y); inner.drop(1).forEach { lineTo(it.x, it.y) }; close()
    }
    clipPathSafe(gloss) {
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .28f), Color.Transparent), center - Offset(r, r), center), center - Offset(r, r), Size(r * 2, r * 2))
    }
}

private inline fun DrawScope.clipPathSafe(path: Path, block: DrawScope.() -> Unit) =
    drawContext.canvas.let { canvas ->
        canvas.save(); canvas.clipPath(path); block(); canvas.restore()
    }

/**
 * Emblemas de ahorro, uno por nivel, que cuentan una historia que crece:
 * moneda, monedas, billete, billetera, alcancía, planta, bolsa, cofre, caja fuerte,
 * gráfico, casa, lingotes, bóveda, diamante y corona.
 */
private fun DrawScope.drawSavingsEmblem(index: Int, box: Float, ink: Color, detail: Color, measurer: TextMeasurer?) {
    fun p(x: Float, y: Float) = Offset(box * x, box * y)
    fun sz(x: Float, y: Float) = Size(box * x, box * y)
    fun corner(v: Float) = androidx.compose.ui.geometry.CornerRadius(box * v)
    fun path(points: List<Offset>) = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
    fun sol(center: Offset, size: Float, color: Color) {
        if (measurer == null) return
        val text = measurer.measure("S/", TextStyle(color = color, fontSize = (box * size).toSp(), fontWeight = FontWeight.Black))
        drawText(text, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f))
    }
    fun coin(center: Offset, radius: Float) {
        drawCircle(ink, box * radius, center)
        drawCircle(detail, box * radius * .78f, center, style = Stroke(box * radius * .12f))
    }
    when (index) {
        0 -> { coin(p(.5f, .5f), .34f); sol(p(.5f, .5f), .26f, detail) }                       // Cobre: moneda
        1 -> {                                                                                      // Plata: monedas apiladas
            listOf(.74f, .62f, .5f).forEach { y -> drawRoundRect(ink, p(.2f, y), sz(.6f, .1f), corner(.05f)); drawRoundRect(detail, p(.2f, y + .08f), sz(.6f, .02f)) }
            coin(p(.5f, .32f), .2f)
        }
        2 -> {                                                                                      // Oro: billete
            drawRoundRect(ink, p(.1f, .28f), sz(.8f, .46f), corner(.06f))
            drawRoundRect(detail, p(.15f, .33f), sz(.7f, .36f), corner(.04f), style = Stroke(box * .025f))
            drawCircle(detail, box * .1f, p(.5f, .51f))
            listOf(p(.22f, .4f), p(.78f, .62f)).forEach { drawCircle(detail, box * .03f, it) }
        }
        3 -> {                                                                                      // Platino: billetera
            drawRoundRect(detail, p(.24f, .2f), sz(.46f, .3f), corner(.04f))                         // billete asomando
            drawRoundRect(ink, p(.14f, .32f), sz(.72f, .48f), corner(.09f))
            drawRoundRect(detail, p(.56f, .46f), sz(.3f, .2f), corner(.06f))
            drawCircle(ink, box * .04f, p(.66f, .56f))
        }
        4 -> {                                                                                      // Diamante: alcancía
            drawOval(ink, p(.16f, .34f), sz(.66f, .46f))
            drawPath(path(listOf(p(.3f, .38f), p(.36f, .2f), p(.46f, .35f))), ink)                          // oreja
            drawRoundRect(ink, p(.76f, .5f), sz(.12f, .14f), corner(.04f))                          // hocico
            listOf(.28f, .58f).forEach { drawRoundRect(ink, p(it, .72f), sz(.1f, .14f), corner(.03f)) } // patas
            drawRoundRect(detail, p(.4f, .4f), sz(.2f, .04f), corner(.02f))                         // ranura
            drawCircle(detail, box * .035f, p(.66f, .5f))                                          // ojo
            coin(p(.5f, .16f), .1f)
        }
        5 -> {                                                                                      // Esmeralda: planta que crece sobre monedas
            listOf(.76f, .66f).forEach { y -> drawRoundRect(ink, p(.26f, y), sz(.48f, .09f), corner(.045f)) }
            drawRect(ink, p(.47f, .3f), sz(.06f, .38f))
            drawOval(ink, p(.18f, .28f), sz(.32f, .18f))
            drawOval(ink, p(.5f, .16f), sz(.32f, .18f))
            drawLine(detail, p(.24f, .37f), p(.48f, .37f), box * .02f)
            drawLine(detail, p(.52f, .25f), p(.76f, .25f), box * .02f)
        }
        6 -> {                                                                                      // Rubí: bolsa de dinero
            val bag = Path().apply {
                moveTo(box * .38f, box * .3f); lineTo(box * .62f, box * .3f)
                cubicTo(box * .9f, box * .45f, box * .9f, box * .86f, box * .5f, box * .86f)
                cubicTo(box * .1f, box * .86f, box * .1f, box * .45f, box * .38f, box * .3f); close()
            }
            drawPath(bag, ink)
            drawPath(path(listOf(p(.36f, .12f), p(.64f, .12f), p(.56f, .28f), p(.44f, .28f))), ink)
            drawRoundRect(detail, p(.36f, .27f), sz(.28f, .05f), corner(.025f))
            sol(p(.5f, .6f), .24f, detail)
        }
        7 -> {                                                                                      // Amatista: cofre
            drawRoundRect(ink, p(.14f, .26f), sz(.72f, .22f), corner(.1f))
            drawRect(ink, p(.14f, .44f), sz(.72f, .34f))
            drawRect(detail, p(.14f, .44f), sz(.72f, .035f))
            drawRoundRect(detail, p(.44f, .4f), sz(.12f, .16f), corner(.03f))
            listOf(.26f, .7f).forEach { drawRect(detail, p(it, .26f), sz(.04f, .52f)) }
        }
        8 -> {                                                                                      // Obsidiana: caja fuerte
            drawRoundRect(ink, p(.16f, .18f), sz(.68f, .62f), corner(.08f))
            drawCircle(detail, box * .16f, p(.48f, .49f), style = Stroke(box * .04f))
            drawLine(detail, p(.48f, .4f), p(.48f, .49f), box * .035f, StrokeCap.Round)
            drawRoundRect(detail, p(.74f, .38f), sz(.05f, .22f), corner(.02f))
            listOf(.24f, .68f).forEach { drawRect(ink, p(it, .8f), sz(.08f, .06f)) }
        }
        9 -> {                                                                                      // Perla: gráfico que sube
            listOf(Triple(.18f, .58f, .22f), Triple(.38f, .46f, .34f), Triple(.58f, .32f, .48f)).forEach { (x, y, h) ->
                drawRoundRect(ink, p(x, y), sz(.16f, h), corner(.03f))
            }
            drawLine(detail, p(.16f, .5f), p(.48f, .32f), box * .045f, StrokeCap.Round)
            drawLine(detail, p(.48f, .32f), p(.8f, .14f), box * .045f, StrokeCap.Round)
            drawPath(path(listOf(p(.86f, .1f), p(.72f, .12f), p(.82f, .24f))), detail)
        }
        10 -> {                                                                                     // Madera: casa (la gran meta)
            drawPath(path(listOf(p(.5f, .14f), p(.88f, .46f), p(.12f, .46f))), ink)
            drawRect(ink, p(.2f, .44f), sz(.6f, .38f))
            drawRoundRect(detail, p(.42f, .56f), sz(.16f, .26f), corner(.03f))
            drawRect(detail, p(.26f, .52f), sz(.11f, .1f)); drawRect(detail, p(.63f, .52f), sz(.11f, .1f))
        }
        11 -> {                                                                                     // Piedra: lingotes
            fun bar(x: Float, y: Float) {
                drawPath(path(listOf(p(x + .06f, y), p(x + .28f, y), p(x + .34f, y + .16f), p(x, y + .16f))), ink)
                drawLine(detail, p(x + .06f, y + .03f), p(x + .28f, y + .03f), box * .02f)
            }
            bar(.14f, .6f); bar(.52f, .6f); bar(.33f, .42f)
            drawCircle(ink, box * .05f, p(.5f, .26f)); drawCircle(ink, box * .03f, p(.66f, .2f))
        }
        12 -> {                                                                                     // Acero: puerta de bóveda
            drawCircle(ink, box * .36f, p(.5f, .5f))
            drawCircle(detail, box * .26f, p(.5f, .5f), style = Stroke(box * .035f))
            repeat(6) { i ->
                val angle = i * PI / 3
                val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                drawLine(detail, p(.5f, .5f) + dir * (box * .06f), p(.5f, .5f) + dir * (box * .2f), box * .035f, StrokeCap.Round)
            }
            drawCircle(detail, box * .06f, p(.5f, .5f))
        }
        13 -> {                                                                                     // Cristal: diamante
            drawPath(path(listOf(p(.28f, .24f), p(.72f, .24f), p(.88f, .4f), p(.5f, .84f), p(.12f, .4f))), ink)
            drawLine(detail, p(.12f, .4f), p(.88f, .4f), box * .025f)
            drawPath(path(listOf(p(.36f, .4f), p(.5f, .84f), p(.64f, .4f))), detail.copy(alpha = .35f))
            drawLine(detail, p(.36f, .24f), p(.36f, .4f), box * .02f); drawLine(detail, p(.64f, .24f), p(.64f, .4f), box * .02f)
        }
        else -> drawCrown(box, ink, detail)                                                         // Leyenda: corona
    }
}

private fun DrawScope.drawFlame(box: Float, color: Color, inner: Color) {
    fun p(x: Float, y: Float) = Offset(box * (.18f + x * .64f), box * (.1f + y * .8f))
    val outer = Path().apply {
        val a = p(.5f, 0f); moveTo(a.x, a.y)
        cubic(p(.58f, .22f), p(.92f, .32f), p(.92f, .64f))
        cubic(p(.92f, .86f), p(.72f, 1f), p(.5f, 1f))
        cubic(p(.28f, 1f), p(.08f, .86f), p(.08f, .64f))
        cubic(p(.08f, .46f), p(.2f, .36f), p(.3f, .26f))
        cubic(p(.3f, .42f), p(.38f, .5f), p(.46f, .52f))
        cubic(p(.4f, .36f), p(.42f, .16f), p(.5f, 0f))
        close()
    }
    drawPath(outer, color)
    val core = Path().apply {
        val a = p(.5f, .48f); moveTo(a.x, a.y)
        cubic(p(.6f, .62f), p(.72f, .7f), p(.72f, .82f))
        cubic(p(.72f, .94f), p(.62f, 1f), p(.5f, 1f))
        cubic(p(.38f, 1f), p(.28f, .94f), p(.28f, .82f))
        cubic(p(.28f, .7f), p(.42f, .62f), p(.5f, .48f))
        close()
    }
    drawPath(core, inner)
}

private fun Path.cubic(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)

private fun DrawScope.drawCrown(box: Float, color: Color, jewel: Color) {
    fun p(x: Float, y: Float) = Offset(box * (.12f + x * .76f), box * (.18f + y * .66f))
    val crown = Path().apply {
        val a = p(0f, .85f); moveTo(a.x, a.y)
        listOf(p(0f, .2f), p(.27f, .5f), p(.5f, 0f), p(.73f, .5f), p(1f, .2f), p(1f, .85f)).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(crown, color)
    val base = p(0f, .9f)
    drawRect(color, base, Size(box * .76f, box * .1f))
    listOf(p(0f, .2f), p(.5f, 0f), p(1f, .2f)).forEach { drawCircle(color, box * .055f, it) }
    drawCircle(jewel, box * .06f, p(.5f, .62f))
}

/** Alerta al subir de nivel: la insignia aparece flotando con brillo, rayos y destellos. */
@Composable
internal fun StreakLevelDialog(level: StreakLevel, streak: Int, onDismiss: () -> Unit) {
    val index = streakLevels.indexOf(level).coerceAtLeast(0)
    val next = streakLevels.getOrNull(index + 1)
    val accent = level.color
    val enter = remember { Animatable(0f) }
    LaunchedEffect(level) { enter.animateTo(1f, spring(dampingRatio = .48f, stiffness = Spring.StiffnessLow)) }
    val loop = rememberInfiniteTransition(label = "insignia")
    val float by loop.animateFloat(-9f, 9f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "flotar")
    val tilt by loop.animateFloat(-4f, 4f, infiniteRepeatable(tween(2300), RepeatMode.Reverse), label = "inclinar")
    val glow by loop.animateFloat(.55f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "brillo")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "rayos")
    val twinkle by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "destellos")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 26.dp).fillMaxWidth()
                .graphicsLayer { val s = .82f + .18f * enter.value; scaleX = s; scaleY = s; alpha = enter.value.coerceIn(0f, 1f) },
            shape = RoundedCornerShape(34.dp),
            color = Color(0xFF0B1510),
            border = BorderStroke(2.dp, accent.copy(alpha = .75f)),
            shadowElevation = 30.dp,
        ) {
            Box(Modifier.fillMaxWidth().background(Brush.radialGradient(listOf(accent.copy(alpha = .30f), Color.Transparent), radius = 900f))) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("¡SUBISTE DE NIVEL!", color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Ganaste una nueva medalla de ahorro", color = Color.White.copy(alpha = .72f), fontSize = 12.sp, textAlign = TextAlign.Center)

                    // Insignia flotante con rayos giratorios, halo y destellos alrededor.
                    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().rotate(spin)) {
                            val rays = 14
                            repeat(rays) { i ->
                                drawArc(accent.copy(alpha = .16f * glow), startAngle = i * 360f / rays, sweepAngle = 9f, useCenter = true)
                            }
                            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .55f * glow), Color.Transparent)), radius = size.minDimension * .42f)
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2, size.height / 2)
                            repeat(8) { i ->
                                val phase = (twinkle + i / 8f) % 1f
                                val angle = i * (2 * PI / 8) + twinkle * .6
                                val r = size.minDimension * (.36f + .1f * phase)
                                val p = Offset(c.x + (cos(angle) * r).toFloat(), c.y + (sin(angle) * r).toFloat())
                                drawCircle(Color.White.copy(alpha = (1f - phase) * .9f), radius = 3.5f + 5f * (1f - phase), center = p)
                            }
                        }
                        StreakBadge(
                            level, 190.dp,
                            modifier = Modifier.offset(y = float.dp).graphicsLayer {
                                rotationZ = tilt
                                val pop = .4f + .6f * enter.value
                                scaleX = pop; scaleY = pop
                            },
                        )
                    }

                    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .18f), border = BorderStroke(1.dp, accent.copy(alpha = .6f))) {
                        Text("MEDALLA DE AHORRO · NIVEL ${index + 1} DE ${streakLevels.size}", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(level.name.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = 1.sp)
                    Text("$streak ${if (streak == 1) "día" else "días"} ahorrando seguido", color = Color.White.copy(alpha = .82f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))

                    // Camino: nivel anterior, actual y siguiente.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        streakLevels.getOrNull(index - 1)?.let { StreakBadge(it, 38.dp) }
                        StreakBadge(level, 50.dp)
                        if (next != null) {
                            Icon(DinexNavigationIcons.Chevron, null, tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(16.dp))
                            StreakBadge(next, 38.dp, locked = true)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (next != null) "Siguiente medalla: ${next.name} en ${next.startsAt - streak} ${if (next.startsAt - streak == 1) "día" else "días"}"
                        else "¡Llegaste al nivel máximo! Eres una leyenda del ahorro.",
                        color = Color.White.copy(alpha = .65f), fontSize = 11.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Seguir mi racha", color = Color(0xFF0A0C10), fontWeight = FontWeight.Black, fontSize = 15.sp) }
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
    }
}
