package com.example.dinex

internal enum class ReportFormat(val label: String, val extension: String, val mime: String) {
    XLSX("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("CSV", "csv", "text/csv"),
    PDF("PDF", "pdf", "application/pdf")
}

internal fun createMovementReport(items: List<Movement>, format: ReportFormat, period: String, owner: String): TransferDocument {
    require(items.size <= MAX_IMPORT_ROWS) { "Exporta como máximo $MAX_IMPORT_ROWS movimientos por reporte." }
    val bytes = when (format) {
        ReportFormat.CSV -> ("\uFEFF" + exportMovementsCsv(items)).encodeToByteArray()
        ReportFormat.XLSX -> exportWorkbook(items, period, owner)
        ReportFormat.PDF -> exportPdfReport(items, period, owner)
    }
    val today = civilDateFromEpochDays()
    val date = "${today.year}-${today.month.toString().padStart(2, '0')}-${today.day.toString().padStart(2, '0')}"
    return TransferDocument("DINEX_movimientos_${date}.${format.extension}", format.mime, bytes)
}

private fun workbookText(column: String, row: Int, value: String, style: Int = 0) =
    "<c r=\"$column$row\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${xmlText(value)}</t></is></c>"

private fun workbookNumber(column: String, row: Int, amount: Double) =
    "<c r=\"$column$row\" s=\"2\"><v>${format2(amount)}</v></c>"

internal fun exportWorkbook(items: List<Movement>, period: String, owner: String): ByteArray {
    val namespace = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    val headings = listOf("Fecha", "Descripción", "Categoría", "Monto", "Tipo", "Medio", "Ubicación")
    val sheet = buildString {
        append("<worksheet xmlns=\"$namespace\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        append("<cols><col min=\"1\" max=\"1\" width=\"14\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"42\" customWidth=\"1\"/><col min=\"3\" max=\"7\" width=\"22\" customWidth=\"1\"/></cols><sheetData>")
        append("<row r=\"1\">"); headings.forEachIndexed { i, value -> append(workbookText(('A'.code + i).toChar().toString(), 1, value, 1)) }; append("</row>")
        items.forEachIndexed { i, item ->
            val row = i + 2
            append("<row r=\"$row\">")
            append(workbookText("A", row, item.dateIso())); append(workbookText("B", row, item.title)); append(workbookText("C", row, item.category))
            append(workbookNumber("D", row, item.amount)); append(workbookText("E", row, if (item.income) "Ingreso" else "Egreso"))
            append(workbookText("F", row, item.method)); append(workbookText("G", row, item.location)); append("</row>")
        }
        append("</sheetData><autoFilter ref=\"A1:G${items.size + 1}\"/></worksheet>")
    }
    val income = items.filter { it.income }.sumOf { it.amount }
    val expenses = items.filterNot { it.income }.sumOf { it.amount }
    val summary = buildString {
        append("<worksheet xmlns=\"$namespace\"><cols><col min=\"1\" max=\"2\" width=\"40\" customWidth=\"1\"/></cols><sheetData>")
        listOf("Reporte DINEX" to owner, "Periodo" to period, "Movimientos" to items.size.toString()).forEachIndexed { i, pair ->
            append("<row r=\"${i + 1}\">${workbookText("A", i + 1, pair.first, 1)}${workbookText("B", i + 1, pair.second)}</row>")
        }
        listOf("Ingresos" to income, "Egresos" to expenses, "Balance del periodo" to income - expenses).forEachIndexed { i, pair ->
            append("<row r=\"${i + 5}\">${workbookText("A", i + 5, pair.first, 1)}${workbookNumber("B", i + 5, pair.second)}</row>")
        }
        append("</sheetData></worksheet>")
    }
    return writeZip(linkedMapOf(
        "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
        "_rels/.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
        "xl/workbook.xml" to """<workbook xmlns="$namespace" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Movimientos" sheetId="1" r:id="rId1"/><sheet name="Resumen" sheetId="2" r:id="rId2"/></sheets></workbook>""",
        "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
        "xl/styles.xml" to """<styleSheet xmlns="$namespace"><numFmts count="1"><numFmt numFmtId="164" formatCode="&quot;S/ &quot;#,##0.00"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0D3A42"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""",
        "xl/worksheets/sheet1.xml" to sheet,
        "xl/worksheets/sheet2.xml" to summary,
    ))
}

private fun pdfBytes(value: String): ByteArray = value.map { (if (it.code in 0..255) it.code else '?'.code).toByte() }.toByteArray()
private fun pdfEscape(value: String) = value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace('\n', ' ').replace('\r', ' ')
private fun reportWrap(value: String, max: Int): List<String> {
    val result = mutableListOf<String>(); var line = ""
    value.replace(Regex("\\s+"), " ").split(' ').forEach { word ->
        if (line.isNotEmpty() && line.length + word.length + 1 > max) { result.add(line); line = "" }
        if (word.length > max) { if (line.isNotEmpty()) { result.add(line); line = "" }; result.addAll(word.chunked(max).dropLast(1)); line = word.chunked(max).last() }
        else line = if (line.isEmpty()) word else "$line $word"
    }
    if (line.isNotEmpty()) result.add(line)
    return result.ifEmpty { listOf("") }
}

internal fun exportPdfReport(items: List<Movement>, period: String, owner: String): ByteArray {
    val income = items.filter { it.income }.sumOf { it.amount }; val expense = items.filterNot { it.income }.sumOf { it.amount }
    val pages = mutableListOf<String>(); var commands = StringBuilder(); var y = 0
    fun text(x: Int, at: Int, value: String, size: Int = 10, bold: Boolean = false) {
        commands.append("BT /${if (bold) "F2" else "F1"} $size Tf $x $at Td (${pdfEscape(value)}) Tj ET\n")
    }
    fun beginPage() {
        commands = StringBuilder("0.05 0.15 0.2 rg\n")
        text(40, 795, "DINEX - Reporte de movimientos", 19, true)
        text(40, 772, "$owner | $period".take(100), 10)
        text(40, 746, "Ingresos: ${money(income)}   Egresos: ${money(expense)}   Balance: ${money(income - expense)}", 10, true)
        text(40, 721, "${items.size} movimientos registrados", 10)
        commands.append("0.8 0.85 0.85 RG 40 709 m 555 709 l S\n")
        text(40, 688, "Fecha / Tipo", 10, true); text(156, 688, "Descripción y detalle", 10, true); text(459, 688, "Monto (S/)", 10, true)
        y = 665
    }
    fun finishPage() { text(40, 28, "DINEX | Página ${pages.size + 1} | Los montos corresponden a tus registros", 8); pages.add(commands.toString()) }
    beginPage()
    items.forEach { item ->
        val detail = "${item.category} / ${item.method}" + if (item.location.isNotBlank()) " / ${item.location}" else ""
        val lines = reportWrap(item.title, 46) + reportWrap(detail, 54)
        val height = lines.size * 13 + 15
        if (y - height < 55) { finishPage(); beginPage() }
        text(40, y, item.dateIso()); text(40, y - 14, if (item.income) "Ingreso" else "Egreso", 9)
        lines.forEachIndexed { i, line -> text(156, y - i * 13, line, if (i == 0) 10 else 9, i == 0) }
        text(459, y, (if (item.income) "+ " else "- ") + format2(item.amount), 10, true)
        y -= height
        commands.append("0.9 0.92 0.92 RG 40 ${y + 5} m 555 ${y + 5} l S\n")
    }
    if (items.isEmpty()) text(40, y, "No hay movimientos en el periodo seleccionado.", 11)
    finishPage()
    val objects = mutableListOf<ByteArray>()
    fun obj(value: String) { objects.add(pdfBytes(value)) }
    obj("<< /Type /Catalog /Pages 2 0 R >>")
    obj("<< /Type /Pages /Count ${pages.size} /Kids [${pages.indices.joinToString(" ") { "${5 + it * 2} 0 R" }}] >>")
    obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
    pages.forEachIndexed { i, page ->
        obj("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${6 + i * 2} 0 R >>")
        val content = pdfBytes(page)
        objects.add(pdfBytes("<< /Length ${content.size} >>\nstream\n") + content + pdfBytes("\nendstream"))
    }
    val result = BinaryBuffer(); result.bytes(pdfBytes("%PDF-1.4\n%âãÏÓ\n")); val offsets = mutableListOf(0)
    objects.forEachIndexed { i, content -> offsets.add(result.size); result.bytes(pdfBytes("${i + 1} 0 obj\n")); result.bytes(content); result.bytes(pdfBytes("\nendobj\n")) }
    val xref = result.size
    result.bytes(pdfBytes("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n"))
    offsets.drop(1).forEach { result.bytes(pdfBytes("${it.toString().padStart(10, '0')} 00000 n \n")) }
    result.bytes(pdfBytes("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n"))
    return result.array()
}
