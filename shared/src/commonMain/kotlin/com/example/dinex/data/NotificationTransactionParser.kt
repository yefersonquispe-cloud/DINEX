package com.example.dinex

/** Deliberately returns suggestions, not automatic charges: messages can be ambiguous. */
fun parseDinexTransactionNotification(source: String, text: String): CapturedTransaction? {
    val normalized = normalizedColumn(text)
    val ignored = listOf(
        "código de verificación", "codigo de verificacion", "código otp", "codigo otp",
        "oferta por", "compra y gana", "rechazad", "saldo insuficiente", "pago pendiente",
        "no se pudo", "transaccion fallida", "operacion cancelada", "pago cancelado",
        "solicitud de pago", "solicita un pago", "solicita un yape", "yape pendiente",
    )
    if (ignored.any(normalized::contains)) return null
    val expenseWords = listOf(
        "gastaste", "compraste", "pagaste", "pago realizado", "pago exitoso", "compra aprobada",
        "realizaste una compra", "realizaste un consumo", "consumo por", "retiro realizado",
        "cargo realizado", "cobro realizado", "operación realizada", "operacion realizada",
        "transferencia enviada", "transferiste", "enviaste", "enviado", "debito",
        "yapeaste", "has yapeado", "depositaste", "deposito realizado", "realizaste un deposito",
    )
    val incomeWords = listOf(
        "recibiste", "recibido", "recibida", "abono", "te depositaron", "has recibido",
        "te yapearon", "te yapeo", "te ha yapeado", "te han yapeado", "yape recibido",
        "te envio", "te enviaron", "te ha enviado", "te han enviado", "te pago", "te pagaron",
        "te transfirio", "te transfirieron", "transferencia recibida", "devolucion recibida",
        "deposito recibido", "deposito a tu cuenta", "deposito en tu cuenta",
        "dinero recibido", "pago recibido", "pago de", "acredito en tu cuenta",
    )
    val incomingPosition = incomeWords.map { normalized.indexOf(it) }.filter { it >= 0 }.minOrNull()
    // "Depósito" alone can be an incoming alert, but "depositaste" is an expense.
    val genericDeposit = "deposito" in normalized && expenseWords.none(normalized::contains)
    val outgoingPosition = expenseWords.filterNot { it.startsWith("operaci") }
        .map { normalized.indexOf(it) }.filter { it >= 0 }.minOrNull()
    val income = when {
        incomingPosition != null && (outgoingPosition == null || incomingPosition < outgoingPosition) -> true
        outgoingPosition != null -> false
        genericDeposit -> true
        expenseWords.any(normalized::contains) -> false
        else -> return null
    }
    // Match the complete number, including four or more digits without grouping.
    val matches = Regex("(?:S\\s*/\\.?|PEN|soles?)\\s*([0-9]+(?:[.,][0-9]+)*)|([0-9]+(?:[.,][0-9]+)*)\\s*(?:S\\s*/\\.?|PEN|soles?)", RegexOption.IGNORE_CASE)
        .findAll(text).toList()
    val match = matches.maxByOrNull { candidate ->
        val before = normalizedColumn(text.substring(0, candidate.range.first).takeLast(100))
        val financial = (incomeWords + expenseWords + "deposito").maxOf { before.lastIndexOf(it) }
        val balance = listOf("saldo", "disponible", "limite").maxOf { before.lastIndexOf(it) }
        if (balance > financial) -1 else 1
    } ?: return null
    val raw = match.groupValues[1].ifBlank { match.groupValues[2] }
    val amountString = when {
        raw.contains(',') && raw.contains('.') && raw.lastIndexOf(',') > raw.lastIndexOf('.') -> raw.replace(".", "").replace(',', '.')
        raw.contains(',') && raw.contains('.') -> raw.replace(",", "")
        raw.count { it == ',' } == 1 && raw.substringAfter(',').length <= 2 -> raw.replace(',', '.')
        raw.count { it == '.' } == 1 && raw.substringAfter('.').length <= 2 -> raw
        else -> raw.replace(",", "").replace(".", "")
    }
    val amount = amountString.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
    val parts = text.split('·').map(String::trim).filter(String::isNotBlank)
    val title = parts.firstOrNull { normalizedColumn(it) != normalizedColumn(source) }
        ?.take(160).orEmpty().ifBlank { if (income) "Ingreso de $source" else "Gasto de $source" }
    return CapturedTransaction(source, title, amount, income, text)
}
