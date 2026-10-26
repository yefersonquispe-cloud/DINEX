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
    val explicit = Regex("(?:s/\\.?|soles?|sol|pen)\\s*([0-9]+(?:[.,][0-9]{1,2})?)|([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:s/\\.?|soles?|sol|pen)", RegexOption.IGNORE_CASE).find(normalized)
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
    val title = Regex("(?:en|de|para|por)\\s+([a-záéíóúñ0-9\\s]+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim()
        ?.replaceFirstChar { it.uppercase() }
        ?.replace(Regex("\\b(soles?|sol|s/\\.?|pen)\\b.*", RegexOption.IGNORE_CASE), "")
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
        .replace(Regex("\\b(oye\\s+dinex|dinex|agenda|agendar|recuérdame|recuerdame|recordatorio|recordar|para\\s+hoy|para\\s+mañana|hoy|mañana)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("(?:s/|soles?|sol)?\\s*[0-9]+(?:[.,][0-9]{1,2})?", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', ',', '.', ':')
    val title = cleaned.replace(Regex("\\b(por|de|para|en)\\s*$", RegexOption.IGNORE_CASE), "")
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
    "m4|" + listOf(
        item.id, transferEncodeField(item.title), transferEncodeField(item.category), item.amount, item.income, item.day,
        transferEncodeField(item.method), transferEncodeField(item.location), item.month, item.year,
        item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
    ).joinToString("|")
}
internal fun decodeMovements(raw: String?): List<Movement> = raw?.lineSequence()?.mapNotNull { line ->
    val escaped = line.startsWith("m3|") || line.startsWith("m4|")
    val body = when {
        line.startsWith("m4|") -> line.removePrefix("m4|")
        line.startsWith("m3|") -> line.removePrefix("m3|")
        else -> line
    }
    val f = body.split('|'); if (f.size < 7) return@mapNotNull null
    fun field(index: Int) = f.getOrNull(index).orEmpty().let { if (escaped) transferDecodeField(it) else it }
    Movement(f[0].toIntOrNull() ?: return@mapNotNull null, field(1), field(2), f[3].toDoubleOrNull() ?: return@mapNotNull null,
        f[4].toBooleanStrictOrNull() ?: return@mapNotNull null, f[5].toIntOrNull() ?: return@mapNotNull null, field(6), field(7),
        f.getOrNull(8)?.toIntOrNull() ?: civilDateFromEpochDays().month,
        f.getOrNull(9)?.toIntOrNull() ?: civilDateFromEpochDays().year,
        f.getOrNull(10)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 },
        f.getOrNull(11)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 })
}?.toList().orEmpty()

private fun transferEncodeField(value: String) = value.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A").replace("\r", "%0D")
private fun transferDecodeField(value: String) = value.replace("%7C", "|").replace("%0A", "\n").replace("%0D", "\r").replace("%25", "%")

internal fun parseImportedMovements(raw: String): List<Movement> {
    return runCatching { reviewImportedRows(parseCsvRows(raw)).movements }.getOrDefault(emptyList())
}

internal fun exportMovementsCsv(movements: List<Movement>): String = buildString {
    appendLine("fecha;descripcion;categoria;monto;tipo;medio;ubicacion")
    movements.forEach { movement ->
        appendLine(listOf(movement.dateIso(), movement.title, movement.category, format2(movement.amount), if (movement.income) "Ingreso" else "Egreso", movement.method, movement.location).joinToString(";", transform = ::csvField))
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
