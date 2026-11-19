package com.example.dinex

internal class BinaryBuffer(initial: Int = 1024) {
    private var storage = ByteArray(initial)
    var size = 0; private set
    fun byte(value: Int) {
        if (size == storage.size) storage = storage.copyOf((storage.size * 2).coerceAtLeast(16))
        storage[size++] = value.toByte()
    }
    fun bytes(value: ByteArray) { value.forEach { byte(it.toInt()) } }
    fun little(value: Int, count: Int) { repeat(count) { byte(value ushr (it * 8)) } }
    fun array() = storage.copyOf(size)
    fun copyBack(distance: Int, count: Int, limit: Int) {
        require(distance in 1..size && size + count <= limit) { "Excel comprimido inválido o demasiado grande." }
        repeat(count) { byte(storage[size - distance].toInt()) }
    }
}

private fun ByteArray.uint(offset: Int, count: Int): Int {
    require(offset >= 0 && offset + count <= size) { "Archivo Excel incompleto." }
    var value = 0; repeat(count) { value = value or ((this[offset + it].toInt() and 255) shl (8 * it)) }
    return value
}

internal fun crc32(bytes: ByteArray): Int {
    var crc = -1
    bytes.forEach { b ->
        crc = crc xor (b.toInt() and 255)
        repeat(8) { crc = (crc ushr 1) xor (if ((crc and 1) != 0) 0xEDB88320.toInt() else 0) }
    }
    return crc.inv()
}

/** ZIP/DEFLATE reader shared by all targets. It never extracts files to disk. */
internal fun readZip(bytes: ByteArray): Map<String, ByteArray> {
    require(bytes.size in 22..MAX_TRANSFER_BYTES) { "Excel vacío, incompleto o mayor de 20 MB." }
    val end = (bytes.size - 22 downTo (bytes.size - 65_557).coerceAtLeast(0))
        .firstOrNull { bytes.uint(it, 4) == 0x06054b50 } ?: error("El archivo no es un Excel .xlsx válido.")
    val entries = bytes.uint(end + 10, 2)
    require(entries in 1..1000 && bytes.uint(end + 4, 2) == 0) { "Excel multipartes o con demasiados archivos." }
    var cursor = bytes.uint(end + 16, 4)
    var expanded = 0
    val result = mutableMapOf<String, ByteArray>()
    repeat(entries) {
        require(bytes.uint(cursor, 4) == 0x02014b50) { "Índice del Excel dañado." }
        val flags = bytes.uint(cursor + 8, 2)
        val method = bytes.uint(cursor + 10, 2)
        val checksum = bytes.uint(cursor + 16, 4)
        val packedSize = bytes.uint(cursor + 20, 4)
        val unpackedSize = bytes.uint(cursor + 24, 4)
        val nameSize = bytes.uint(cursor + 28, 2)
        val extraSize = bytes.uint(cursor + 30, 2)
        val commentSize = bytes.uint(cursor + 32, 2)
        val local = bytes.uint(cursor + 42, 4)
        require(nameSize <= 1024 && unpackedSize in 0..MAX_TRANSFER_BYTES && packedSize in 0..bytes.size) { "Contenido del Excel demasiado grande." }
        expanded += unpackedSize
        require(expanded <= 40 * 1024 * 1024 && flags and 1 == 0) { "Excel cifrado o contenido demasiado grande." }
        val name = bytes.copyOfRange(cursor + 46, cursor + 46 + nameSize).decodeToString()
        cursor += 46 + nameSize + extraSize + commentSize
        if (!name.endsWith(".xml")) return@repeat
        require(bytes.uint(local, 4) == 0x04034b50) { "Contenido del Excel dañado." }
        val start = local + 30 + bytes.uint(local + 26, 2) + bytes.uint(local + 28, 2)
        require(start >= 0 && start.toLong() + packedSize <= bytes.size) { "Excel truncado." }
        val compressed = bytes.copyOfRange(start, start + packedSize)
        val content = when (method) {
            0 -> compressed
            8 -> inflateWorkbook(compressed, unpackedSize)
            else -> error("Este Excel usa una compresión no compatible.")
        }
        require(content.size == unpackedSize && crc32(content) == checksum) { "El Excel está dañado. Vuelve a descargarlo." }
        result[name] = content
    }
    return result
}

private class DeflateBits(private val bytes: ByteArray) {
    var position = 0
    fun read(count: Int): Int {
        require(position.toLong() + count <= bytes.size.toLong() * 8) { "Excel comprimido incompleto." }
        var value = 0
        repeat(count) { i -> value = value or (((bytes[position / 8].toInt() ushr (position % 8)) and 1) shl i); position++ }
        return value
    }
    fun align() { position = (position + 7) / 8 * 8 }
}

private class DeflateTree(lengths: List<Int>) {
    private val symbols = mutableMapOf<Int, Int>()
    private val max = lengths.maxOrNull() ?: 0
    init {
        require(max <= 15) { "Código Huffman inválido." }
        val counts = IntArray(16); lengths.forEach { if (it > 0) counts[it]++ }
        val next = IntArray(16); var code = 0
        for (length in 1..15) { code = (code + counts[length - 1]) shl 1; next[length] = code }
        lengths.forEachIndexed { symbol, length -> if (length > 0) {
            val canonical = next[length]++; var reversed = 0
            repeat(length) { bit -> reversed = (reversed shl 1) or ((canonical ushr bit) and 1) }
            symbols[(1 shl length) or reversed] = symbol
        } }
    }
    fun decode(bits: DeflateBits): Int {
        var value = 0
        for (length in 1..max) {
            value = value or (bits.read(1) shl (length - 1))
            symbols[(1 shl length) or value]?.let { return it }
        }
        error("Código del Excel comprimido inválido.")
    }
}

private fun inflateWorkbook(bytes: ByteArray, limit: Int): ByteArray {
    val bits = DeflateBits(bytes); val output = BinaryBuffer(limit.coerceAtMost(4096).coerceAtLeast(16))
    val lengthBase = intArrayOf(3,4,5,6,7,8,9,10,11,13,15,17,19,23,27,31,35,43,51,59,67,83,99,115,131,163,195,227,258)
    val lengthExtra = intArrayOf(0,0,0,0,0,0,0,0,1,1,1,1,2,2,2,2,3,3,3,3,4,4,4,4,5,5,5,5,0)
    val distanceBase = intArrayOf(1,2,3,4,5,7,9,13,17,25,33,49,65,97,129,193,257,385,513,769,1025,1537,2049,3073,4097,6145,8193,12289,16385,24577)
    val distanceExtra = intArrayOf(0,0,0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,11,11,12,12,13,13)
    var final: Int
    do {
        final = bits.read(1)
        when (val kind = bits.read(2)) {
            0 -> {
                bits.align(); val count = bits.read(16); val inverse = bits.read(16)
                require(count xor inverse == 65535 && output.size + count <= limit) { "Bloque Excel inválido." }
                repeat(count) { output.byte(bits.read(8)) }
            }
            1, 2 -> {
                val literal: DeflateTree; val distance: DeflateTree
                if (kind == 1) {
                    literal = DeflateTree(List(288) { when (it) { in 0..143 -> 8; in 144..255 -> 9; in 256..279 -> 7; else -> 8 } })
                    distance = DeflateTree(List(32) { 5 })
                } else {
                    val literals = bits.read(5) + 257; val distances = bits.read(5) + 1; val codes = bits.read(4) + 4
                    val order = intArrayOf(16,17,18,0,8,7,9,6,10,5,11,4,12,3,13,2,14,1,15)
                    val codeLengths = MutableList(19) { 0 }; repeat(codes) { codeLengths[order[it]] = bits.read(3) }
                    val tree = DeflateTree(codeLengths); val lengths = mutableListOf<Int>()
                    while (lengths.size < literals + distances) {
                        val symbol = tree.decode(bits)
                        when (symbol) {
                            in 0..15 -> lengths.add(symbol)
                            16 -> { require(lengths.isNotEmpty()); val previous = lengths.last(); repeat(bits.read(2) + 3) { lengths.add(previous) } }
                            17 -> repeat(bits.read(3) + 3) { lengths.add(0) }
                            18 -> repeat(bits.read(7) + 11) { lengths.add(0) }
                        }
                        require(lengths.size <= literals + distances) { "Árbol del Excel inválido." }
                    }
                    literal = DeflateTree(lengths.take(literals)); distance = DeflateTree(lengths.drop(literals))
                }
                while (true) {
                    val symbol = literal.decode(bits)
                    when {
                        symbol < 256 -> { require(output.size < limit) { "Excel demasiado grande." }; output.byte(symbol) }
                        symbol == 256 -> break
                        symbol in 257..285 -> {
                            val lengthIndex = symbol - 257; val length = lengthBase[lengthIndex] + bits.read(lengthExtra[lengthIndex])
                            val dist = distance.decode(bits); require(dist < 30) { "Distancia comprimida inválida." }
                            output.copyBack(distanceBase[dist] + bits.read(distanceExtra[dist]), length, limit)
                        }
                        else -> error("Bloque Excel inválido.")
                    }
                }
            }
            else -> error("Compresión Excel inválida.")
        }
    } while (final == 0)
    return output.array()
}

internal fun writeZip(files: Map<String, String>): ByteArray {
    val data = BinaryBuffer(); val directory = BinaryBuffer()
    files.forEach { (name, text) ->
        val filename = name.encodeToByteArray(); val content = text.encodeToByteArray(); val checksum = crc32(content); val offset = data.size
        data.little(0x04034b50, 4); data.little(20, 2); data.little(0, 2); data.little(0, 2)
        data.little(0, 2); data.little(33, 2); data.little(checksum, 4); data.little(content.size, 4); data.little(content.size, 4)
        data.little(filename.size, 2); data.little(0, 2); data.bytes(filename); data.bytes(content)
        directory.little(0x02014b50, 4); directory.little(20, 2); directory.little(20, 2)
        directory.little(0, 2); directory.little(0, 2); directory.little(0, 2); directory.little(33, 2)
        directory.little(checksum, 4); directory.little(content.size, 4); directory.little(content.size, 4)
        directory.little(filename.size, 2); repeat(4) { directory.little(0, 2) }; directory.little(0, 4)
        directory.little(offset, 4); directory.bytes(filename)
    }
    val offset = data.size; data.bytes(directory.array()); data.little(0x06054b50, 4)
    data.little(0, 2); data.little(0, 2); data.little(files.size, 2); data.little(files.size, 2)
    data.little(directory.size, 4); data.little(offset, 4); data.little(0, 2)
    return data.array()
}

internal fun xmlText(value: String): String = value.filter { it.code >= 32 || it == '\n' || it == '\t' }
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

private fun unescapeXml(value: String): String = value.replace(Regex("&#(x[0-9a-fA-F]+|[0-9]+);")) {
    val n = it.groupValues[1]; val code = if (n.startsWith('x')) n.drop(1).toIntOrNull(16) else n.toIntOrNull()
    when { code == null || code !in 0..0x10ffff -> ""; code <= 0xffff -> code.toChar().toString()
        else -> { val cp = code - 0x10000; "${(0xd800 + (cp shr 10)).toChar()}${(0xdc00 + (cp and 1023)).toChar()}" } }
}.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

private fun tags(xml: String, tag: String) = Regex("<(?:[\\w]+:)?$tag(?:\\s[^>]*)?>([\\s\\S]*?)</(?:[\\w]+:)?$tag>").findAll(xml)
private fun attribute(xml: String, name: String) = Regex("\\b$name=['\"]([^'\"]*)['\"]").find(xml)?.groupValues?.get(1)

internal fun readWorkbookRows(bytes: ByteArray): List<List<List<String>>> {
    val files = readZip(bytes)
    require("xl/workbook.xml" in files) { "No encontramos un libro Excel válido." }
    fun text(name: String) = files[name]?.decodeToString().orEmpty().also {
        require(!it.contains("<!DOCTYPE", true) && !it.contains("<!ENTITY", true)) { "Excel con XML no compatible." }
    }
    val shared = tags(text("xl/sharedStrings.xml"), "si").map { item ->
        tags(item.groupValues[1], "t").joinToString("") { unescapeXml(it.groupValues[1]) }
    }.toList()
    val workbook = text("xl/workbook.xml")
    val oldDateSystem = Regex("date1904=['\"](?:1|true)['\"]").containsMatchIn(workbook)
    val styles = text("xl/styles.xml")
    val customDateIds = Regex("<numFmt\\b[^>]*>").findAll(styles).mapNotNull { item ->
        val id = attribute(item.value, "numFmtId")?.toIntOrNull()
        val format = attribute(item.value, "formatCode").orEmpty().replace(Regex("\"[^\"]*\"|\\[[^\\]]*\\]"), "")
        if (id != null && ('y' in format.lowercase() || 'd' in format.lowercase())) id else null
    }.toSet()
    val dateStyles = tags(styles, "cellXfs").firstOrNull()?.groupValues?.get(1)?.let { cellXfs ->
        Regex("<xf\\b[^>]*>").findAll(cellXfs).mapIndexedNotNull { i, xf ->
            val id = attribute(xf.value, "numFmtId")?.toIntOrNull()
            if (id in 14..22 || id in 45..47 || id in customDateIds) i else null
        }.toSet()
    }.orEmpty()
    return files.keys.filter { Regex("xl/worksheets/[^/]+\\.xml").matches(it) }.sorted().take(20).map { sheet ->
        val rows = mutableListOf<List<String>>()
        tags(text(sheet), "row").forEach { row ->
            require(rows.size <= MAX_IMPORT_ROWS + 100) { "La hoja supera $MAX_IMPORT_ROWS filas." }
            val cells = mutableListOf<String>()
            tags(row.groupValues[1], "c").forEach { cell ->
                val ref = attribute(cell.value.substringBefore('>'), "r").orEmpty().takeWhile(Char::isLetter)
                var column = 0; ref.forEach { column = column * 26 + it.uppercaseChar().code - 'A'.code + 1 }
                val index = if (column > 0) column - 1 else cells.size
                require(index < 1000) { "La hoja contiene demasiadas columnas." }
                while (cells.size <= index) cells.add("")
                val type = attribute(cell.value.substringBefore('>'), "t")
                val raw = tags(cell.groupValues[1], "v").firstOrNull()?.groupValues?.get(1).orEmpty()
                val style = attribute(cell.value.substringBefore('>'), "s")?.toIntOrNull()
                cells[index] = when {
                    type == "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                    type == "inlineStr" -> tags(cell.groupValues[1], "t").joinToString("") { unescapeXml(it.groupValues[1]) }
                    style in dateStyles && raw.toDoubleOrNull() != null -> civilDateFromEpochDays(raw.toDouble().toInt() - if (oldDateSystem) 24_107 else 25_569)
                        .let { "${it.year}-${it.month.toString().padStart(2, '0')}-${it.day.toString().padStart(2, '0')}" }
                    else -> unescapeXml(raw)
                }
            }
            if (cells.any(String::isNotBlank)) rows.add(cells)
        }
        rows
    }
}
