package com.example.dinex

import kotlin.math.abs

internal fun normalizedColumn(value: String): String = value.trim().trim('\uFEFF').lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
    .replace(Regex("\\s+"), " ")

internal fun csvField(value: String): String {
    // Excel must treat user-supplied descriptions as text, not spreadsheet formulas.
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}

internal fun parseCsvRows(text: String): List<List<String>> {
    val source = text.removePrefix("\uFEFF")
    val sample = source.lineSequence().take(12).joinToString("\n")
    fun countDelimiter(c: Char): Int {
        var quoted = false; var count = 0
        sample.forEach { if (it == '"') quoted = !quoted else if (!quoted && it == c) count++ }
        return count
    }
    val separator = listOf(';', '\t', ',').maxBy { countDelimiter(it) }
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false; var index = 0
    fun finishCell() { row.add(cell.toString().trim()); cell.clear(); require(row.size <= 1000) { "Demasiadas columnas en el archivo." } }
    fun finishRow() {
        finishCell()
        if (row.any(String::isNotBlank)) rows.add(row.toList())
        row.clear()
        require(rows.size <= MAX_IMPORT_ROWS + 100) { "Importa como máximo $MAX_IMPORT_ROWS movimientos por archivo." }
    }
    while (index < source.length) {
        val c = source[index++]
        when {
            c == '"' && quoted && index < source.length && source[index] == '"' -> { cell.append('"'); index++ }
            c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
            !quoted && c == separator -> finishCell()
            !quoted && (c == '\n' || c == '\r') -> { if (c == '\r' && index < source.length && source[index] == '\n') index++; finishRow() }
            else -> cell.append(c)
        }
    }
    require(!quoted) { "El CSV tiene una comilla sin cerrar. Revisa el archivo." }
    if (cell.isNotEmpty() || row.isNotEmpty()) finishRow()
    return rows
}

internal fun parseImportAmount(raw: String): Double? {
    if (Regex("USD|US\\$|\\$|EUR|€", RegexOption.IGNORE_CASE).containsMatchIn(raw)) return null
    var value = raw.trim().replace(Regex("S/\\.?|PEN|soles?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[\\s\u00A0]"), "")
    val negative = value.startsWith('(') && value.endsWith(')')
    if (negative) value = value.substring(1, value.lastIndex)
    if (',' in value && '.' in value) {
        value = if (value.lastIndexOf(',') > value.lastIndexOf('.')) value.replace(".", "").replace(',', '.')
        else value.replace(",", "")
    } else {
        val delimiter = if (',' in value) ',' else '.'
        val count = value.count { it == delimiter }
        if (count > 0) {
            val decimalDigits = value.length - value.lastIndexOf(delimiter) - 1
            value = if (decimalDigits in 1..2) {
                val last = value.lastIndexOf(delimiter)
                value.substring(0, last).replace(delimiter.toString(), "") + "." + value.substring(last + 1)
            } else if (decimalDigits == 3) value.replace(delimiter.toString(), "") else return null
        }
    }
    return value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { if (negative) -abs(it) else it }
}

internal fun parseImportDate(raw: String): DinexDate? {
    val value = normalizedColumn(raw)
    if (value.isBlank()) return civilDateFromEpochDays()
    val numeric = value.toDoubleOrNull()
    if (numeric != null && numeric in 1.0..109_574.0) return civilDateFromEpochDays(numeric.toInt() - 25_569)
    val parts = Regex("^(\\d{1,4})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})").find(value)
    if (parts != null) {
        val (a, b, c) = parts.destructured
        val year = if (a.length == 4) a.toInt() else c.toInt().let { if (it < 100) it + 2000 else it }
        val month = b.toInt()
        val day = if (a.length == 4) c.toInt() else a.toInt()
        return if (validDate(year, month, day)) DinexDate(year, month, day) else null
    }
    val textual = Regex("^(\\d{1,2})\\s+(?:de\\s+)?([a-z]+)\\s+(?:de\\s+)?(\\d{4})").find(value)
    if (textual != null) {
        val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
        val month = if (textual.groupValues[2].startsWith("set")) 9 else months.indexOf(textual.groupValues[2].take(3)) + 1
        val year = textual.groupValues[3].toInt(); val day = textual.groupValues[1].toInt()
        return if (validDate(year, month, day)) DinexDate(year, month, day) else null
    }
    return null
}

internal fun reviewImportedRows(rows: List<List<String>>): ImportReview {
    if (rows.isEmpty()) return ImportReview(emptyList())
    val headerIndex = rows.take(50).indexOfFirst { row ->
        val keys = row.map(::normalizedColumn)
        keys.any { it.contains("monto") || it.contains("importe") || it == "amount" || it == "total" || it.contains("cargo") || it.contains("debito") || it.contains("abono") } &&
            keys.any { it.contains("fecha") || it == "date" || it.contains("descrip") || it.contains("concept") || it.contains("tipo") }
    }
    val header = if (headerIndex >= 0) rows[headerIndex].map(::normalizedColumn) else emptyList()
    val data = if (headerIndex >= 0) rows.drop(headerIndex + 1) else rows
    require(data.size <= MAX_IMPORT_ROWS) { "El archivo supera $MAX_IMPORT_ROWS filas." }
    fun column(vararg terms: String): Int = header.indexOfFirst { h -> terms.any { h == it || h.contains(it) } }
    val dateColumn = column("fecha", "date", "dia")
    val titleColumn = column("descripcion", "concepto", "detalle", "comercio", "nombre", "destinatario", "destino", "mensaje")
    val amountColumn = column("monto", "importe", "amount", "valor", "total")
    val debitColumn = column("cargo", "debito")
    val creditColumn = column("abono", "credito")
    val typeColumn = column("tipo", "operacion", "movimiento", "transaccion")
    val categoryColumn = column("categoria", "category")
    val methodColumn = column("medio", "metodo", "canal")
    val locationColumn = column("ubicacion", "location")
    var rejected = 0; var inferredType = false; var inferredDate = false
    val parsed = data.mapIndexedNotNull { index, row ->
        fun value(col: Int, fallback: Int = -1): String = row.getOrNull(if (header.isEmpty()) fallback else col).orEmpty().trim()
        val dateText = value(dateColumn, 0)
        val date = parseImportDate(dateText)
        val amount = parseImportAmount(value(amountColumn, 3))
            ?: parseImportAmount(value(creditColumn)).takeIf { it != 0.0 }
            ?: parseImportAmount(value(debitColumn))?.let { -abs(it) }
        if (date == null || amount == null || amount == 0.0) { rejected++; return@mapIndexedNotNull null }
        if (dateText.isBlank()) inferredDate = true
        val kind = normalizedColumn(value(typeColumn, 4))
        val description = normalizedColumn(value(titleColumn, 1))
        val evidence = "$kind $description"
        val incoming = listOf("ingreso", "abono", "entrada", "deposit", "recib", "credito", "income", "te yapearon", "te pago", "te pagaron", "te yapeo", "te yapearon")
        val outgoing = listOf("egreso", "gasto", "salida", "envi", "pago", "pagast", "compra", "retiro", "debito", "cargo", "expense", "yapeaste")
        val credit = parseImportAmount(value(creditColumn))
        val debit = parseImportAmount(value(debitColumn))
        val income = when {
            incoming.any(kind::contains) -> true
            outgoing.any(kind::contains) -> false
            credit != null && credit > 0 && (debit == null || debit == 0.0) -> true
            debit != null && debit > 0 -> false
            incoming.any(description::contains) -> true
            outgoing.any(description::contains) -> false
            amount < 0 -> false
            else -> { inferredType = true; false }
        }
        val title = value(titleColumn, 1).removePrefix("'").ifBlank { "Movimiento importado" }.take(250)
        val category = value(categoryColumn, 2).removePrefix("'").ifBlank { if (income) "Ingreso" else "Otros" }
        Movement(index + 1, title, category.replaceFirstChar { it.uppercase() }, abs(amount), income, date.day,
            value(methodColumn, 5).removePrefix("'").ifBlank { "Importado" }, value(locationColumn, 6).removePrefix("'"), date.month, date.year)
    }
    val warnings = buildList {
        if (header.isEmpty()) add("Sin encabezados: usamos fecha, descripción, categoría, monto, tipo y medio, en ese orden.")
        if (inferredType) add("Algunas filas no indican ingreso o gasto: se proponen como gastos. Revisa antes de importar.")
        if (inferredDate) add("Las filas sin fecha usan la fecha de hoy.")
        if (rejected > 0) add("$rejected filas sin monto o fecha válidos quedaron fuera de la importación.")
    }
    return ImportReview(parsed, rejected, warnings)
}

internal fun reviewImportFile(file: TransferFile): ImportReview {
    require(file.bytes.size <= MAX_TRANSFER_BYTES) { "El archivo supera 20 MB." }
    val extension = file.name.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "csv", "tsv", "txt" -> reviewImportedRows(parseCsvRows(decodeCsvText(file.bytes)))
        "xlsx" -> {
            val worksheets = readWorkbookRows(file.bytes)
            worksheets.map(::reviewImportedRows).firstOrNull { it.movements.isNotEmpty() }
                ?: ImportReview(emptyList(), warnings = listOf("No encontramos columnas de movimientos en las hojas del Excel."))
        }
        "xls" -> error("Este Excel usa el formato antiguo .xls. Guárdalo como .xlsx o CSV e impórtalo de nuevo.")
        else -> error("Selecciona un archivo Excel (.xlsx), CSV o TSV.")
    }
}

private fun decodeCsvText(bytes: ByteArray): String {
    if (bytes.size >= 2 && ((bytes[0].toInt() and 255) == 255 || (bytes[0].toInt() and 255) == 254)) {
        val little = (bytes[0].toInt() and 255) == 255
        return buildString { var i = 2; while (i + 1 < bytes.size) {
            val a = bytes[i++].toInt() and 255; val b = bytes[i++].toInt() and 255
            append((if (little) a or (b shl 8) else b or (a shl 8)).toChar())
        } }
    }
    return bytes.decodeToString().let { text -> if ('\uFFFD' !in text) text else bytes.map { (it.toInt() and 255).toChar() }.joinToString("") }
}
