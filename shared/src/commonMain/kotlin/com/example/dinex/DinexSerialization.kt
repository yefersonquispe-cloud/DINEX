package com.example.dinex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_card_balance_bg
import dinex.shared.generated.resources.dinex_card_savings_bg
import dinex.shared.generated.resources.dinex_app_icon
import dinex.shared.generated.resources.dinex_mascot_ai
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max

// ═══════════════════════════════════════════════════════════════════════════
// SERIALIZATION & FORMATTING
// Local account, movement and reminder persistence plus display helpers.
// ═══════════════════════════════════════════════════════════════════════════
internal fun money(value: Double): String = "S/ ${format2(value)}"

internal fun parseVoiceCommand(text: String, nextId: Int, location: String?): Movement? {
    val normalized = text.lowercase()
    val explicit = Regex("(?i)(?:s/\\.?|soles?|sol|pen)\\s*([0-9]+(?:[.,][0-9]{1,2})?)|([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:s/\\.?|soles?|sol|pen)").find(normalized)
    val amount = explicit?.let { match ->
        (match.groupValues[1].ifBlank { match.groupValues[2] }).replace(',', '.').toDoubleOrNull()
    } ?: Regex("([0-9]+(?:[.,][0-9]{1,2})?)").findAll(normalized).lastOrNull()?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
        ?: return null
    if (amount <= 0) return null
    val income = listOf("ingreso", "recibí", "recibi", "gané", "gane", "me pagaron", "abono").any(normalized::contains)
    val category = when {
        income -> "Ingreso"
        // Se evalúa transporte antes que supermercados: "pasaje del metro"
        // debe ser transporte, mientras "compré en Metro" sigue siendo comida.
        listOf("transporte", "taxi", "uber", "pasaje", "pasajes", "metropolitano", "tren", "línea 1", "linea 1", "gasolina", "combustible", "bus", "micro").any(normalized::contains) -> "Transporte"
        listOf("comida", "almuerzo", "cena", "desayuno", "restaurante", "snack", "mercado", "super", "tottus", "metro", "plaza vea", "kfc", "bembos", "cafe", "café", "pan", "hamburguesa").any(normalized::contains) -> "Comida"
        listOf("estudios", "libro", "curso", "pension", "pensión", "copias", "universidad", "instituto", "colegio", "matricula", "matrícula").any(normalized::contains) -> "Estudios"
        listOf("entretenimiento", "cine", "juego", "salida", "bar", "fiesta", "netflix", "spotify", "steam").any(normalized::contains) -> "Entretenimiento"
        else -> categories.firstOrNull { normalized.contains(it.name.lowercase()) }?.name ?: "Otros"
    }
    val title = Regex("(?i)(?:en|de|para|por)\\s+([a-záéíóúñ0-9\\s]+)$").find(text)?.groupValues?.getOrNull(1)?.trim()
        ?.replaceFirstChar { it.uppercase() }
        ?.replace(Regex("(?i)\\b(soles?|sol|s/\\.?|pen)\\b.*"), "")
        ?.trim(' ', '.', ',')
        ?.ifBlank { null } ?: if (income) "Ingreso por voz" else "Gasto por voz"
    return Movement(nextId, title.take(60), category, amount, income, currentDayOfMonth(), "Voz", location.orEmpty())
}

internal fun parseVoiceReminder(text: String, nextId: Int): PaymentReminder? {
    val normalized = text.lowercase()
    val reminderWords = listOf("agenda", "agendar", "recuérdame", "recuerdame", "recordatorio", "recordar")
    if (reminderWords.none(normalized::contains)) return null
    val amountMatch = Regex("(?:s/\\.?|pen|soles?)\\s*([0-9]+(?:[.,][0-9]{1,2})?)|([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:s/\\.?|pen|soles?)")
        .find(normalized)
    val amount = amountMatch?.let { match ->
        match.groupValues[1].ifBlank { match.groupValues[2] }.replace(',', '.').toDoubleOrNull()
    } ?: 0.0
    val date = when {
        "mañana" in normalized || "manana" in normalized -> "MAÑANA"
        "hoy" in normalized -> "HOY"
        Regex("\\b(?:el|para el)\\s+([0-9]{1,2}(?:[/-][0-9]{1,2})?)").containsMatchIn(normalized) ->
            Regex("\\b(?:el|para el)\\s+([0-9]{1,2}(?:[/-][0-9]{1,2})?)").find(normalized)?.groupValues?.getOrNull(1)?.uppercase() ?: "PRÓXIMO"
        else -> "PRÓXIMO"
    }
    val cleaned = text
        .replace(Regex("(?i)\\b(oye\\s+dinex|dinex|agenda|agendar|recuérdame|recuerdame|recordatorio|recordar|para\\s+hoy|para\\s+mañana|hoy|mañana)\\b"), " ")
        .replace(Regex("(?i)(?:s/|soles?|sol)?\\s*[0-9]+(?:[.,][0-9]{1,2})?"), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', ',', '.', ':')
    val title = cleaned.replace(Regex("(?i)\\b(por|de|para|en)\\s*$"), "")
        .trim(' ', ',', '.', ':')
        .ifBlank { "Recordatorio por voz" }
        .replaceFirstChar { it.uppercase() }
        .take(60)
    return PaymentReminder(nextId, title, date, amount.coerceAtLeast(0.0))
}

internal fun format2(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0.00"
    val cents = (kotlin.math.abs(value) * 100 + 0.5).toLong()
    val sign = if (value < -0.005) "-" else ""
    return "$sign${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
internal fun percent(part: Double, total: Double): Int = if (total <= 0) 0 else (part / total * 100).toInt()

internal fun credentialDigest(email: String, password: String): String {
    val source = "$email|DINEX-LOCAL|$password"
    var first = -3750763034362895579L
    var second = 7046029254386353131L
    repeat(2_048) { round ->
        source.forEach { char ->
            first = (first xor (char.code + round).toLong()) * 1099511628211L
            first = first xor (first ushr 31)
            second = (second + char.code + round) * -7046029254386353131L
            second = second xor (second ushr 27)
        }
    }
    return first.toULong().toString(16).padStart(16, '0') + second.toULong().toString(16).padStart(16, '0')
}

internal fun encodeAccounts(items: List<UserAccount>): String = items.joinToString("\n") { account ->
    listOf(account.name.clean(), account.email.clean(), account.passwordDigest).joinToString("|")
}

internal fun decodeAccounts(raw: String?): List<UserAccount> = raw?.lineSequence()?.mapNotNull { line ->
    val fields = line.split('|')
    if (fields.size != 3 || fields[0].isBlank() || fields[1].isBlank() || fields[2].isBlank()) null
    else UserAccount(fields[0], fields[1], fields[2])
}?.toList().orEmpty()

internal fun encodeMovements(items: List<Movement>): String = items.joinToString("\n") { item ->
    listOf(item.id, item.title.clean(), item.category.clean(), item.amount, item.income, item.day, item.method.clean(), item.location.clean()).joinToString("|")
}
internal fun decodeMovements(raw: String?): List<Movement> = raw?.lineSequence()?.mapNotNull { line ->
    val f = line.split('|'); if (f.size < 7) return@mapNotNull null
    Movement(f[0].toIntOrNull() ?: return@mapNotNull null, f[1], f[2], f[3].toDoubleOrNull() ?: return@mapNotNull null,
        f[4].toBooleanStrictOrNull() ?: return@mapNotNull null, f[5].toIntOrNull() ?: return@mapNotNull null, f[6], f.getOrNull(7).orEmpty())
}?.toList().orEmpty()

internal fun parseImportedMovements(raw: String): List<Movement> {
    val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    if (lines.isEmpty()) return emptyList()
    val separator = if (lines.first().contains(';')) ';' else ','
    val first = lines.first().split(separator).map { it.trim().lowercase().removeSurrounding("\"") }
    val hasHeader = first.any { it.contains("monto") || it.contains("importe") || it.contains("descrip") }
    val rows = if (hasHeader) lines.drop(1) else lines
    fun col(row: List<String>, keys: List<String>, fallback: Int): String = first.indexOfFirst { header -> keys.any(header::contains) }.takeIf { it >= 0 }?.let { row.getOrNull(it) }.orEmpty().ifBlank { row.getOrNull(fallback).orEmpty() }
    return rows.mapIndexedNotNull { index, line ->
        val row = line.split(separator).map { it.trim().removeSurrounding("\"") }
        val title = col(row, listOf("descrip", "concept", "detalle", "comercio"), 1).ifBlank { "Movimiento importado" }
        val dateCol = col(row, listOf("fecha", "date", "dia", "día"), 0)
        val parsedDay = Regex("\\b([0-3]?[0-9])\\b").find(dateCol)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 31)
        val day = parsedDay ?: currentDayOfMonth()
        val amountText = col(row, listOf("monto", "importe", "total", "valor"), 3).replace("S/", "").replace(" ", "")
        val rawAmount = if (amountText.contains(',')) amountText.replace(".", "").replace(',', '.') else amountText
        val amount = rawAmount.toDoubleOrNull() ?: return@mapIndexedNotNull null
        val type = col(row, listOf("tipo", "movimiento", "operacion"), 4).lowercase()
        val income = listOf("ingreso", "abono", "entrada", "depósito", "deposito", "recibido")
            .any(type::contains)
        val category = col(row, listOf("categoria", "categoría"), 2).ifBlank { if (income) "Ingreso" else "Otros" }
        Movement(index + 1, title, category.replaceFirstChar { it.uppercase() }, kotlin.math.abs(amount), income, day, col(row, listOf("medio", "metodo", "método"), 5).ifBlank { "Importado" })
    }
}

internal fun exportMovementsCsv(movements: List<Movement>): String = buildString {
    val monthShort = currentMonthShort()
    val year = civilDateFromEpochDays().year
    appendLine("fecha,descripcion,categoria,monto,tipo,medio,ubicacion")
    movements.forEach { movement ->
        appendLine("\"${movement.day} $monthShort $year\",\"${movement.title.clean()}\",\"${movement.category.clean()}\",${format2(movement.amount)},${if (movement.income) "Ingreso" else "Egreso"},\"${movement.method.clean()}\",\"${movement.location.clean()}\"")
    }
}

internal fun encodeReminders(items: List<PaymentReminder>): String = items.joinToString("\n") { item ->
    listOf(item.id, item.title.clean(), item.date.clean(), item.amount).joinToString("|")
}

internal fun decodeReminders(raw: String?): List<PaymentReminder> = raw?.lineSequence()?.mapNotNull { line ->
    val fields = line.split('|')
    if (fields.size != 4) return@mapNotNull null
    PaymentReminder(
        fields[0].toIntOrNull() ?: return@mapNotNull null,
        fields[1],
        fields[2],
        fields[3].toDoubleOrNull() ?: return@mapNotNull null,
    )
}?.toList().orEmpty()

internal fun String.clean(): String = replace('|', '/').replace('\n', ' ').replace('\r', ' ')
