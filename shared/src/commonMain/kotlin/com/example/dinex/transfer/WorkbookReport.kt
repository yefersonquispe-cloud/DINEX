package com.example.dinex

import androidx.compose.ui.graphics.toArgb

// Libro Excel con dos hojas:
//  - "Resumen": cifras con fórmulas reales (SUMIFS, COUNTIFS…) y tres gráficos nativos de Excel.
//  - "Movimientos": la tabla con filtros, fechas reales y montos con formato de soles.
// Las fórmulas viven solo en "Resumen"; los textos del usuario siempre se guardan como texto,
// así nadie puede colar una fórmula en la hoja de movimientos.

private const val SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
private const val OFFICE_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val PACKAGE_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val CHART_NS = "http://schemas.openxmlformats.org/drawingml/2006/chart"
private const val DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
private const val EXCEL_EPOCH = 25_569

// Índices de cellXfs definidos en workbookStyles().
private const val XS_HEADER = 1
private const val XS_MONEY = 2
private const val XS_DATE = 3
private const val XS_TEXT = 4
private const val XS_INCOME = 5
private const val XS_EXPENSE = 6
private const val XS_INCOME_TAG = 7
private const val XS_EXPENSE_TAG = 8
private const val XS_TITLE = 9
private const val XS_SUBTITLE = 10
private const val XS_KPI_LABEL = 11
private const val XS_KPI_INCOME = 12
private const val XS_KPI_EXPENSE = 13
private const val XS_KPI_BALANCE = 14
private const val XS_KPI_PERCENT = 15
private const val XS_KPI_COUNT = 16
private const val XS_SECTION = 17
private const val XS_PERCENT = 18
private const val XS_COUNT = 19
private const val XS_BALANCE = 20
private const val XS_TOTAL_LABEL = 21
private const val XS_TOTAL_MONEY = 22
private const val XS_TOTAL_PERCENT = 23
private const val XS_TOTAL_COUNT = 24
private const val XS_NOTE = 25
private const val XS_TOTAL_BALANCE = 26

private val ChartPalette = listOf("176B43", "E66678", "8A76D4", "D89945", "3E8EB8", "C2577F", "4F9D69", "71827A", "2F5D8A", "B5893B")

private fun cellText(ref: String, value: String, style: Int) =
    "<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${xmlText(value)}</t></is></c>"

private fun cellNumber(ref: String, value: Double, style: Int, decimals: Int = 2) = "<c r=\"$ref\" s=\"$style\"><v>${fixed(value, decimals)}</v></c>"

/** Fórmula con su resultado ya calculado, para que se vea bien incluso en visores que no recalculan. */
private fun cellFormula(ref: String, formula: String, cached: Double, style: Int) =
    "<c r=\"$ref\" s=\"$style\"><f>${xmlText(formula)}</f><v>${fixed(cached, 6)}</v></c>"

private fun row(index: Int, cells: String, height: Double? = null) =
    "<row r=\"$index\"${height?.let { " ht=\"${fixed(it, 1)}\" customHeight=\"1\"" } ?: ""}>$cells</row>"

private fun excelSerial(year: Int, month: Int, day: Int) = epochDaysOf(year, month, day) + EXCEL_EPOCH

private data class ChartSpec(val xml: String, val fromRow: Int, val toRow: Int)

internal fun exportWorkbook(items: List<Movement>, period: String, owner: String): ByteArray {
    val stats = ReportStats(items)
    val last = items.size + 1
    val movementsSheet = movementsSheet(items, last)
    val charts = mutableListOf<ChartSpec>()
    val summarySheet = summarySheet(stats, period, owner, charts)
    val chartFiles = charts.mapIndexed { i, chart -> "xl/charts/chart${i + 1}.xml" to chart.xml }

    val files = linkedMapOf(
        "[Content_Types].xml" to buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
            append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
            append("<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
            append("<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
            if (charts.isNotEmpty()) append("<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
            chartFiles.forEach { (name, _) -> append("<Override PartName=\"/$name\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/>") }
            append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
            append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
            append("</Types>")
        },
        "_rels/.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"$PACKAGE_REL\">" +
            "<Relationship Id=\"rId1\" Type=\"$OFFICE_REL/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
            "<Relationship Id=\"rId3\" Type=\"$OFFICE_REL/extended-properties\" Target=\"docProps/app.xml\"/></Relationships>",
        "docProps/core.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>Reporte de movimientos DINEX</dc:title><dc:creator>${xmlText(owner.ifBlank { "DINEX" })}</dc:creator>" +
            "<dc:description>${xmlText(period)}</dc:description></cp:coreProperties>",
        "docProps/app.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>DINEX</Application></Properties>",
        // La hoja "Resumen" aparece primero, pero el archivo sheet1 sigue siendo "Movimientos" para que reimportar funcione igual.
        "xl/workbook.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"$SHEET_NS\" xmlns:r=\"$OFFICE_REL\">" +
            "<bookViews><workbookView activeTab=\"0\"/></bookViews><sheets><sheet name=\"Resumen\" sheetId=\"2\" r:id=\"rId2\"/>" +
            "<sheet name=\"Movimientos\" sheetId=\"1\" r:id=\"rId1\"/></sheets><definedNames>" +
            "<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"1\" hidden=\"1\">Movimientos!\$A\$1:\$G\$$last</definedName>" +
            "<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"1\">Movimientos!\$1:\$1</definedName></definedNames>" +
            "<calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>",
        "xl/_rels/workbook.xml.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"$PACKAGE_REL\">" +
            "<Relationship Id=\"rId1\" Type=\"$OFFICE_REL/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"$OFFICE_REL/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
            "<Relationship Id=\"rId3\" Type=\"$OFFICE_REL/styles\" Target=\"styles.xml\"/></Relationships>",
        "xl/styles.xml" to workbookStyles(),
        "xl/worksheets/sheet1.xml" to movementsSheet,
        "xl/worksheets/sheet2.xml" to summarySheet,
    )
    if (charts.isNotEmpty()) {
        files["xl/worksheets/_rels/sheet2.xml.rels"] = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"$PACKAGE_REL\">" +
            "<Relationship Id=\"rId1\" Type=\"$OFFICE_REL/drawing\" Target=\"../drawings/drawing1.xml\"/></Relationships>"
        files["xl/drawings/drawing1.xml"] = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"$DRAWING_NS\">")
            charts.forEachIndexed { i, chart ->
                append("<xdr:twoCellAnchor editAs=\"oneCell\">")
                append("<xdr:from><xdr:col>6</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${chart.fromRow}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>")
                append("<xdr:to><xdr:col>15</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${chart.toRow}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>")
                append("<xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr><xdr:cNvPr id=\"${i + 2}\" name=\"Gráfico ${i + 1}\"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>")
                append("<xdr:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/></xdr:xfrm><a:graphic><a:graphicData uri=\"$CHART_NS\">")
                append("<c:chart xmlns:c=\"$CHART_NS\" xmlns:r=\"$OFFICE_REL\" r:id=\"rId${i + 1}\"/></a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor>")
            }
            append("</xdr:wsDr>")
        }
        files["xl/drawings/_rels/drawing1.xml.rels"] = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"$PACKAGE_REL\">" +
            charts.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"$OFFICE_REL/chart\" Target=\"../charts/chart${it + 1}.xml\"/>" } +
            "</Relationships>"
        chartFiles.forEach { (name, xml) -> files[name] = xml }
    }
    return writeZip(files)
}

private fun movementsSheet(items: List<Movement>, last: Int): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"$SHEET_NS\" xmlns:r=\"$OFFICE_REL\">")
    append("<sheetPr><tabColor rgb=\"FF8A76D4\"/><pageSetUpPr fitToPage=\"1\"/></sheetPr><dimension ref=\"A1:G$last\"/>")
    append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
    append("<selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"18\"/>")
    append("<cols><col min=\"1\" max=\"1\" width=\"13\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"42\" customWidth=\"1\"/>")
    append("<col min=\"3\" max=\"3\" width=\"19\" customWidth=\"1\"/><col min=\"4\" max=\"4\" width=\"15\" customWidth=\"1\"/>")
    append("<col min=\"5\" max=\"5\" width=\"11\" customWidth=\"1\"/><col min=\"6\" max=\"6\" width=\"17\" customWidth=\"1\"/>")
    append("<col min=\"7\" max=\"7\" width=\"22\" customWidth=\"1\"/></cols><sheetData>")
    append(row(1, listOf("Fecha", "Descripción", "Categoría", "Monto", "Tipo", "Medio", "Ubicación")
        .mapIndexed { i, value -> cellText("${'A' + i}1", value, XS_HEADER) }.joinToString(""), 24.0))
    items.forEachIndexed { i, item ->
        val r = i + 2
        append(row(r, cellNumber("A$r", excelSerial(item.year, item.month, item.day).toDouble(), XS_DATE, 0) +
            cellText("B$r", item.title, XS_TEXT) + cellText("C$r", item.category, XS_TEXT) +
            cellNumber("D$r", item.amount, if (item.income) XS_INCOME else XS_EXPENSE) +
            cellText("E$r", if (item.income) "Ingreso" else "Egreso", if (item.income) XS_INCOME_TAG else XS_EXPENSE_TAG) +
            cellText("F$r", item.method, XS_TEXT) + cellText("G$r", item.location, XS_TEXT), 19.0))
    }
    append("</sheetData><autoFilter ref=\"A1:G$last\"/>")
    // Filas alternadas (cebra) sin tocar la columna Tipo, que ya tiene su color.
    if (items.isNotEmpty()) append("<conditionalFormatting sqref=\"A2:D$last F2:G$last\"><cfRule type=\"expression\" dxfId=\"0\" priority=\"1\"><formula>MOD(ROW(),2)=1</formula></cfRule></conditionalFormatting>")
    append("<pageMargins left=\"0.4\" right=\"0.4\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>")
    append("<pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
    append("<headerFooter><oddFooter>&amp;LDINEX · Movimientos&amp;RPágina &amp;P de &amp;N</oddFooter></headerFooter></worksheet>")
}

private fun summarySheet(stats: ReportStats, period: String, owner: String, charts: MutableList<ChartSpec>): String {
    val today = civilDateFromEpochDays()
    val rows = StringBuilder()
    val merges = mutableListOf("A1:E1", "A2:E2")
    val dataBars = mutableListOf<String>()
    var r = 1
    val generated = "${today.day.toString().padStart(2, '0')}/${today.month.toString().padStart(2, '0')}/${today.year}"
    rows.append(row(1, cellText("A1", "Reporte de movimientos DINEX", XS_TITLE), 34.0))
    rows.append(row(2, cellText("A2", "${owner.ifBlank { "Mi cuenta" }}  ·  $period  ·  Generado el $generated", XS_SUBTITLE), 18.0))

    // Indicadores principales (fila 4 títulos, fila 5 valores).
    rows.append(row(4, listOf("INGRESOS", "EGRESOS", "BALANCE", "TASA DE AHORRO", "MOVIMIENTOS")
        .mapIndexed { i, label -> cellText("${'A' + i}4", label, XS_KPI_LABEL) }.joinToString(""), 22.0))
    rows.append(row(5,
        cellFormula("A5", "SUMIFS(Movimientos!\$D:\$D,Movimientos!\$E:\$E,\"Ingreso\")", stats.income, XS_KPI_INCOME) +
            cellFormula("B5", "SUMIFS(Movimientos!\$D:\$D,Movimientos!\$E:\$E,\"Egreso\")", stats.expense, XS_KPI_EXPENSE) +
            cellFormula("C5", "A5-B5", stats.balance, XS_KPI_BALANCE) +
            cellFormula("D5", "IF(A5>0,C5/A5,0)", stats.savingsRate, XS_KPI_PERCENT) +
            cellFormula("E5", "COUNTA(Movimientos!\$A:\$A)-1", stats.items.size.toDouble(), XS_KPI_COUNT), 32.0))
    r = 7

    fun section(title: String, vararg headers: String) {
        rows.append(row(r, cellText("A$r", title, XS_SECTION), 24.0)); r++
        rows.append(row(r, headers.mapIndexed { i, h -> cellText("${'A' + i}$r", h, XS_HEADER) }.joinToString(""), 21.0)); r++
    }

    /** Tabla de gastos por una columna de Movimientos (C = categoría, F = medio). Devuelve el rango de datos. */
    fun expenseTable(totals: List<ReportTotal>, column: Char, empty: String): IntRange? {
        if (totals.isEmpty()) {
            rows.append(row(r, cellText("A$r", empty, XS_NOTE))); r++
            return null
        }
        val start = r
        totals.forEach { total ->
            val share = if (stats.expense > 0) total.amount / stats.expense else 0.0
            rows.append(row(r, cellText("A$r", total.label, XS_TEXT) +
                cellFormula("B$r", "SUMIFS(Movimientos!\$D:\$D,Movimientos!\$$column:\$$column,A$r,Movimientos!\$E:\$E,\"Egreso\")", total.amount, XS_MONEY) +
                cellFormula("C$r", "IF(\$B\$5>0,B$r/\$B\$5,0)", share, XS_PERCENT) +
                cellFormula("D$r", "COUNTIFS(Movimientos!\$$column:\$$column,A$r,Movimientos!\$E:\$E,\"Egreso\")", total.count.toDouble(), XS_COUNT) +
                cellFormula("E$r", "IF(D$r>0,B$r/D$r,0)", total.average, XS_MONEY), 19.0))
            r++
        }
        val end = r - 1
        val sum = totals.sumOf { it.amount }; val count = totals.sumOf { it.count }
        rows.append(row(r, cellText("A$r", "Total", XS_TOTAL_LABEL) +
            cellFormula("B$r", "SUM(B$start:B$end)", sum, XS_TOTAL_MONEY) +
            cellFormula("C$r", "SUM(C$start:C$end)", if (stats.expense > 0) sum / stats.expense else 0.0, XS_TOTAL_PERCENT) +
            cellFormula("D$r", "SUM(D$start:D$end)", count.toDouble(), XS_TOTAL_COUNT) +
            cellFormula("E$r", "IF(D$r>0,B$r/D$r,0)", if (count > 0) sum / count else 0.0, XS_TOTAL_MONEY), 21.0))
        r++
        dataBars += "C$start:C$end"
        return start..end
    }

    section("Gastos por categoría", "Categoría", "Total gastado", "% del gasto", "Movimientos", "Gasto promedio")
    val categoryRows = expenseTable(stats.categories, 'C', "Sin gastos en este periodo.")
    r++

    section("Mes a mes", "Mes", "Ingresos", "Egresos", "Balance", "Tasa de ahorro")
    val monthStart = r
    stats.months.forEach { month ->
        val from = "DATE(${month.year},${month.month},1)"
        val to = if (month.month == 12) "DATE(${month.year + 1},1,1)" else "DATE(${month.year},${month.month + 1},1)"
        fun sum(kind: String) = "SUMIFS(Movimientos!\$D:\$D,Movimientos!\$E:\$E,\"$kind\",Movimientos!\$A:\$A,\">=\"&$from,Movimientos!\$A:\$A,\"<\"&$to)"
        rows.append(row(r, cellText("A$r", month.label, XS_TEXT) +
            cellFormula("B$r", sum("Ingreso"), month.income, XS_MONEY) +
            cellFormula("C$r", sum("Egreso"), month.expense, XS_MONEY) +
            cellFormula("D$r", "B$r-C$r", month.balance, XS_BALANCE) +
            cellFormula("E$r", "IF(B$r>0,D$r/B$r,0)", if (month.income > 0) month.balance / month.income else 0.0, XS_PERCENT), 19.0))
        r++
    }
    val monthEnd = r - 1
    if (stats.months.isNotEmpty()) {
        rows.append(row(r, cellText("A$r", "Total", XS_TOTAL_LABEL) +
            cellFormula("B$r", "SUM(B$monthStart:B$monthEnd)", stats.income, XS_TOTAL_MONEY) +
            cellFormula("C$r", "SUM(C$monthStart:C$monthEnd)", stats.expense, XS_TOTAL_MONEY) +
            cellFormula("D$r", "B$r-C$r", stats.balance, XS_TOTAL_BALANCE) +
            cellFormula("E$r", "IF(B$r>0,D$r/B$r,0)", stats.savingsRate, XS_TOTAL_PERCENT), 21.0))
        r++
    }
    r++

    section("Medios de pago", "Medio", "Total gastado", "% del gasto", "Movimientos", "Gasto promedio")
    val methodRows = expenseTable(stats.methods, 'F', "Sin gastos en este periodo.")
    r++
    rows.append(row(r, cellText("A$r", "Los totales usan fórmulas sobre la hoja Movimientos: si corriges un monto allí, el resumen y los gráficos se actualizan solos.", XS_NOTE)))
    val lastRow = r

    // Gráficos nativos de Excel a la derecha de las tablas (columnas G a O).
    var chartRow = 3
    if (categoryRows != null) {
        charts += ChartSpec(doughnutChart("Gastos por categoría", categoryRows, stats.categories), chartRow, chartRow + 18)
        chartRow += 20
    }
    if (stats.months.isNotEmpty()) {
        charts += ChartSpec(monthChart(monthStart..monthEnd, stats.months), chartRow, chartRow + 18)
        chartRow += 20
    }
    if (methodRows != null) {
        val height = (6 + stats.methods.size * 2).coerceIn(14, 30)
        charts += ChartSpec(methodChart(methodRows, stats.methods), chartRow, chartRow + height)
    }

    return buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"$SHEET_NS\" xmlns:r=\"$OFFICE_REL\">")
        append("<sheetPr><tabColor rgb=\"FF176B43\"/><pageSetUpPr fitToPage=\"1\"/></sheetPr><dimension ref=\"A1:E$lastRow\"/>")
        append("<sheetViews><sheetView showGridLines=\"0\" tabSelected=\"1\" workbookViewId=\"0\"><selection activeCell=\"A1\" sqref=\"A1\"/></sheetView></sheetViews>")
        append("<sheetFormatPr defaultRowHeight=\"18\"/>")
        append("<cols><col min=\"1\" max=\"1\" width=\"28\" customWidth=\"1\"/><col min=\"2\" max=\"3\" width=\"17\" customWidth=\"1\"/>")
        append("<col min=\"4\" max=\"5\" width=\"16\" customWidth=\"1\"/><col min=\"6\" max=\"6\" width=\"3\" customWidth=\"1\"/></cols>")
        append("<sheetData>").append(rows).append("</sheetData>")
        append("<mergeCells count=\"${merges.size}\">").append(merges.joinToString("") { "<mergeCell ref=\"$it\"/>" }).append("</mergeCells>")
        dataBars.forEachIndexed { i, range ->
            append("<conditionalFormatting sqref=\"$range\"><cfRule type=\"dataBar\" priority=\"${i + 1}\"><dataBar>")
            append("<cfvo type=\"num\" val=\"0\"/><cfvo type=\"num\" val=\"1\"/><color rgb=\"FF8CC8A2\"/></dataBar></cfRule></conditionalFormatting>")
        }
        append("<pageMargins left=\"0.4\" right=\"0.4\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>")
        append("<pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
        if (charts.isNotEmpty()) append("<drawing r:id=\"rId1\"/>")
        append("</worksheet>")
    }
}

// ---------------------------------------------------------------- Gráficos (DrawingML)

private fun chartText(value: String) = xmlText(value)

private fun richTitle(title: String) = "<c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz=\"1300\" b=\"1\"/></a:pPr>" +
    "<a:r><a:rPr lang=\"es-PE\" sz=\"1300\" b=\"1\"><a:solidFill><a:srgbClr val=\"183326\"/></a:solidFill></a:rPr><a:t>${chartText(title)}</a:t></a:r></a:p></c:rich></c:tx>" +
    "<c:overlay val=\"0\"/></c:title><c:autoTitleDeleted val=\"0\"/>"

private fun strRef(ref: String, values: List<String>) = "<c:strRef><c:f>$ref</c:f><c:strCache><c:ptCount val=\"${values.size}\"/>" +
    values.mapIndexed { i, v -> "<c:pt idx=\"$i\"><c:v>${chartText(v)}</c:v></c:pt>" }.joinToString("") + "</c:strCache></c:strRef>"

private fun numRef(ref: String, values: List<Double>) = "<c:numRef><c:f>$ref</c:f><c:numCache><c:formatCode>General</c:formatCode><c:ptCount val=\"${values.size}\"/>" +
    values.mapIndexed { i, v -> "<c:pt idx=\"$i\"><c:v>${fixed(v, 2)}</c:v></c:pt>" }.joinToString("") + "</c:numCache></c:numRef>"

private fun solid(hex: String) = "<c:spPr><a:solidFill><a:srgbClr val=\"$hex\"/></a:solidFill></c:spPr>"

private fun chartSpace(body: String) = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
    "<c:chartSpace xmlns:c=\"$CHART_NS\" xmlns:a=\"$DRAWING_NS\" xmlns:r=\"$OFFICE_REL\"><c:roundedCorners val=\"0\"/><c:chart>$body" +
    "<c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart>" +
    "<c:spPr><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill><a:ln w=\"9525\"><a:solidFill><a:srgbClr val=\"D4E4D8\"/></a:solidFill></a:ln></c:spPr>" +
    "<c:txPr><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz=\"900\"><a:solidFill><a:srgbClr val=\"536A5A\"/></a:solidFill>" +
    "<a:latin typeface=\"Calibri\"/></a:defRPr></a:pPr><a:endParaRPr lang=\"es-PE\"/></a:p></c:txPr></c:chartSpace>"

private const val NO_LABEL_FLAGS = "<c:showLegendKey val=\"0\"/><c:showVal val=\"0\"/><c:showCatName val=\"0\"/><c:showSerName val=\"0\"/><c:showPercent val=\"0\"/><c:showBubbleSize val=\"0\"/>"

private fun axes(catId: Int, valId: Int, horizontal: Boolean) =
    "<c:catAx><c:axId val=\"$catId\"/><c:scaling><c:orientation val=\"${if (horizontal) "maxMin" else "minMax"}\"/></c:scaling><c:delete val=\"0\"/>" +
        "<c:axPos val=\"${if (horizontal) "l" else "b"}\"/><c:numFmt formatCode=\"General\" sourceLinked=\"0\"/><c:majorTickMark val=\"none\"/><c:minorTickMark val=\"none\"/>" +
        "<c:tickLblPos val=\"nextTo\"/><c:spPr><a:ln w=\"9525\"><a:solidFill><a:srgbClr val=\"D4E4D8\"/></a:solidFill></a:ln></c:spPr>" +
        "<c:crossAx val=\"$valId\"/><c:crosses val=\"autoZero\"/><c:auto val=\"1\"/><c:lblAlgn val=\"ctr\"/><c:lblOffset val=\"100\"/><c:noMultiLvlLbl val=\"0\"/></c:catAx>" +
        "<c:valAx><c:axId val=\"$valId\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:delete val=\"0\"/><c:axPos val=\"${if (horizontal) "b" else "l"}\"/>" +
        "<c:majorGridlines><c:spPr><a:ln w=\"6350\"><a:solidFill><a:srgbClr val=\"E6EEE8\"/></a:solidFill></a:ln></c:spPr></c:majorGridlines>" +
        "<c:numFmt formatCode=\"&quot;S/ &quot;#,##0\" sourceLinked=\"0\"/><c:majorTickMark val=\"none\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/>" +
        "<c:spPr><a:ln><a:noFill/></a:ln></c:spPr><c:crossAx val=\"$catId\"/><c:crosses val=\"${if (horizontal) "max" else "autoZero"}\"/><c:crossBetween val=\"between\"/></c:valAx>"

private fun doughnutChart(title: String, rows: IntRange, totals: List<ReportTotal>): String {
    val points = totals.indices.joinToString("") { i ->
        "<c:dPt><c:idx val=\"$i\"/><c:bubble3D val=\"0\"/><c:spPr><a:solidFill><a:srgbClr val=\"${categoryHex(totals[i].label, i)}\"/></a:solidFill>" +
            "<a:ln w=\"19050\"><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill></a:ln></c:spPr></c:dPt>"
    }
    val labels = "<c:dLbls><c:numFmt formatCode=\"0%\" sourceLinked=\"0\"/><c:spPr><a:noFill/><a:ln><a:noFill/></a:ln></c:spPr>" +
        "<c:txPr><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz=\"900\" b=\"1\"><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill></a:defRPr></a:pPr><a:endParaRPr lang=\"es-PE\"/></a:p></c:txPr>" +
        "<c:showLegendKey val=\"0\"/><c:showVal val=\"0\"/><c:showCatName val=\"0\"/><c:showSerName val=\"0\"/><c:showPercent val=\"1\"/><c:showBubbleSize val=\"0\"/><c:showLeaderLines val=\"0\"/></c:dLbls>"
    return chartSpace(richTitle(title) + "<c:plotArea><c:layout/><c:doughnutChart><c:varyColors val=\"1\"/>" +
        "<c:ser><c:idx val=\"0\"/><c:order val=\"0\"/><c:tx>${strRef("Resumen!\$B\$${rows.first - 1}", listOf("Total gastado"))}</c:tx>$points$labels" +
        "<c:cat>${strRef("Resumen!\$A\$${rows.first}:\$A\$${rows.last}", totals.map { it.label })}</c:cat>" +
        "<c:val>${numRef("Resumen!\$B\$${rows.first}:\$B\$${rows.last}", totals.map { it.amount })}</c:val></c:ser>" +
        "<c:dLbls>$NO_LABEL_FLAGS<c:showLeaderLines val=\"0\"/></c:dLbls><c:firstSliceAng val=\"0\"/><c:holeSize val=\"55\"/></c:doughnutChart></c:plotArea>" +
        "<c:legend><c:legendPos val=\"r\"/><c:overlay val=\"0\"/></c:legend>")
}

private fun monthChart(rows: IntRange, months: List<ReportMonth>): String {
    val header = rows.first - 1
    fun series(index: Int, column: Char, name: String, color: String, values: List<Double>) =
        "<c:ser><c:idx val=\"$index\"/><c:order val=\"$index\"/><c:tx>${strRef("Resumen!\$$column\$$header", listOf(name))}</c:tx>${solid(color)}" +
            "<c:invertIfNegative val=\"0\"/><c:cat>${strRef("Resumen!\$A\$${rows.first}:\$A\$${rows.last}", months.map { it.label })}</c:cat>" +
            "<c:val>${numRef("Resumen!\$$column\$${rows.first}:\$$column\$${rows.last}", values)}</c:val></c:ser>"
    return chartSpace(richTitle("Ingresos y egresos por mes") + "<c:plotArea><c:layout/><c:barChart><c:barDir val=\"col\"/><c:grouping val=\"clustered\"/><c:varyColors val=\"0\"/>" +
        series(0, 'B', "Ingresos", "176B43", months.map { it.income }) + series(1, 'C', "Egresos", "B84754", months.map { it.expense }) +
        "<c:gapWidth val=\"70\"/><c:overlap val=\"-8\"/><c:axId val=\"5001\"/><c:axId val=\"5002\"/></c:barChart>${axes(5001, 5002, false)}</c:plotArea>" +
        "<c:legend><c:legendPos val=\"t\"/><c:overlay val=\"0\"/></c:legend>")
}

private fun methodChart(rows: IntRange, totals: List<ReportTotal>): String {
    val labels = "<c:dLbls><c:numFmt formatCode=\"&quot;S/ &quot;#,##0.00\" sourceLinked=\"0\"/><c:spPr><a:noFill/><a:ln><a:noFill/></a:ln></c:spPr>" +
        "<c:txPr><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz=\"850\" b=\"1\"><a:solidFill><a:srgbClr val=\"183326\"/></a:solidFill></a:defRPr></a:pPr><a:endParaRPr lang=\"es-PE\"/></a:p></c:txPr>" +
        "<c:dLblPos val=\"outEnd\"/><c:showLegendKey val=\"0\"/><c:showVal val=\"1\"/><c:showCatName val=\"0\"/><c:showSerName val=\"0\"/><c:showPercent val=\"0\"/><c:showBubbleSize val=\"0\"/></c:dLbls>"
    return chartSpace(richTitle("Gastos por medio de pago") + "<c:plotArea><c:layout/><c:barChart><c:barDir val=\"bar\"/><c:grouping val=\"clustered\"/><c:varyColors val=\"0\"/>" +
        "<c:ser><c:idx val=\"0\"/><c:order val=\"0\"/><c:tx>${strRef("Resumen!\$B\$${rows.first - 1}", listOf("Total gastado"))}</c:tx>${solid("8A76D4")}" +
        "<c:invertIfNegative val=\"0\"/>$labels<c:cat>${strRef("Resumen!\$A\$${rows.first}:\$A\$${rows.last}", totals.map { it.label })}</c:cat>" +
        "<c:val>${numRef("Resumen!\$B\$${rows.first}:\$B\$${rows.last}", totals.map { it.amount })}</c:val></c:ser>" +
        "<c:gapWidth val=\"60\"/><c:axId val=\"6001\"/><c:axId val=\"6002\"/></c:barChart>${axes(6001, 6002, true)}</c:plotArea>")
}

private fun categoryHex(label: String, index: Int): String =
    categories.firstOrNull { it.name.equals(label, true) }?.let { (it.color.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase() }
        ?: ChartPalette[index % ChartPalette.size]

// ---------------------------------------------------------------- Estilos

private fun workbookStyles(): String {
    fun font(size: Int, color: String, bold: Boolean = false, italic: Boolean = false) =
        "<font>${if (bold) "<b/>" else ""}${if (italic) "<i/>" else ""}<sz val=\"$size\"/><color rgb=\"FF$color\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"
    fun fill(color: String) = "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF$color\"/><bgColor indexed=\"64\"/></patternFill></fill>"
    fun xf(numFmt: Int, font: Int, fill: Int, border: Int, horizontal: String? = null, indent: Int = 0) =
        "<xf numFmtId=\"$numFmt\" fontId=\"$font\" fillId=\"$fill\" borderId=\"$border\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
            "<alignment vertical=\"center\"${horizontal?.let { " horizontal=\"$it\"" } ?: ""}${if (indent > 0) " indent=\"$indent\"" else ""}/></xf>"
    val fonts = listOf(
        font(11, "183326"), font(11, "FFFFFF", bold = true), font(11, "176B43", bold = true), font(11, "B84754", bold = true),
        font(18, "115B38", bold = true), font(10, "536A5A"), font(9, "536A5A", bold = true), font(15, "176B43", bold = true),
        font(15, "B84754", bold = true), font(15, "183326", bold = true), font(12, "115B38", bold = true), font(11, "183326", bold = true),
        font(9, "536A5A", italic = true), font(11, "B84754"),
    )
    val fills = listOf("<fill><patternFill patternType=\"none\"/></fill>", "<fill><patternFill patternType=\"gray125\"/></fill>",
        fill("176B43"), fill("E5F3E9"), fill("FBE7E9"), fill("E2F2E7"))
    val borders = listOf(
        "<border><left/><right/><top/><bottom/><diagonal/></border>",
        "<border><left/><right/><top/><bottom style=\"thin\"><color rgb=\"FFD4E4D8\"/></bottom><diagonal/></border>",
        "<border><left/><right/><top style=\"medium\"><color rgb=\"FF176B43\"/></top><bottom/><diagonal/></border>",
        "<border><left style=\"thin\"><color rgb=\"FFD4E4D8\"/></left><right style=\"thin\"><color rgb=\"FFD4E4D8\"/></right>" +
            "<top style=\"thin\"><color rgb=\"FFD4E4D8\"/></top><bottom style=\"thin\"><color rgb=\"FFD4E4D8\"/></bottom><diagonal/></border>",
    )
    val cellXfs = listOf(
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>",
        xf(0, 1, 2, 0, indent = 1),          // XS_HEADER
        xf(164, 0, 0, 1),                    // XS_MONEY
        xf(165, 0, 0, 1, "center"),          // XS_DATE
        xf(0, 0, 0, 1),                      // XS_TEXT
        xf(164, 2, 0, 1),                    // XS_INCOME
        xf(164, 13, 0, 1),                   // XS_EXPENSE
        xf(0, 2, 5, 1, "center"),            // XS_INCOME_TAG
        xf(0, 3, 4, 1, "center"),            // XS_EXPENSE_TAG
        xf(0, 4, 0, 0),                      // XS_TITLE
        xf(0, 5, 0, 0),                      // XS_SUBTITLE
        xf(0, 6, 3, 3, "center"),            // XS_KPI_LABEL
        xf(164, 7, 0, 3, "center"),          // XS_KPI_INCOME
        xf(164, 8, 0, 3, "center"),          // XS_KPI_EXPENSE
        xf(167, 9, 0, 3, "center"),          // XS_KPI_BALANCE
        xf(166, 9, 0, 3, "center"),          // XS_KPI_PERCENT
        xf(1, 9, 0, 3, "center"),            // XS_KPI_COUNT
        xf(0, 10, 0, 0),                     // XS_SECTION
        xf(166, 0, 0, 1),                    // XS_PERCENT
        xf(1, 0, 0, 1, "center"),            // XS_COUNT
        xf(167, 0, 0, 1),                    // XS_BALANCE
        xf(0, 11, 3, 2),                     // XS_TOTAL_LABEL
        xf(164, 11, 3, 2),                   // XS_TOTAL_MONEY
        xf(166, 11, 3, 2),                   // XS_TOTAL_PERCENT
        xf(1, 11, 3, 2, "center"),           // XS_TOTAL_COUNT
        xf(0, 12, 0, 0),                     // XS_NOTE
        xf(167, 11, 3, 2),                   // XS_TOTAL_BALANCE
    )
    return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"$SHEET_NS\">" +
        "<numFmts count=\"4\"><numFmt numFmtId=\"164\" formatCode=\"&quot;S/ &quot;#,##0.00\"/><numFmt numFmtId=\"165\" formatCode=\"dd/mm/yyyy\"/>" +
        "<numFmt numFmtId=\"166\" formatCode=\"0.0%\"/><numFmt numFmtId=\"167\" formatCode=\"&quot;S/ &quot;#,##0.00;[Red]-&quot;S/ &quot;#,##0.00\"/></numFmts>" +
        "<fonts count=\"${fonts.size}\">${fonts.joinToString("")}</fonts><fills count=\"${fills.size}\">${fills.joinToString("")}</fills>" +
        "<borders count=\"${borders.size}\">${borders.joinToString("")}</borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"${cellXfs.size}\">${cellXfs.joinToString("")}</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "<dxfs count=\"1\"><dxf><fill><patternFill patternType=\"solid\"><bgColor rgb=\"FFF5FAF6\"/></patternFill></fill></dxf></dxfs>" +
        "<tableStyles count=\"0\" defaultTableStyle=\"TableStyleMedium2\" defaultPivotStyle=\"PivotStyleLight16\"/></styleSheet>"
}
