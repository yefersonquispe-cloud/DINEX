package com.example.dinex

// ═══════════════════════════════════════════════════════════════════════════
// OYE DINEX · COMPRENSIÓN DE VOZ
// Convierte una frase natural en una intención: registrar un movimiento, crear un
// recordatorio, consultar tus finanzas, confirmar, cancelar o preguntar a Dinex IA.
// Las respuestas se calculan SOLO con los movimientos reales de la cuenta: nunca se
// inventan montos. Antes de guardar algo dudoso (monto alto, sin moneda, varios
// números, otra fecha o dólares) se pide confirmación.
// ═══════════════════════════════════════════════════════════════════════════

/** Prefijo con el que la burbuja de voz pide abrir Dinex IA con una pregunta. */
internal const val VOICE_AI_PREFIX = "dinex-ia::"

internal enum class VoicePeriod(val label: String) {
    TODAY("hoy"),
    YESTERDAY("ayer"),
    THIS_WEEK("esta semana"),
    LAST_WEEK("la semana pasada"),
    THIS_MONTH("este mes"),
    LAST_MONTH("el mes pasado"),
    ALL("en total"),
}

internal sealed interface VoiceQuery {
    data class Spent(val period: VoicePeriod, val category: String? = null) : VoiceQuery
    data class Earned(val period: VoicePeriod) : VoiceQuery
    data object Balance : VoiceQuery
    data class TopExpenses(val period: VoicePeriod) : VoiceQuery
    data class Saved(val period: VoicePeriod) : VoiceQuery
    data object Recent : VoiceQuery
}

/** Movimiento entendido por voz, listo para guardar o para confirmar. */
internal data class VoiceDraft(
    val movement: Movement,
    /** Motivos por los que conviene confirmar antes de guardar; vacío si es seguro. */
    val doubts: List<String> = emptyList(),
) {
    val needsConfirmation: Boolean get() = doubts.isNotEmpty()
}

internal sealed interface VoiceOutcome {
    /** Respuesta a una consulta, con filas opcionales para mostrar ordenadas. */
    data class Answer(val text: String, val items: List<String> = emptyList()) : VoiceOutcome
    /** Guardar sin preguntar (datos claros). */
    data class Save(val draft: VoiceDraft) : VoiceOutcome
    /** Pedir confirmación antes de guardar. */
    data class Confirm(val draft: VoiceDraft, val prompt: String) : VoiceOutcome
    data class SaveReminder(val reminder: PaymentReminder) : VoiceOutcome
    data class Cancelled(val text: String = "Listo, no guardé nada.") : VoiceOutcome
    /** No es un comando conocido: se puede preguntar a Dinex IA. */
    data class AskAi(val question: String) : VoiceOutcome
    data class Help(val text: String) : VoiceOutcome
}

/**
 * Conversación de voz con memoria corta: recuerda la última consulta (para "¿y en
 * transporte?") y un movimiento pendiente de confirmar (para "sí" o "no").
 */
internal class VoiceConversation(private val clock: () -> Long = ::epochMillis) {
    private var lastQuery: VoiceQuery? = null
    private var lastQueryAt = 0L
    var pending: VoiceDraft? = null
        private set
    private var answers = 0

    fun reset() { lastQuery = null; pending = null }

    fun interpret(text: String, data: FinancialData, today: Int = currentDayIndex()): VoiceOutcome {
        val normalized = normalizeVoice(text)
        if (normalized.isBlank()) return VoiceOutcome.Help("No escuché nada. Prueba: “¿cuánto gasté esta semana?”")

        pending?.let { draft ->
            when (voiceConfirmation(normalized)) {
                true -> { pending = null; return VoiceOutcome.Save(draft.copy(doubts = emptyList())) }
                false -> { pending = null; return VoiceOutcome.Cancelled() }
                null -> pending = null // Dijo otra cosa: se descarta lo pendiente y se atiende la nueva frase.
            }
        }
        if (isCancel(normalized)) return VoiceOutcome.Cancelled("Listo, cancelado.")
        if (isHelp(normalized)) return VoiceOutcome.Help(
            "Puedes decir: “registra 25 soles en comida”, “recibí 1500 soles”, “¿cuánto gasté esta semana?” o “muéstrame mis últimos movimientos”.",
        )

        parseVoiceReminder(text, (data.reminders.maxOfOrNull { it.id } ?: 0) + 1)?.let { return VoiceOutcome.SaveReminder(it) }

        val query = parseVoiceQuery(normalized, followUpOf = lastQuery.takeIf { clock() - lastQueryAt < 120_000 })
        if (query != null) {
            lastQuery = query
            lastQueryAt = clock()
            val answer = answerVoiceQuery(data, query, today, variant = answers++)
            return answer
        }

        val draft = parseVoiceDraft(text, (data.movements.maxOfOrNull { it.id } ?: 0) + 1, today)
        if (draft != null) {
            if (!draft.needsConfirmation) return VoiceOutcome.Save(draft)
            pending = draft
            return VoiceOutcome.Confirm(draft, confirmationPrompt(draft))
        }
        return VoiceOutcome.AskAi(text.trim())
    }
}

// ── Normalización ─────────────────────────────────────────────────────────────

private val WakePrefix = Regex("^(?:(?:oye|hey|hola|ok|okey|okay|ey)\\s+)?(?:dinex|dines|dinnex|di nex|inex|dinez)\\b[\\s,.:]*")

/** Minúsculas, sin tildes, sin la frase de activación y con los números dictados en cifras. */
internal fun normalizeVoice(text: String): String {
    var value = text.lowercase()
        .map { char ->
            when (char) {
                'á', 'à', 'ä' -> 'a'; 'é', 'è', 'ë' -> 'e'; 'í', 'ì', 'ï' -> 'i'
                'ó', 'ò', 'ö' -> 'o'; 'ú', 'ù', 'ü' -> 'u'
                '¿', '?', '¡', '!', ';', '"', '“', '”', '«', '»' -> ' '
                else -> char
            }
        }.joinToString("")
        .replace(Regex("\\s+"), " ").trim()
    value = value.replace(WakePrefix, "").trim()
    // "1,500" y "1.500" son mil quinientos; "18,50" son dieciocho con cincuenta.
    value = Regex("\\b\\d{1,3}(?:[.,]\\d{3})+\\b").replace(value) { it.value.filter(Char::isDigit) }
    value = Regex("(\\d),(\\d{1,2})\\b").replace(value) { "${it.groupValues[1]}.${it.groupValues[2]}" }
    value = spokenNumbersToDigits(value)
    // "18 con 50" → 18.50 ; "20 y medio" → 20.50
    value = Regex("\\b(\\d+)\\s+(?:soles?\\s+)?con\\s+(\\d{2})\\b").replace(value) { "${it.groupValues[1]}.${it.groupValues[2]} " }
    value = Regex("\\b(\\d+)\\s+(soles?\\s+)?y\\s+medio\\b").replace(value) { "${it.groupValues[1]}.50 ${it.groupValues[2]}" }
    return value.replace(',', ' ').replace(Regex("\\s+"), " ").trim()
}

private val Units = mapOf(
    "cero" to 0, "uno" to 1, "un" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
    "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
    "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18,
    "diecinueve" to 19, "veinte" to 20, "veintiun" to 21, "veintiuno" to 21, "veintiuna" to 21, "veintidos" to 22,
    "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27,
    "veintiocho" to 28, "veintinueve" to 29, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
    "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90,
    "cien" to 100, "ciento" to 100, "doscientos" to 200, "doscientas" to 200, "trescientos" to 300, "trescientas" to 300,
    "cuatrocientos" to 400, "cuatrocientas" to 400, "quinientos" to 500, "quinientas" to 500, "seiscientos" to 600,
    "seiscientas" to 600, "setecientos" to 700, "setecientas" to 700, "ochocientos" to 800, "ochocientas" to 800,
    "novecientos" to 900, "novecientas" to 900,
)
private val MoneyWords = setOf("sol", "soles", "lucas", "luca", "dolar", "dolares", "mil")

/** "mil quinientos soles" → "1500 soles"; "veinticinco" → "25". "un gasto" se deja igual. */
internal fun spokenNumbersToDigits(text: String): String {
    val tokens = text.split(' ')
    val out = mutableListOf<String>()
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        val next = tokens.getOrNull(index + 1)
        val isArticle = token in setOf("un", "una", "uno")
        val startsNumber = (token in Units && (!isArticle || next in MoneyWords)) || (token == "mil" && next != null)
        if (!startsNumber) { out += token; index++; continue }
        var total = 0
        var current = 0
        var consumed = false
        while (index < tokens.size) {
            val word = tokens[index]
            when {
                word in Units -> { current += Units.getValue(word); consumed = true; index++ }
                word == "mil" -> { current = (if (current == 0) 1 else current) * 1000; total += current; current = 0; consumed = true; index++ }
                word == "y" && consumed && tokens.getOrNull(index + 1) in Units && tokens[index + 1] !in setOf("un", "una", "uno") -> index++
                else -> break
            }
        }
        out += (total + current).toString()
    }
    return out.joinToString(" ")
}

// ── Detección de intenciones ─────────────────────────────────────────────────

private fun String.hasAny(vararg words: String) = words.any { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(this) }

private fun isCancel(text: String) = text in setOf("no", "nada", "para", "basta", "detente") ||
    text.hasAny("cancela", "cancelar", "cancelalo", "olvidalo", "no importa", "dejalo")

private fun isHelp(text: String) = text in setOf("ayuda", "ayudame") || text.hasAny("que puedes hacer", "como te uso", "que sabes hacer")

/** Respuesta a "¿guardo esto?": true = sí, false = no, null = dijo otra cosa. */
internal fun voiceConfirmation(text: String): Boolean? {
    val clean = normalizeVoice(text)
    return when {
        clean.hasAny("no", "cancela", "cancelar", "mejor no", "incorrecto", "esta mal", "olvidalo") -> false
        clean.hasAny("si", "claro", "correcto", "dale", "ok", "okey", "confirmo", "confirma", "guarda", "guardalo", "exacto", "afirmativo", "listo") -> true
        else -> null
    }
}

private fun periodOf(text: String): VoicePeriod? = when {
    text.hasAny("anteayer") -> null
    text.hasAny("hoy", "en el dia") -> VoicePeriod.TODAY
    text.hasAny("ayer") -> VoicePeriod.YESTERDAY
    text.hasAny("semana pasada", "la otra semana", "ultima semana") -> VoicePeriod.LAST_WEEK
    text.hasAny("esta semana", "la semana", "semana") -> VoicePeriod.THIS_WEEK
    text.hasAny("mes pasado", "el otro mes", "ultimo mes") -> VoicePeriod.LAST_MONTH
    text.hasAny("este mes", "el mes", "mes", "mensual") -> VoicePeriod.THIS_MONTH
    text.hasAny("en total", "siempre", "historial completo", "todo el tiempo") -> VoicePeriod.ALL
    else -> null
}

private val CategoryWords = linkedMapOf(
    // Transporte se revisa antes que comida: "pasaje del metro" es transporte, "compré en Metro" es comida.
    "Transporte" to listOf("transporte", "taxi", "uber", "didi", "indrive", "cabify", "pasaje", "pasajes", "metropolitano", "tren", "linea 1", "gasolina", "combustible", "bus", "micro", "combi", "colectivo", "movilidad"),
    "Comida" to listOf("comida", "alimentacion", "alimentos", "almuerzo", "cena", "desayuno", "lonche", "restaurante", "snack", "mercado", "supermercado", "super", "tottus", "metro", "plaza vea", "wong", "kfc", "bembos", "pollo", "polleria", "chifa", "menu", "cafe", "pan", "hamburguesa", "pizza", "gaseosa", "delivery"),
    "Estudios" to listOf("estudio", "estudios", "libro", "libros", "curso", "pension", "copia", "copias", "universidad", "instituto", "colegio", "matricula", "utiles"),
    "Entretenimiento" to listOf("entretenimiento", "cine", "juego", "juegos", "salida", "bar", "fiesta", "netflix", "spotify", "steam", "concierto", "disney", "youtube"),
    "Otros" to listOf("otros", "otro", "varios"),
)

/** Categoría mencionada en la frase, o null si no se nombró ninguna. */
internal fun voiceCategory(normalized: String): String? =
    CategoryWords.entries.firstOrNull { (_, words) -> words.any { normalized.hasWordOrPlural(it) } }?.key

private fun String.hasWordOrPlural(word: String) = Regex("\\b${Regex.escape(word)}(?:s|es)?\\b").containsMatchIn(this)

private val QueryWords = arrayOf("cuanto", "cuanta", "cuantos", "cuales", "cual", "en que", "donde", "muestrame", "muestra", "dime", "ensename", "como voy", "quiero ver", "ver mis", "saldo", "disponible", "resumen")
private val RecordCommands = arrayOf("registra", "registrar", "anota", "anotar", "agrega", "agregar", "apunta", "guarda", "anade", "pon")

/** Consulta financiera, o null si la frase no es una pregunta. */
internal fun parseVoiceQuery(text: String, followUpOf: VoiceQuery? = null): VoiceQuery? {
    val asks = text.hasAny(*QueryWords)
    val commands = text.hasAny(*RecordCommands)
    val period = periodOf(text)
    val category = voiceCategory(text)
    // Seguimiento corto: "¿y en transporte?", "¿y ayer?", "¿y la semana pasada?".
    if (followUpOf != null && !commands && text.split(' ').size <= 6 && (text.startsWith("y ") || !asks) &&
        !Regex("\\d").containsMatchIn(text) && (period != null || category != null)
    ) {
        return when (followUpOf) {
            is VoiceQuery.Spent -> VoiceQuery.Spent(period ?: followUpOf.period, category ?: followUpOf.category.takeIf { period != null })
            is VoiceQuery.Earned -> VoiceQuery.Earned(period ?: followUpOf.period)
            is VoiceQuery.TopExpenses -> if (category != null) VoiceQuery.Spent(period ?: followUpOf.period, category) else VoiceQuery.TopExpenses(period ?: followUpOf.period)
            is VoiceQuery.Saved -> VoiceQuery.Saved(period ?: followUpOf.period)
            VoiceQuery.Balance, VoiceQuery.Recent -> VoiceQuery.Spent(period ?: VoicePeriod.THIS_MONTH, category)
        }
    }
    if (commands || (!asks && Regex("\\d").containsMatchIn(text))) return null
    val recent = text.hasAny("ultimos movimientos", "movimientos", "historial", "ultimas compras", "ultimos gastos", "lo ultimo que")
    return when {
        text.hasAny("mayores gastos", "mayor gasto", "gaste mas", "mas gaste", "mas gasto", "se me va", "se va mi dinero", "gastos mas grandes") ->
            VoiceQuery.TopExpenses(period ?: VoicePeriod.THIS_MONTH)
        recent && !text.hasAny("cuanto") -> VoiceQuery.Recent
        text.hasAny("ahorre", "ahorro", "ahorrado", "he ahorrado", "ahorros", "me quedo", "me sobro") ->
            VoiceQuery.Saved(period ?: VoicePeriod.THIS_MONTH)
        text.hasAny("disponible", "saldo", "cuanto tengo", "cuanto dinero tengo", "cuanta plata tengo", "cuanto me queda", "plata tengo", "dinero tengo") ->
            VoiceQuery.Balance
        text.hasAny("ingrese", "gane", "recibi", "ingresos", "ingreso", "cobre", "me pagaron", "me entro", "entro") ->
            VoiceQuery.Earned(period ?: VoicePeriod.THIS_MONTH)
        text.hasAny("gaste", "gastos", "gasto", "gastado", "pague", "consumi", "se fue") ->
            VoiceQuery.Spent(period ?: VoicePeriod.THIS_MONTH, category)
        asks && category != null -> VoiceQuery.Spent(period ?: VoicePeriod.THIS_MONTH, category)
        else -> null
    }
}

// ── Registro de movimientos ───────────────────────────────────────────────────

private val AmountWithCurrency = Regex(
    "(?:s/\\.?|soles?|sol|pen|lucas?|dolares?|usd|\\$)\\s*(\\d+(?:\\.\\d{1,2})?)|(\\d+(?:\\.\\d{1,2})?)\\s*(s/|soles?|sol|pen|lucas?|dolares?|usd|\\$)",
)

/** Movimiento a partir de una frase como "registra un gasto de 25 soles en comida con Yape". */
internal fun parseVoiceDraft(text: String, nextId: Int, today: Int = currentDayIndex()): VoiceDraft? {
    val normalized = normalizeVoice(text)
    val withCurrency = AmountWithCurrency.find(normalized)
    // Números que no son fechas ("el 15", "día 3") ni horas.
    val numbers = Regex("(?<![\\w.])(\\d+(?:\\.\\d{1,2})?)(?![\\w.])").findAll(normalized)
        .filterNot { match ->
            val before = normalized.substring(0, match.range.first).trimEnd()
            before.endsWith(" el") || before.endsWith(" dia") || before == "el" || before.endsWith(" las")
        }
        .map { it.groupValues[1] }.distinct().toList()
    val amountText = withCurrency?.let { it.groupValues[1].ifBlank { it.groupValues[2] } } ?: numbers.lastOrNull() ?: return null
    val amount = amountText.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() } ?: return null

    val incomeHint = normalized.hasAny("ingreso", "recibi", "gane", "me pagaron", "cobre", "me depositaron", "me yapearon", "me plinearon",
        "sueldo", "salario", "propina", "vendi", "abono", "me transfirieron", "me prestaron", "me devolvieron")
    val expenseHint = normalized.hasAny("gasto", "gaste", "pague", "compre", "yapee", "plinee", "pagar", "gastar", "consumi")
    val income = incomeHint && !normalized.hasAny("gasto de", "un gasto")
    val category = if (income) "Ingreso" else voiceCategory(normalized) ?: "Otros"
    val method = when {
        normalized.hasAny("yape", "yapee", "yapeo", "yapearon") -> "Yape"
        normalized.hasAny("plin", "plinee", "plinearon") -> "Plin"
        normalized.hasAny("efectivo", "cash", "en mano") -> "Efectivo"
        normalized.hasAny("tarjeta", "debito", "credito", "visa", "mastercard") -> "Tarjeta"
        normalized.hasAny("transferencia", "transferi", "transfirieron", "deposito") -> "Transferencia"
        else -> "Voz"
    }
    val dayOffset = when {
        normalized.hasAny("anteayer") -> 2
        normalized.hasAny("ayer") -> 1
        else -> 0
    }
    val date = civilDateFromEpochDays(today - dayOffset)
    val currency = withCurrency?.value?.let { if (Regex("dolar|usd|\\$").containsMatchIn(it)) "USD" else "PEN" } ?: "PEN"

    val doubts = buildList {
        if (withCurrency == null) add("No escuché la moneda")
        if (currency == "USD") add("Dijiste dólares; Dinex guarda en soles")
        if (numbers.size > 1) add("Escuché varios números")
        if (amount >= 500) add("Es un monto alto")
        if (dayOffset > 0) add("La fecha no es hoy")
        if (incomeHint && expenseHint) add("No quedó claro si es gasto o ingreso")
    }
    val movement = Movement(
        id = nextId,
        title = voiceTitle(text, income, category),
        category = category,
        amount = amount,
        income = income,
        day = date.day,
        method = method,
        month = date.month,
        year = date.year,
    )
    return VoiceDraft(movement, doubts)
}

/** Descripción breve: lo que va después de "en", "por" o "para" ("almuerzo", "trabajo"). */
private fun voiceTitle(text: String, income: Boolean, category: String): String {
    // Sin banderas en línea ni \p{L}: así la expresión también funciona en la web (JavaScript).
    val ignore = RegexOption.IGNORE_CASE
    val clean = text.trim().replace(Regex("^(?:(?:oye|hey|hola|ok)\\s+)?dinex[\\s,.:]*", ignore), "")
    val sentence = clean.replace(Regex("[¿?¡!,]"), " ").replace(Regex("\\.(?!\\d)"), " ").trim()
    // Lo que sigue a la ÚLTIMA preposición: "un gasto de 25 soles en comida" → "comida".
    val tail = Regex("\\b(?:en|para|por|de)\\s+", ignore).findAll(sentence).lastOrNull()
        ?.let { sentence.substring(it.range.last + 1) }.orEmpty()
    val stripped = tail
        .replace(Regex("\\b(con|por|en)\\s+(yape|plin|efectivo|tarjeta|transferencia)\\b.*", ignore), "")
        .replace(Regex("\\b(yape|plin|efectivo|tarjeta|transferencia|hoy|ayer|anteayer|soles?|sol|lucas?|d[oó]lares?)\\b", ignore), "")
        .replace(Regex("^\\s*(un|una|el|la|los|las|mi|mis)\\s+", ignore), "")
        .replace(Regex("\\d+(?:[.,]\\d+)?"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    val generic = setOf("gasto", "ingreso", "comida", "gastos", "ingresos")
    return when {
        stripped.length >= 2 && stripped.lowercase() !in generic -> stripped.replaceFirstChar { it.uppercase() }.take(60)
        income -> "Ingreso por voz"
        category != "Otros" -> category
        else -> "Gasto por voz"
    }
}

private fun confirmationPrompt(draft: VoiceDraft): String {
    val item = draft.movement
    val type = if (item.income) "un ingreso" else "un gasto"
    val where = if (item.income) "" else " en ${item.category}"
    val day = if (item.day == civilDateFromEpochDays().day && item.month == civilDateFromEpochDays().month) "" else " del ${item.day}/${item.month}"
    return "¿Guardo $type de ${money(item.amount)}$where$day? ${draft.doubts.first()}."
}

internal fun voiceSavedMessage(item: Movement): String =
    if (item.income) "Listo: ingreso de ${money(item.amount)} guardado."
    else "Listo: gasto de ${money(item.amount)} en ${item.category} guardado."

// ── Respuestas con datos reales ───────────────────────────────────────────────

private fun Movement.epochDay() = epochDaysOf(year, month, day)

/** Lunes = 0 … domingo = 6 (el 1 de enero de 1970 fue jueves). */
private fun weekday(epochDay: Int) = ((epochDay + 3) % 7 + 7) % 7

internal fun FinancialData.movementsIn(period: VoicePeriod, today: Int = currentDayIndex()): List<Movement> {
    val now = civilDateFromEpochDays(today)
    val monday = today - weekday(today)
    val lastMonth = if (now.month == 1) 12 to now.year - 1 else now.month - 1 to now.year
    return movements.filter { item ->
        when (period) {
            VoicePeriod.TODAY -> item.epochDay() == today
            VoicePeriod.YESTERDAY -> item.epochDay() == today - 1
            VoicePeriod.THIS_WEEK -> item.epochDay() in monday..today
            VoicePeriod.LAST_WEEK -> item.epochDay() in (monday - 7) until monday
            VoicePeriod.THIS_MONTH -> item.month == now.month && item.year == now.year
            VoicePeriod.LAST_MONTH -> item.month == lastMonth.first && item.year == lastMonth.second
            VoicePeriod.ALL -> true
        }
    }
}

private fun VoicePeriod.sentenceStart() = when (this) {
    VoicePeriod.ALL -> "En total"
    else -> label.replaceFirstChar { it.uppercase() }
}

internal fun answerVoiceQuery(data: FinancialData, query: VoiceQuery, today: Int = currentDayIndex(), variant: Int = 0): VoiceOutcome.Answer {
    return when (query) {
        is VoiceQuery.Spent -> {
            val items = data.movementsIn(query.period, today).filterNot { it.income }
                .filter { query.category == null || it.budgetCategory() == query.category }
            val total = items.sumOf { it.amount }
            when {
                query.category != null && total <= 0 -> VoiceOutcome.Answer("No tienes gastos en ${query.category} ${query.period.label}.")
                query.category != null -> VoiceOutcome.Answer("${query.period.sentenceStart()} gastaste ${money(total)} en ${query.category}.")
                total <= 0 -> VoiceOutcome.Answer("No registraste gastos ${query.period.label}.")
                else -> {
                    val top = items.groupBy { it.budgetCategory() }.mapValues { (_, list) -> list.sumOf { it.amount } }.maxByOrNull { it.value }
                    val detail = top?.let { (name, value) ->
                        if (variant % 2 == 0) " Tu categoría con mayor gasto fue $name: ${money(value)}." else " Donde más gastaste fue en $name (${money(value)})."
                    }.orEmpty()
                    VoiceOutcome.Answer("${query.period.sentenceStart()} gastaste ${money(total)}.$detail")
                }
            }
        }
        is VoiceQuery.Earned -> {
            val total = data.movementsIn(query.period, today).filter { it.income }.sumOf { it.amount }
            if (total <= 0) VoiceOutcome.Answer("No registraste ingresos ${query.period.label}.")
            else VoiceOutcome.Answer("${query.period.sentenceStart()} ingresaste ${money(total)}.")
        }
        VoiceQuery.Balance -> {
            val balance = data.availableBalance()
            val goals = if (data.savings > 0) " Además, tienes ${money(data.savings)} separados en tus metas." else ""
            if (data.movements.isEmpty()) VoiceOutcome.Answer("Aún no tienes movimientos registrados. Tu saldo disponible es ${money(0.0)}.")
            else VoiceOutcome.Answer("Tienes ${money(balance)} disponibles.$goals")
        }
        is VoiceQuery.TopExpenses -> {
            val top = data.movementsIn(query.period, today).filterNot { it.income }.sortedByDescending { it.amount }.take(3)
            if (top.isEmpty()) VoiceOutcome.Answer("No tienes gastos ${query.period.label}.")
            else VoiceOutcome.Answer(
                "Tus mayores gastos ${query.period.label}: " + top.joinToString(", ", limit = 3) { "${it.title} ${money(it.amount)}" } + ".",
                top.map { "${it.title} · ${it.budgetCategory()} · ${money(it.amount)}" },
            )
        }
        is VoiceQuery.Saved -> {
            val items = data.movementsIn(query.period, today)
            val income = items.filter { it.income }.sumOf { it.amount }
            val spent = items.filterNot { it.income }.sumOf { it.amount }
            val net = income - spent
            val goals = if (data.savings > 0) " En tus metas tienes ${money(data.savings)} ahorrados en total." else ""
            when {
                income <= 0 && spent <= 0 -> VoiceOutcome.Answer("No tienes movimientos ${query.period.label}.$goals")
                net >= 0 -> VoiceOutcome.Answer("${query.period.sentenceStart()} ingresaste ${money(income)} y gastaste ${money(spent)}: te quedaron ${money(net)}.$goals")
                else -> VoiceOutcome.Answer("${query.period.sentenceStart()} gastaste ${money(-net)} más de lo que ingresaste.$goals")
            }
        }
        VoiceQuery.Recent -> {
            val recent = data.movements.sortedWith(compareByDescending<Movement> { it.epochDay() }.thenByDescending { it.id }).take(5)
            if (recent.isEmpty()) VoiceOutcome.Answer("Aún no tienes movimientos registrados.")
            else VoiceOutcome.Answer(
                "Estos son tus últimos ${recent.size} movimientos.",
                recent.map { item ->
                    val day = when (item.epochDay()) { today -> "Hoy"; today - 1 -> "Ayer"; else -> "${item.day}/${item.month}" }
                    "$day · ${item.title} · ${if (item.income) "+" else "−"}${money(item.amount)}"
                },
            )
        }
    }
}
