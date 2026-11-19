package com.example.dinex

/**
 * Lector de comprobantes peruanos a partir del texto que reconoce la cámara (OCR).
 * Distingue el tipo de comprobante y busca el monto realmente pagado, sin confundirlo
 * con el subtotal, el IGV, el vuelto o el efectivo entregado. Funciona sin internet.
 */
enum class ReceiptKind(val label: String) {
    BOLETA("Boleta electrónica"),
    FACTURA("Factura electrónica"),
    NOTA_VENTA("Nota de venta"),
    TICKET("Ticket"),
    VOUCHER("Voucher de tarjeta"),
    YAPE("Pago con Yape"),
    PLIN("Pago con Plin"),
    TRANSFERENCIA("Transferencia bancaria"),
    SERVICIO("Recibo de servicio"),
    HONORARIOS("Recibo por honorarios"),
    BOLETO("Boleto o entrada"),
    DELIVERY("Pedido por app"),
    PRODUCTO("Producto"),
}

data class ReceiptDate(val day: Int, val month: Int, val year: Int)

data class ReceiptReading(
    val kind: ReceiptKind,
    val merchant: String?,
    val ruc: String?,
    val total: Double?,
    /** 0–100: qué tan seguro es el monto (100 = línea "IMPORTE TOTAL" que cuadra con el IGV). */
    val totalConfidence: Int,
    val date: ReceiptDate?,
    val method: String?,
    val items: List<String>,
    val category: String?,
    /** Todos los montos que aparecen en el comprobante, para validar lo que diga la IA. */
    val amounts: List<Double>,
)

private fun plain(text: String): String = buildString {
    for (c in text.uppercase()) append(
        when (c) {
            'Á' -> 'A'; 'É' -> 'E'; 'Í' -> 'I'; 'Ó' -> 'O'; 'Ú' -> 'U'; 'Ü' -> 'U'
            else -> c
        },
    )
}

/** Corrige confusiones típicas del OCR en los montos: "5/." por "S/", "O" por cero, espacios dentro del número. */
private fun cleanMoneyLine(line: String): String = line
    .replace(Regex("(?<![A-Z0-9])[S5$]\\s*/\\s*\\.?"), "S/ ")
    .replace(Regex("(?<=\\d)[Oo](?=\\d)"), "0")
    .replace(Regex("(?<=\\d)\\s*([.,])\\s*(?=\\d{2}(?!\\d))"), "$1")

private val moneyRegex = Regex("(?<![\\d.,/-])(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d+)([.,]\\d{1,2})(?![\\d%])")
private val solesWithoutDecimals = Regex("S/\\s*(\\d{1,6})(?![\\d.,])")

/** Convierte "1,234.50", "1.234,50", "12,50" o "12.5" en número. */
internal fun parseSolesAmount(text: String): Double? {
    val compact = text.replace(" ", "")
    val lastSep = maxOf(compact.lastIndexOf('.'), compact.lastIndexOf(','))
    if (lastSep < 0) return compact.toDoubleOrNull()
    val decimals = compact.substring(lastSep + 1)
    val whole = compact.substring(0, lastSep).replace(".", "").replace(",", "")
    return if (decimals.length in 1..2) "$whole.$decimals".toDoubleOrNull() else (whole + decimals).toDoubleOrNull()
}

private fun amountsIn(line: String): List<Double> {
    val clean = cleanMoneyLine(line)
    val withDecimals = moneyRegex.findAll(clean).mapNotNull { parseSolesAmount(it.value) }.toList()
    val plainSoles = solesWithoutDecimals.findAll(clean).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
    return (withDecimals + plainSoles).filter { it > 0 && it < 1_000_000 }
}

private val strongTotal = listOf(
    "IMPORTE TOTAL", "TOTAL A PAGAR", "TOTAL PAGADO", "MONTO TOTAL", "TOTAL VENTA", "NETO A PAGAR", "MONTO A PAGAR",
    "IMPORTE A PAGAR", "TOTAL PAGAR", "TOTAL S/", "TOTAL SOLES", "TOTAL PEN", "MONTO PAGADO", "IMPORTE PAGADO", "TOTAL DEL PEDIDO",
)
private val walletTotal = listOf("YAPEASTE", "PLINEASTE", "ENVIASTE", "PAGASTE", "TRANSFERISTE", "MONTO ENVIADO", "MONTO TRANSFERIDO")
private val notTotal = listOf(
    "SUBTOTAL", "SUB TOTAL", "SUB-TOTAL", "IGV", "I.G.V", "GRAVAD", "EXONERAD", "INAFECT", "GRATUIT", "DESCUENTO", "DSCTO", "DCTO",
    "VUELTO", "CAMBIO", "EFECTIVO", "RECIBIDO", "PAGO CON", "REDONDEO", "ICBPER", "PUNTOS", "SALDO", "LIMITE", "AHORRASTE",
    "TOTAL DESCUENTO", "VALOR VENTA", "ISC", "PRECIO UNIT", "P.UNIT", "P. UNIT", "CANT", "TIPO DE CAMBIO", "T.C.",
)

private val merchantCategories = listOf(
    "Comida" to listOf("POLLERIA", "POLLO", "CHIFA", "CEVICHE", "RESTAURANT", "RESTOBAR", "KFC", "BEMBOS", "MCDONALD", "BURGER", "PIZZA",
        "PAPA JOHN", "CAFE", "STARBUCKS", "PANADERIA", "PASTELERIA", "BODEGA", "MINIMARKET", "TOTTUS", "PLAZA VEA", "WONG", "MASS",
        "TAMBO", "OXXO", "MERCADO", "SUPERMERCADO", "MAKRO", "VIVANDA", "MENU", "ALMUERZO", "JUGUERIA", "HELADERIA", "NORKYS",
        "ROKYS", "DUNKIN", "CHICHA", "SANGUCHERIA", "COMIDA", "RAPPI", "PEDIDOSYA", "DIDI FOOD", "SUPERMERCADOS PERUANOS", "CENCOSUD"),
    "Transporte" to listOf("UBER", "CABIFY", "INDRIVE", "INDRIVER", "DIDI", "TAXI", "GRIFO", "PRIMAX", "REPSOL", "PECSA", "PETROPERU",
        "GASOLINA", "DIESEL", "GLP", "COMBUSTIBLE", "METROPOLITANO", "CORREDOR", "PASAJE", "CRUZ DEL SUR", "OLTURSA", "CIVA", "MOVIL TOURS",
        "LINEA", "PEAJE", "ESTACIONAMIENTO", "PARKING", "LATAM", "SKY AIRLINE", "JETSMART", "ASIENTO", "ORIGEN", "DESTINO"),
    "Estudios" to listOf("LIBRERIA", "TAI LOY", "TAILOY", "UNIVERSIDAD", "INSTITUTO", "TECSUP", "SENATI", "MATRICULA", "PENSION",
        "COPIAS", "FOTOCOPIA", "IMPRESION", "UTILES", "CUADERNO", "CURSO", "COLEGIO", "ACADEMIA", "UDEMY", "PLATZI", "LIBRO"),
    "Entretenimiento" to listOf("CINEPLANET", "CINEMARK", "CINEPOLIS", "CINE", "NETFLIX", "SPOTIFY", "DISNEY", "HBO", "STEAM",
        "PLAYSTATION", "XBOX", "NINTENDO", "JOINNUS", "TELETICKET", "TICKETMASTER", "CONCIERTO", "DISCOTECA", "KARAOKE", "BOWLING",
        "FUNCION", "SALA", "ENTRADA"),
)

private val serviceCompanies = listOf("LUZ DEL SUR", "ENEL", "SEDAPAL", "SEDAPAR", "SEDALIB", "EPS GRAU", "MOVISTAR", "TELEFONICA",
    "CLARO", "ENTEL", "BITEL", "CALIDDA", "WIN", "HIDRANDINA", "ELECTROSUR", "ELECTRO SUR ESTE", "ELECTRONORTE", "ELECTROCENTRO",
    "ELECTRO ORIENTE", "SEAL")

/** Busca una empresa de servicios como palabra completa (así "ELECTRONICA" no se confunde con una eléctrica). */
private fun serviceCompanyIn(all: String): String? = serviceCompanies.firstOrNull { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(all) }

private val monthNames = mapOf(
    "ENE" to 1, "FEB" to 2, "MAR" to 3, "ABR" to 4, "MAY" to 5, "JUN" to 6, "JUL" to 7, "AGO" to 8, "SEP" to 9, "SET" to 9,
    "OCT" to 10, "NOV" to 11, "DIC" to 12,
)

private fun findDate(lines: List<String>): ReceiptDate? {
    val numeric = Regex("(?<!\\d)(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})(?!\\d)")
    val iso = Regex("(?<!\\d)(20\\d{2})[/.-](\\d{1,2})[/.-](\\d{1,2})(?!\\d)")
    val words = Regex("(?<!\\d)(\\d{1,2})\\s*(?:DE\\s+)?([A-Z]{3})[A-Z]*\\.?\\s*(?:DE\\s+|DEL\\s+)?(20\\d{2})")
    fun valid(d: Int, m: Int, y: Int) = d in 1..31 && m in 1..12 && y in 2000..2100
    // Prioriza la línea de emisión; los recibos de servicios traen también la fecha de vencimiento.
    val ordered = lines.sortedByDescending { line -> if ("EMISION" in line || "FECHA" in line && "VENC" !in line) 1 else 0 }
    for (line in ordered) {
        if ("VENC" in line || "CORTE" in line) continue
        iso.find(line)?.let { m ->
            val (y, mo, d) = m.destructured
            if (valid(d.toInt(), mo.toInt(), y.toInt())) return ReceiptDate(d.toInt(), mo.toInt(), y.toInt())
        }
        numeric.find(line)?.let { m ->
            val (d, mo, yRaw) = m.destructured
            val y = yRaw.toInt().let { if (it < 100) 2000 + it else it }
            if (valid(d.toInt(), mo.toInt(), y)) return ReceiptDate(d.toInt(), mo.toInt(), y)
        }
        words.find(line)?.let { m ->
            val (d, mon, y) = m.destructured
            val month = monthNames[mon]
            if (month != null && valid(d.toInt(), month, y.toInt())) return ReceiptDate(d.toInt(), month, y.toInt())
        }
    }
    return null
}

private fun detectKind(all: String): ReceiptKind = when {
    Regex("YAPEASTE|YAPE[O ]|\\bYAPE\\b").containsMatchIn(all) && ("OPERACION" in all || "YAPEASTE" in all || "ENVIASTE" in all) -> ReceiptKind.YAPE
    Regex("PLINEASTE|\\bPLIN\\b").containsMatchIn(all) && ("OPERACION" in all || "PLINEASTE" in all || "ENVIASTE" in all) -> ReceiptKind.PLIN
    "RECIBO POR HONORARIOS" in all -> ReceiptKind.HONORARIOS
    "FACTURA" in all || Regex("\\bF[A-Z0-9]{3}\\s*-\\s*\\d{3,8}\\b").containsMatchIn(all) -> ReceiptKind.FACTURA
    "BOLETA" in all || Regex("\\bB[A-Z0-9]{3}\\s*-\\s*\\d{3,8}\\b").containsMatchIn(all) -> ReceiptKind.BOLETA
    "NOTA DE VENTA" in all -> ReceiptKind.NOTA_VENTA
    serviceCompanyIn(all) != null && ("SUMINISTRO" in all || "RECIBO" in all || "VENCIMIENTO" in all || "CONSUMO" in all) -> ReceiptKind.SERVICIO
    Regex("VISA|MASTERCARD|MASTER CARD|AMEX|DINERS|NIUBIZ|IZIPAY|CULQI|VENDEMAS|COD\\.? ?AUT|AUTORIZACION|APROBAD|\\*{4}\\s*\\d{4}").containsMatchIn(all) -> ReceiptKind.VOUCHER
    "TRANSFERENCIA" in all && ("OPERACION" in all || "CUENTA" in all) -> ReceiptKind.TRANSFERENCIA
    listOf("RAPPI", "PEDIDOSYA", "DIDI FOOD", "TU PEDIDO", "RESUMEN DEL PEDIDO").any { it in all } -> ReceiptKind.DELIVERY
    listOf("ASIENTO", "PASAJERO", "FUNCION", "BUTACA", "ENTRADA GENERAL", "ORIGEN").any { it in all } -> ReceiptKind.BOLETO
    "TICKET" in all || "RUC" in all || "TOTAL" in all -> ReceiptKind.TICKET
    else -> ReceiptKind.PRODUCTO
}

private fun detectMethod(all: String, kind: ReceiptKind): String? = when {
    kind == ReceiptKind.YAPE || Regex("\\bYAPE\\b").containsMatchIn(all) -> "Yape"
    kind == ReceiptKind.PLIN || Regex("\\bPLIN\\b").containsMatchIn(all) -> "Plin"
    kind == ReceiptKind.VOUCHER || Regex("VISA|MASTERCARD|TARJETA|DEBITO|CREDITO").containsMatchIn(all) -> "Tarjeta"
    kind == ReceiptKind.TRANSFERENCIA -> "Transferencia"
    Regex("EFECTIVO|CONTADO").containsMatchIn(all) -> "Efectivo"
    else -> null
}

private val addressHints = listOf("AV.", "AV ", "JR.", "JR ", "CALLE", "MZ", " LT", "URB", "PSJE", "TELF", "TEL:", "CEL", "WWW", "@", "HTTP",
    "LIMA", "AREQUIPA", "CUSCO", "PERU", "DIRECCION", "SUCURSAL", "TIENDA N")

private fun findMerchant(lines: List<String>, original: List<String>, kind: ReceiptKind, all: String): String? {
    if (kind == ReceiptKind.YAPE || kind == ReceiptKind.PLIN) {
        // "¡Yapeaste! S/ 25 ... a Juan Pérez" o "Enviado a: Juan"
        lines.forEachIndexed { i, line ->
            Regex("(?:^|\\s)(?:A|PARA|DESTINO|ENVIADO A|DESTINATARIO)[: ]+([A-Z][A-Z .'*]{2,40})$").find(line)?.let {
                return original[i].substring(original[i].length - it.groupValues[1].length).trim()
            }
        }
    }
    if (kind == ReceiptKind.SERVICIO) serviceCompanyIn(all)?.let { return it.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) } }
    val rucIndex = lines.indexOfFirst { "RUC" in it }
    val ignore = listOf("BOLETA", "FACTURA", "ELECTRONICA", "TICKET", "NOTA DE VENTA", "RUC", "FECHA", "HORA", "CLIENTE", "DNI", "SENOR",
        "VOUCHER", "COMPROBANTE", "RECIBO", "BIENVENIDO", "GRACIAS", "CAJA", "CAJERO", "SERIE")
    fun good(i: Int): Boolean {
        val line = lines[i]
        return line.length in 3..40 && line.count(Char::isLetter) >= 3 && ignore.none { it in line } &&
            addressHints.none { it in line } && amountsIn(line).isEmpty()
    }
    if (rucIndex > 0) (rucIndex - 1 downTo maxOf(0, rucIndex - 3)).firstOrNull(::good)?.let { return original[it].trim() }
    return lines.indices.take(6).firstOrNull(::good)?.let { original[it].trim() }
}

private fun findItems(lines: List<String>, original: List<String>): List<String> {
    val itemLine = Regex("^(?:\\d+(?:[.,]\\d+)?\\s*(?:X|UND|UN|KG|LT)?\\s+)?([A-Z][A-Z0-9 /.%'&-]{2,40}?)\\s+(?:S/\\s*)?\\d+[.,]\\d{2}$")
    return lines.mapIndexedNotNull { i, line ->
        if (notTotal.any { it in line } || "TOTAL" in line || "RUC" in line) return@mapIndexedNotNull null
        itemLine.find(cleanMoneyLine(line))?.groupValues?.get(1)?.trim()?.takeIf { it.count(Char::isLetter) >= 3 }?.let {
            original[i].trim().take(it.length.coerceAtMost(original[i].trim().length)).trim()
        }
    }.distinct().take(8)
}

fun readReceiptText(raw: String): ReceiptReading {
    val original = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
    val lines = original.map(::plain)
    val all = lines.joinToString("\n")
    val kind = detectKind(all)

    // Candidatos a total, con puntaje según la palabra clave de su línea (o de la línea anterior, si el OCR la separó).
    data class Candidate(val amount: Double, val score: Int, val index: Int)
    val candidates = mutableListOf<Candidate>()
    lines.forEachIndexed { i, line ->
        val here = amountsIn(line)
        val negative = notTotal.any { it in line }
        val score = when {
            negative && strongTotal.none { it in line } -> -100
            strongTotal.any { it in line } -> 100
            walletTotal.any { it in line } -> 95
            Regex("\\bTOTAL\\b").containsMatchIn(line) -> 85
            Regex("\\b(MONTO|IMPORTE)\\b").containsMatchIn(line) -> 70
            else -> 0
        }
        when {
            here.isNotEmpty() -> here.forEach { candidates += Candidate(it, score, i) }
            score > 0 -> lines.getOrNull(i + 1)?.let { next ->
                if (notTotal.none { it in next }) amountsIn(next).forEach { candidates += Candidate(it, score - 5, i + 1) }
            }
        }
    }
    val amounts = candidates.map { it.amount }.distinct()
    val ruc = Regex("(?<!\\d)((?:10|15|16|17|20)\\d{9})(?!\\d)").find(all)?.groupValues?.get(1)

    // Cuadre con el IGV: OP. GRAVADA + IGV = TOTAL (18 %) sube la confianza.
    fun firstAmountAfter(key: String): Double? = lines.indexOfFirst { key in it }.takeIf { it >= 0 }?.let { i ->
        amountsIn(lines[i]).lastOrNull() ?: lines.getOrNull(i + 1)?.let { amountsIn(it).firstOrNull() }
    }
    val gravada = firstAmountAfter("GRAVAD") ?: firstAmountAfter("VALOR VENTA") ?: firstAmountAfter("SUBTOTAL") ?: firstAmountAfter("SUB TOTAL")
    val igv = firstAmountAfter("IGV") ?: firstAmountAfter("I.G.V")

    val best = candidates.filter { it.score > 0 }
        .maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { it.index }.thenBy { it.amount })
    val cash = firstAmountAfter("EFECTIVO") ?: firstAmountAfter("RECIBIDO") ?: firstAmountAfter("PAGO CON")
    val fallback = candidates.filter { it.score >= 0 && (cash == null || it.amount < cash) }.maxByOrNull { it.amount }
    val total = best?.amount ?: fallback?.amount
    val adds = gravada != null && igv != null && total != null && kotlin.math.abs(gravada + igv - total) <= 0.06
    val confidence = when {
        total == null -> 0
        adds -> 98
        best != null && best.score >= 95 -> 92
        best != null && best.score >= 85 -> 86
        best != null -> 75
        else -> 55
    }

    val merchant = findMerchant(lines, original, kind, all)
    val merchantText = plain(merchant.orEmpty()) + "\n" + all
    val category = when (kind) {
        ReceiptKind.SERVICIO -> "Otros"
        else -> merchantCategories.firstOrNull { (_, words) -> words.any { Regex("\\b${Regex.escape(it)}").containsMatchIn(merchantText) } }?.first
    }
    return ReceiptReading(
        kind = kind,
        merchant = merchant,
        ruc = ruc,
        total = total,
        totalConfidence = confidence,
        date = findDate(lines),
        method = detectMethod(all, kind),
        items = findItems(lines, original),
        category = category,
        amounts = amounts,
    )
}
