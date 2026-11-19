package com.example.dinex

import androidx.compose.ui.graphics.toArgb
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Reporte PDF hecho a mano (sin librerías) para que funcione igual en Android, iOS y la web.
// Usa Helvetica con WinAnsiEncoding y mide cada texto con las métricas reales de la fuente,
// así nada se encima ni se sale de su columna.

private const val PDF_PAGE_W = 595.0
private const val PDF_PAGE_H = 842.0
private const val PDF_MARGIN = 40.0
private const val PDF_CONTENT_W = PDF_PAGE_W - 2 * PDF_MARGIN
private const val PDF_RIGHT = PDF_PAGE_W - PDF_MARGIN
private const val PDF_BOTTOM_LIMIT = 64.0

private class Rgb(hex: Int) {
    val op = listOf(hex shr 16, hex shr 8, hex).joinToString(" ") { fixed((it and 255) / 255.0, 3) }
}

private val PdfWhite = Rgb(0xFFFFFF)
private val PdfNight = Rgb(0x183326)
private val PdfMuted = Rgb(0x5B7062)
private val PdfBrand = Rgb(0x176B43)
private val PdfBrandDeep = Rgb(0x10502F)
private val PdfBrandSoft = Rgb(0xBFE3CC)
private val PdfBrandGlow1 = Rgb(0x16613C)
private val PdfBrandGlow2 = Rgb(0x135836)
private val PdfChip = Rgb(0x1F7A4D)
private val PdfMint = Rgb(0xE8F4EC)
private val PdfMintDeep = Rgb(0x115B38)
private val PdfExpense = Rgb(0xB84754)
private val PdfLine = Rgb(0xD9E6DC)
private val PdfGrid = Rgb(0xE6EEE8)
private val PdfTrack = Rgb(0xEEF4EF)
private val PdfZebra = Rgb(0xF6FAF7)
private val PdfCategoryPalette = listOf(0x176B43, 0xE66678, 0x8A76D4, 0xD89945, 0x3E8EB8, 0xC2577F, 0x4F9D69, 0x71827A).map(::Rgb)

// Anchos de Helvetica y Helvetica-Bold (AFM de Adobe) para los caracteres 32..126, en milésimas de em.
private val HelveticaWidths = ("278 278 355 556 556 889 667 191 333 333 389 584 278 333 278 278 556 556 556 556 556 556 556 556 556 556 " +
    "278 278 584 584 584 556 1015 667 667 722 722 667 611 778 722 278 500 667 556 833 722 778 667 778 722 667 611 722 667 944 " +
    "667 667 611 278 278 278 469 556 333 556 556 500 556 556 278 556 556 222 222 500 222 833 556 556 556 556 333 500 278 556 " +
    "500 722 500 500 500 334 260 334 584").split(' ').map(String::toInt)
private val HelveticaBoldWidths = ("278 333 474 556 556 889 722 238 333 333 389 584 278 333 278 278 556 556 556 556 556 556 556 556 556 556 " +
    "333 333 584 584 584 611 975 722 722 722 722 667 611 778 722 278 556 722 611 833 722 778 667 778 722 667 611 722 667 944 " +
    "667 667 611 333 278 333 584 556 333 556 611 556 611 556 333 611 611 278 278 556 278 889 611 611 611 611 389 556 333 611 " +
    "556 778 556 556 500 389 280 389 584").split(' ').map(String::toInt)

// Caracteres fuera de Latin-1 que sí existen en WinAnsiEncoding, con su byte y sus anchos (normal, negrita).
private val WinAnsiExtras = mapOf(
    '€' to Triple(0x80, 556, 556), '‚' to Triple(0x82, 222, 278), '„' to Triple(0x84, 333, 500), '…' to Triple(0x85, 1000, 1000),
    '‰' to Triple(0x89, 1000, 1000), '‘' to Triple(0x91, 222, 278), '’' to Triple(0x92, 222, 278), '“' to Triple(0x93, 333, 500),
    '”' to Triple(0x94, 333, 500), '•' to Triple(0x95, 350, 350), '–' to Triple(0x96, 556, 556), '—' to Triple(0x97, 1000, 1000),
    '™' to Triple(0x99, 1000, 1000), 'Š' to Triple(0x8A, 667, 667), 'š' to Triple(0x9A, 500, 556), 'Œ' to Triple(0x8C, 1000, 1000),
    'œ' to Triple(0x9C, 944, 944), 'Ž' to Triple(0x8E, 611, 611), 'ž' to Triple(0x9E, 500, 500), 'Ÿ' to Triple(0x9F, 667, 667),
)
private val LatinWidths = mapOf(
    '¡' to (333 to 333), '¿' to (611 to 611), '·' to (278 to 278), '«' to (556 to 556), '»' to (556 to 556), '°' to (400 to 400),
    'º' to (365 to 365), 'ª' to (370 to 370), '×' to (584 to 584), '±' to (584 to 584), '©' to (737 to 737), '®' to (737 to 737),
    '§' to (556 to 556), '¢' to (556 to 556), '£' to (556 to 556), '¥' to (556 to 556), '´' to (333 to 333), '¨' to (333 to 333),
    'ß' to (611 to 611), 'Æ' to (1000 to 1000), 'æ' to (889 to 889), 'Ð' to (722 to 722), 'ð' to (556 to 611), 'Þ' to (667 to 667),
    'þ' to (556 to 611), '÷' to (584 to 584), '¬' to (584 to 584), '¦' to (260 to 280), '¹' to (333 to 333), '²' to (333 to 333),
    '³' to (333 to 333), '¼' to (834 to 834), '½' to (834 to 834), '¾' to (834 to 834), 'µ' to (556 to 611), '¶' to (537 to 556),
    '¤' to (556 to 556), '¯' to (333 to 333), '¸' to (333 to 333), '­' to (333 to 333), ' ' to (278 to 278),
)
private val AccentBase = buildMap {
    listOf("ÀÁÂÃÄÅ" to 'A', "àáâãäå" to 'a', "ÈÉÊË" to 'E', "èéêë" to 'e', "ÌÍÎÏ" to 'I', "ÒÓÔÕÖØ" to 'O', "òóôõöø" to 'o',
        "ÙÚÛÜ" to 'U', "ùúûü" to 'u', "Ñ" to 'N', "ñ" to 'n', "Ç" to 'C', "ç" to 'c', "Ý" to 'Y', "ýÿ" to 'y')
        .forEach { (chars, base) -> chars.forEach { put(it, base) } }
}

private fun charWidth(c: Char, bold: Boolean): Int {
    val table = if (bold) HelveticaBoldWidths else HelveticaWidths
    return when {
        c.code in 32..126 -> table[c.code - 32]
        c in "ìíîï" -> 278 // la i acentuada no tiene el ancho angosto de la i
        c in AccentBase -> table[AccentBase.getValue(c).code - 32]
        c in WinAnsiExtras -> WinAnsiExtras.getValue(c).let { if (bold) it.third else it.second }
        c in LatinWidths -> LatinWidths.getValue(c).let { if (bold) it.second else it.first }
        else -> 556
    }
}

/** Deja solo caracteres que Helvetica puede dibujar; los emojis se quitan y lo demás se reemplaza. */
private fun pdfClean(value: String): String = buildString {
    var i = 0
    while (i < value.length) {
        val c = value[i++]
        when {
            c == '→' -> append("->")
            c == '−' -> append('-')
            c == '\t' || c == '\n' || c == '\r' || c == ' ' -> append(' ')
            c.isHighSurrogate() -> if (i < value.length && value[i].isLowSurrogate()) i++
            c.code in 32..126 || c.code in 161..255 || c in WinAnsiExtras -> append(c)
            c.isLetterOrDigit() -> append('?')
        }
    }
}.replace(Regex(" {2,}"), " ")

private fun pdfLiteral(clean: String): String = buildString {
    clean.forEach { c ->
        val code = WinAnsiExtras[c]?.first ?: c.code
        if (code == '('.code || code == ')'.code || code == '\\'.code) append('\\')
        append(code.toChar())
    }
}

private fun textWidth(value: String, size: Double, bold: Boolean) = value.sumOf { charWidth(it, bold) } * size / 1000.0

private fun ellipsize(value: String, maxWidth: Double, size: Double, bold: Boolean = false): String {
    val clean = pdfClean(value).trim()
    if (textWidth(clean, size, bold) <= maxWidth) return clean
    var end = clean.length
    while (end > 0 && textWidth(clean.take(end).trimEnd() + "…", size, bold) > maxWidth) end--
    return clean.take(end).trimEnd() + "…"
}

private fun wrapText(value: String, maxWidth: Double, size: Double, bold: Boolean = false, maxLines: Int = 2): List<String> {
    val words = pdfClean(value).trim().split(' ').filter(String::isNotEmpty)
    val lines = mutableListOf<String>()
    var line = ""
    for (word in words) {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (textWidth(candidate, size, bold) <= maxWidth) { line = candidate; continue }
        if (line.isNotEmpty()) lines.add(line)
        line = word
        // Palabras larguísimas (por ejemplo un código) se cortan por caracteres.
        while (textWidth(line, size, bold) > maxWidth) {
            var cut = line.length - 1
            while (cut > 1 && textWidth(line.take(cut), size, bold) > maxWidth) cut--
            lines.add(line.take(cut)); line = line.drop(cut)
        }
    }
    if (line.isNotEmpty()) lines.add(line)
    if (lines.isEmpty()) return listOf("")
    if (lines.size <= maxLines) return lines
    return lines.take(maxLines - 1) + ellipsize(lines.drop(maxLines - 1).joinToString(" "), maxWidth, size, bold)
}

private fun fitSize(value: String, maxWidth: Double, start: Double, min: Double, bold: Boolean): Double {
    var size = start
    while (size > min && textWidth(pdfClean(value), size, bold) > maxWidth) size -= 0.5
    return size
}

private fun pn(value: Double) = fixed(value, 2)

private class PdfPage {
    val out = StringBuilder()

    fun rect(x: Double, y: Double, w: Double, h: Double, color: Rgb) {
        out.append("${color.op} rg ${pn(x)} ${pn(y)} ${pn(w)} ${pn(h)} re f\n")
    }

    fun roundRect(x: Double, y: Double, w: Double, h: Double, radius: Double, fill: Rgb?, stroke: Rgb? = null, lineWidth: Double = 0.75) {
        if (w <= 0 || h <= 0) return
        val r = min(radius, min(w, h) / 2); val k = r * 0.5523
        fill?.let { out.append("${it.op} rg ") }
        stroke?.let { out.append("${it.op} RG ${pn(lineWidth)} w ") }
        out.append("${pn(x + r)} ${pn(y)} m ${pn(x + w - r)} ${pn(y)} l ")
        out.append("${pn(x + w - r + k)} ${pn(y)} ${pn(x + w)} ${pn(y + r - k)} ${pn(x + w)} ${pn(y + r)} c ")
        out.append("${pn(x + w)} ${pn(y + h - r)} l ${pn(x + w)} ${pn(y + h - r + k)} ${pn(x + w - r + k)} ${pn(y + h)} ${pn(x + w - r)} ${pn(y + h)} c ")
        out.append("${pn(x + r)} ${pn(y + h)} l ${pn(x + r - k)} ${pn(y + h)} ${pn(x)} ${pn(y + h - r + k)} ${pn(x)} ${pn(y + h - r)} c ")
        out.append("${pn(x)} ${pn(y + r)} l ${pn(x)} ${pn(y + r - k)} ${pn(x + r - k)} ${pn(y)} ${pn(x + r)} ${pn(y)} c h ")
        out.append(when { fill != null && stroke != null -> "B"; fill != null -> "f"; else -> "S" }).append('\n')
    }

    fun circle(cx: Double, cy: Double, r: Double, color: Rgb) {
        val k = r * 0.5523
        out.append("${color.op} rg ${pn(cx + r)} ${pn(cy)} m ")
        out.append("${pn(cx + r)} ${pn(cy + k)} ${pn(cx + k)} ${pn(cy + r)} ${pn(cx)} ${pn(cy + r)} c ")
        out.append("${pn(cx - k)} ${pn(cy + r)} ${pn(cx - r)} ${pn(cy + k)} ${pn(cx - r)} ${pn(cy)} c ")
        out.append("${pn(cx - r)} ${pn(cy - k)} ${pn(cx - k)} ${pn(cy - r)} ${pn(cx)} ${pn(cy - r)} c ")
        out.append("${pn(cx + k)} ${pn(cy - r)} ${pn(cx + r)} ${pn(cy - k)} ${pn(cx + r)} ${pn(cy)} c f\n")
    }

    fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Rgb, width: Double = 0.75) {
        out.append("${color.op} RG ${pn(width)} w ${pn(x1)} ${pn(y1)} m ${pn(x2)} ${pn(y2)} l S\n")
    }

    fun text(x: Double, y: Double, value: String, size: Double, bold: Boolean = false, color: Rgb = PdfNight, spacing: Double = 0.0) {
        val clean = pdfClean(value)
        if (clean.isBlank()) return
        out.append("BT /${if (bold) "F2" else "F1"} ${pn(size)} Tf ${pn(spacing)} Tc ${color.op} rg ${pn(x)} ${pn(y)} Td (${pdfLiteral(clean)}) Tj ET\n")
    }

    fun textRight(right: Double, y: Double, value: String, size: Double, bold: Boolean = false, color: Rgb = PdfNight, spacing: Double = 0.0) {
        val clean = pdfClean(value)
        text(right - textWidth(clean, size, bold) - spacing * clean.length, y, clean, size, bold, color, spacing)
    }

    fun textCenter(center: Double, y: Double, value: String, size: Double, bold: Boolean = false, color: Rgb = PdfNight) {
        val clean = pdfClean(value)
        text(center - textWidth(clean, size, bold) / 2, y, clean, size, bold, color)
    }
}

private fun niceCeil(value: Double): Double {
    if (value <= 0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val step = listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { value / magnitude <= it + 1e-9 }
    return step * magnitude
}

private fun compactMoney(value: Double): String {
    fun short(v: Double) = fixed(v, 1).removeSuffix(".0")
    return "S/ " + when {
        value >= 1_000_000 -> short(value / 1_000_000) + "M"
        value >= 1_000 -> short(value / 1_000) + "k"
        else -> fixed(value, 0)
    }
}

private fun pdfPlural(count: Int, one: String, many: String) = "$count ${if (count == 1) one else many}"

internal fun exportPdfReport(items: List<Movement>, period: String, owner: String): ByteArray {
    val stats = ReportStats(items)
    val today = civilDateFromEpochDays()
    val generated = "${today.day} ${reportMonthShort(today.month)} ${today.year}"
    val pages = mutableListOf<PdfPage>()
    var page = PdfPage().also(pages::add)
    var y: Double

    // ---------- Portada: franja verde con título ----------
    val bandBottom = PDF_PAGE_H - 150
    page.rect(0.0, bandBottom, PDF_PAGE_W, 150.0, PdfBrandDeep)
    page.out.append("q 0 ${pn(bandBottom)} ${pn(PDF_PAGE_W)} 150 re W n\n")
    page.circle(560.0, 830.0, 120.0, PdfBrandGlow1)
    page.circle(455.0, 675.0, 70.0, PdfBrandGlow2)
    page.out.append("Q\n")
    page.text(PDF_MARGIN, 804.0, "DINEX · REPORTE FINANCIERO", 8.0, true, PdfBrandSoft, 1.6)
    page.text(PDF_MARGIN, 770.0, "Reporte de movimientos", 26.0, true, PdfWhite)
    page.text(PDF_MARGIN, 748.0, ellipsize(owner.ifBlank { "Mi cuenta DINEX" }, 360.0, 11.5), 11.5, false, PdfWhite)
    page.text(PDF_MARGIN, 728.0, ellipsize("Periodo: $period  ·  Generado el $generated", 380.0, 9.0), 9.0, false, PdfBrandSoft)
    val chipLabel = pdfPlural(items.size, "movimiento", "movimientos")
    val chipW = textWidth(chipLabel, 8.5, true) + 24
    page.roundRect(PDF_RIGHT - chipW, 795.0, chipW, 20.0, 10.0, PdfChip)
    page.text(PDF_RIGHT - chipW + 12, 801.5, chipLabel, 8.5, true, PdfWhite)

    // ---------- Tarjetas con las cifras principales ----------
    data class Kpi(val label: String, val value: String, val note: String, val color: Rgb)
    val kpis = listOf(
        Kpi("INGRESOS", moneyGrouped(stats.income), pdfPlural(stats.incomeCount, "ingreso", "ingresos"), PdfBrand),
        Kpi("EGRESOS", moneyGrouped(stats.expense), pdfPlural(stats.expenseCount, "gasto", "gastos"), PdfExpense),
        Kpi("BALANCE", moneyGrouped(stats.balance), "Ingresos menos egresos", if (stats.balance < 0) PdfExpense else PdfNight),
        Kpi("TASA DE AHORRO", if (stats.income > 0) "${fixed(stats.savingsRate * 100, 1)}%" else "—",
            if (stats.income > 0) "De lo que ingresó" else "Sin ingresos en el periodo", if (stats.savingsRate < 0) PdfExpense else PdfMintDeep),
    )
    val cardW = (PDF_CONTENT_W - 30) / 4
    val cardY = 596.0
    kpis.forEachIndexed { i, kpi ->
        val x = PDF_MARGIN + i * (cardW + 10)
        page.roundRect(x, cardY, cardW, 72.0, 9.0, PdfWhite, PdfLine)
        page.roundRect(x + 12, cardY + 51, 6.0, 6.0, 3.0, kpi.color)
        page.text(x + 23, cardY + 51.5, kpi.label, 7.0, true, PdfMuted, 0.5)
        val size = fitSize(kpi.value, cardW - 24, 15.0, 9.0, true)
        page.text(x + 12, cardY + 28, kpi.value, size, true, kpi.color)
        page.text(x + 12, cardY + 13, ellipsize(kpi.note, cardW - 24, 7.5), 7.5, false, PdfMuted)
    }
    y = 566.0

    fun section(title: String, note: String = "") {
        page.text(PDF_MARGIN, y, title, 13.0, true, PdfNight)
        if (note.isNotEmpty()) page.textRight(PDF_RIGHT, y + 1, note, 8.5, false, PdfMuted)
        page.line(PDF_MARGIN, y - 9, PDF_RIGHT, y - 9, PdfLine, 0.75)
        y -= 28
    }

    // ---------- Gráfico 1: gastos por categoría (barras horizontales) ----------
    section("Gastos por categoría", if (stats.expense > 0) "Total gastado ${moneyGrouped(stats.expense)}" else "")
    val shownCategories = if (stats.categories.size <= 7) stats.categories else {
        val rest = stats.categories.drop(6)
        stats.categories.take(6) + ReportTotal("Otras categorías", rest.sumOf { it.amount }, rest.sumOf { it.count })
    }
    if (shownCategories.isEmpty()) {
        page.text(PDF_MARGIN, y, "No registraste gastos en este periodo.", 9.5, false, PdfMuted); y -= 22
    } else {
        val largest = shownCategories.maxOf { it.amount }.coerceAtLeast(0.01)
        val trackX = 160.0; val trackW = 250.0
        shownCategories.forEachIndexed { i, total ->
            val color = categories.firstOrNull { it.name.equals(total.label, true) }
                ?.let { Rgb(it.color.toArgb() and 0xFFFFFF) } ?: PdfCategoryPalette[i % PdfCategoryPalette.size]
            page.roundRect(PDF_MARGIN, y - 0.5, 7.0, 7.0, 3.5, color)
            page.text(PDF_MARGIN + 13, y, ellipsize(total.label.ifBlank { "Sin categoría" }, trackX - PDF_MARGIN - 20, 9.0), 9.0, false, PdfNight)
            page.roundRect(trackX, y - 1.5, trackW, 9.0, 4.5, PdfTrack)
            page.roundRect(trackX, y - 1.5, max(9.0, trackW * total.amount / largest), 9.0, 4.5, color)
            val share = if (stats.expense > 0) total.amount / stats.expense * 100 else 0.0
            page.text(trackX + trackW + 10, y, "${fixed(share, if (share < 10) 1 else 0)}%", 8.0, false, PdfMuted)
            page.textRight(PDF_RIGHT, y, moneyGrouped(total.amount), 9.0, true, PdfNight)
            y -= 19
        }
    }
    y -= 16

    // ---------- Gráfico 2: ingresos y egresos por mes (columnas) ----------
    val months = stats.months.takeLast(12)
    if (months.isNotEmpty()) {
        page.text(PDF_MARGIN, y, "Ingresos y egresos por mes", 13.0, true, PdfNight)
        var legendX = PDF_RIGHT
        listOf("Egresos" to PdfExpense, "Ingresos" to PdfBrand).forEach { (label, color) ->
            legendX -= textWidth(label, 8.0, false)
            page.text(legendX, y + 1, label, 8.0, false, PdfMuted)
            legendX -= 12
            page.roundRect(legendX, y + 0.5, 7.0, 7.0, 2.0, color)
            legendX -= 14
        }
        if (stats.months.size > 12) page.text(PDF_MARGIN + textWidth("Ingresos y egresos por mes", 13.0, true) + 8, y + 1, "(últimos 12 meses)", 8.0, false, PdfMuted)
        page.line(PDF_MARGIN, y - 9, PDF_RIGHT, y - 9, PdfLine, 0.75)
        val plotTop = y - 24; val plotBottom = plotTop - 112
        val plotLeft = 88.0; val plotW = PDF_RIGHT - plotLeft
        val top = niceCeil(months.maxOf { max(it.income, it.expense) })
        for (step in 0..4) {
            val gy = plotBottom + (plotTop - plotBottom) * step / 4
            page.line(plotLeft, gy, PDF_RIGHT, gy, if (step == 0) PdfLine else PdfGrid, if (step == 0) 0.9 else 0.5)
            page.textRight(plotLeft - 7, gy - 2.5, compactMoney(top * step / 4), 7.0, false, PdfMuted)
        }
        val groupW = plotW / months.size
        val barW = min(18.0, groupW * 0.3)
        months.forEachIndexed { i, month ->
            val cx = plotLeft + groupW * (i + 0.5)
            listOf(month.income to PdfBrand, month.expense to PdfExpense).forEachIndexed { j, (value, color) ->
                val h = (plotTop - plotBottom) * value / top
                if (h > 0.4) page.roundRect(if (j == 0) cx - barW - 1.5 else cx + 1.5, plotBottom, barW, h, min(2.5, h / 2), color)
            }
            val label = if (months.size <= 6) month.label else "${reportMonthShort(month.month)} ${month.year % 100}"
            page.textCenter(cx, plotBottom - 13, label, 7.5, false, PdfMuted)
        }
        y = plotBottom - 40
    }

    // ---------- Datos clave ----------
    val topCategory = stats.categories.firstOrNull()
    val biggest = stats.largestExpense
    if (topCategory != null && biggest != null) {
        val boxH = 54.0
        page.roundRect(PDF_MARGIN, y - boxH, PDF_CONTENT_W, boxH, 9.0, PdfMint)
        val colW = PDF_CONTENT_W / 3
        listOf(
                Triple("GASTO MÁS ALTO", moneyGrouped(biggest.amount), biggest.title),
                Triple("CATEGORÍA PRINCIPAL", topCategory.label.ifBlank { "Sin categoría" }, "${fixed(topCategory.amount / stats.expense * 100, 0)}% de lo gastado"),
                Triple("GASTO PROMEDIO", moneyGrouped(stats.averageExpense), "En ${pdfPlural(stats.expenseCount, "gasto", "gastos")}"),
        ).forEachIndexed { i, (label, value, note) ->
            val x = PDF_MARGIN + 14 + i * colW
            page.text(x, y - 16, label, 6.8, true, PdfMintDeep, 0.5)
            page.text(x, y - 32, ellipsize(value, colW - 26, 11.5, true), 11.5, true, PdfNight)
            page.text(x, y - 45, ellipsize(note, colW - 26, 7.5), 7.5, false, PdfMuted)
        }
        y -= boxH + 30
    }

    // ---------- Tabla con el detalle ----------
    val dateX = PDF_MARGIN + 10; val descX = 112.0; val descW = 168.0
    val catX = 292.0; val catW = 84.0; val methodX = 384.0; val methodW = 78.0; val amountRight = PDF_RIGHT - 10

    fun newPage() {
        page = PdfPage().also(pages::add)
        page.text(PDF_MARGIN, 806.0, "DINEX", 10.5, true, PdfBrand)
        page.text(PDF_MARGIN + textWidth("DINEX", 10.5, true) + 6, 806.0, "Reporte de movimientos", 10.5, false, PdfNight)
        page.textRight(PDF_RIGHT, 806.5, ellipsize(period, 220.0, 8.5), 8.5, false, PdfMuted)
        page.line(PDF_MARGIN, 795.0, PDF_RIGHT, 795.0, PdfLine, 0.75)
        y = 772.0
    }

    fun tableHeader() {
        page.roundRect(PDF_MARGIN, y - 22, PDF_CONTENT_W, 22.0, 6.0, PdfMint)
        val base = y - 14.5
        page.text(dateX, base, "FECHA", 7.0, true, PdfMintDeep, 0.6)
        page.text(descX, base, "DESCRIPCIÓN", 7.0, true, PdfMintDeep, 0.6)
        page.text(catX, base, "CATEGORÍA", 7.0, true, PdfMintDeep, 0.6)
        page.text(methodX, base, "MEDIO", 7.0, true, PdfMintDeep, 0.6)
        page.textRight(amountRight, base, "MONTO", 7.0, true, PdfMintDeep, 0.6)
        y -= 26
    }

    if (y < 220) newPage()
    section("Detalle de movimientos", pdfPlural(items.size, "movimiento", "movimientos"))
    if (items.isEmpty()) {
        page.text(PDF_MARGIN, y, "No hay movimientos en el periodo seleccionado.", 10.0, false, PdfMuted); y -= 24
    } else {
        tableHeader()
        items.forEachIndexed { index, item ->
            val description = wrapText(item.title.ifBlank { "Movimiento" }, descW, 8.5)
            val place = item.location.takeIf(String::isNotBlank)?.let { ellipsize(it, descW, 7.5) }
            val lines = max(2, description.size + if (place != null) 1 else 0)
            val rowH = lines * 11.0 + 11
            if (y - rowH < PDF_BOTTOM_LIMIT) { newPage(); tableHeader() }
            if (index % 2 == 1) page.roundRect(PDF_MARGIN, y - rowH, PDF_CONTENT_W, rowH, 5.0, PdfZebra)
            val base = y - 14
            page.text(dateX, base, item.reportDate(), 8.5, false, PdfNight)
            page.text(dateX, base - 11, if (item.income) "Ingreso" else "Egreso", 7.5, true, if (item.income) PdfBrand else PdfExpense)
            description.forEachIndexed { i, text -> page.text(descX, base - i * 11, text, 8.5, false, PdfNight) }
            place?.let { page.text(descX, base - description.size * 11, it, 7.5, false, PdfMuted) }
            page.text(catX, base, ellipsize(item.category.ifBlank { "—" }, catW, 8.5), 8.5, false, PdfNight)
            page.text(methodX, base, ellipsize(item.method.ifBlank { "—" }, methodW, 8.5), 8.5, false, PdfNight)
            page.textRight(amountRight, base, (if (item.income) "+ " else "- ") + moneyGrouped(item.amount), 8.5, true, if (item.income) PdfBrand else PdfExpense)
            y -= rowH
        }
        page.line(PDF_MARGIN, y, PDF_RIGHT, y, PdfLine, 0.75)
        y -= 14

        // Cierre con los totales del periodo.
        if (y - 52 < PDF_BOTTOM_LIMIT) newPage()
        page.roundRect(PDF_MARGIN, y - 52, PDF_CONTENT_W, 52.0, 9.0, PdfBrandDeep)
        val colW = PDF_CONTENT_W / 3
        listOf("TOTAL INGRESOS" to moneyGrouped(stats.income), "TOTAL EGRESOS" to moneyGrouped(stats.expense), "BALANCE DEL PERIODO" to moneyGrouped(stats.balance))
            .forEachIndexed { i, (label, value) ->
                val x = PDF_MARGIN + 16 + i * colW
                page.text(x, y - 19, label, 6.8, true, PdfBrandSoft, 0.5)
                page.text(x, y - 37, value, fitSize(value, colW - 28, 13.0, 9.0, true), true, PdfWhite)
            }
        y -= 52
    }

    // ---------- Pie de página con numeración ----------
    pages.forEachIndexed { i, sheet ->
        sheet.line(PDF_MARGIN, 46.0, PDF_RIGHT, 46.0, PdfLine, 0.5)
        sheet.text(PDF_MARGIN, 31.0, "DINEX · Decide mejor. Ahorra más.", 7.5, true, PdfBrand)
        sheet.textCenter(PDF_PAGE_W / 2, 31.0, "Los montos corresponden a tus registros en la app", 7.0, false, PdfMuted)
        sheet.textRight(PDF_RIGHT, 31.0, "Página ${i + 1} de ${pages.size}", 7.5, false, PdfMuted)
    }
    return assemblePdf(pages.map { it.out.toString() }, owner)
}

private fun pdfBytes(value: String): ByteArray = ByteArray(value.length) { (value[it].code and 255).toByte() }

private fun assemblePdf(pages: List<String>, owner: String): ByteArray {
    val today = civilDateFromEpochDays()
    val stamp = "${today.year}${today.month.toString().padStart(2, '0')}${today.day.toString().padStart(2, '0')}"
    val objects = mutableListOf<ByteArray>()
    fun obj(value: String) { objects.add(pdfBytes(value)) }
    obj("<< /Type /Catalog /Pages 2 0 R /ViewerPreferences << /DisplayDocTitle true >> >>")
    obj("<< /Type /Pages /Count ${pages.size} /Kids [${pages.indices.joinToString(" ") { "${6 + it * 2} 0 R" }}] >>")
    obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
    obj("<< /Title (Reporte de movimientos DINEX) /Author (${pdfLiteral(pdfClean(owner))}) /Creator (DINEX) /Producer (DINEX) /CreationDate (D:$stamp) >>")
    pages.forEachIndexed { i, page ->
        obj("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${PDF_PAGE_W.toInt()} ${PDF_PAGE_H.toInt()}] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${7 + i * 2} 0 R >>")
        val content = pdfBytes(page)
        objects.add(pdfBytes("<< /Length ${content.size} >>\nstream\n") + content + pdfBytes("\nendstream"))
    }
    val result = BinaryBuffer()
    result.bytes(pdfBytes("%PDF-1.4\n%âãÏÓ\n"))
    val offsets = mutableListOf<Int>()
    objects.forEachIndexed { i, content ->
        offsets.add(result.size)
        result.bytes(pdfBytes("${i + 1} 0 obj\n")); result.bytes(content); result.bytes(pdfBytes("\nendobj\n"))
    }
    val xref = result.size
    result.bytes(pdfBytes("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n"))
    offsets.forEach { result.bytes(pdfBytes("${it.toString().padStart(10, '0')} 00000 n \n")) }
    result.bytes(pdfBytes("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R /Info 5 0 R >>\nstartxref\n$xref\n%%EOF\n"))
    return result.array()
}
