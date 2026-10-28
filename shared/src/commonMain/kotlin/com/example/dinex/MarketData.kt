package com.example.dinex

/** One live price shown in the market screen. */
internal data class MarketQuote(
    val id: String,
    val symbol: String,
    val name: String,
    val pricePen: Double,
    val priceUsd: Double,
    /** Percent change over 24 h, or null when the source does not give one. */
    val change24h: Double? = null,
    /** Hourly prices in US$ for the last 7 days, oldest first. */
    val trend: List<Double> = emptyList(),
    val change7d: Double? = null,
    val rank: Int? = null,
    val marketCapUsd: Double? = null,
    val volumeUsd: Double? = null,
    val high24hUsd: Double? = null,
    val low24hUsd: Double? = null,
)

internal data class MarketSnapshot(
    val penPerUsd: Double,
    val currencies: List<MarketQuote>,
    val metals: List<MarketQuote>,
    val crypto: List<MarketQuote>,
)

/** Dialog settings for a screen that must also cover the system bars (Android draws edge to edge). */
internal expect fun fullScreenDialogProperties(): androidx.compose.ui.window.DialogProperties

/** GET a URL and return its body as text; throws on network or HTTP errors. */
internal expect suspend fun fetchMarketText(url: String): String

private const val CoinGecko = "https://api.coingecko.com/api/v3"
private const val CryptoCount = 30

/** Currencies people in Peru compare against, in display order (code to Spanish name). */
private val Currencies = listOf(
    "usd" to "Dólar estadounidense",
    "eur" to "Euro",
    "gbp" to "Libra esterlina",
    "jpy" to "Yen japonés",
    "cny" to "Yuan chino",
    "cad" to "Dólar canadiense",
    "chf" to "Franco suizo",
    "brl" to "Real brasileño",
    "mxn" to "Peso mexicano",
    "clp" to "Peso chileno",
    "cop" to "Peso colombiano",
    "ars" to "Peso argentino",
    "bob" to "Boliviano",
    "uyu" to "Peso uruguayo",
)

/** Loads every price from CoinGecko's free public API (no key needed): two calls per refresh. */
internal suspend fun loadMarketSnapshot(): MarketSnapshot {
    val rates = fetchMarketText("$CoinGecko/exchange_rates")
    val coins = fetchMarketText(
        "$CoinGecko/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=$CryptoCount&page=1" +
            "&sparkline=true&price_change_percentage=24h,7d",
    )
    return parseMarketSnapshot(rates, coins)
}

/**
 * exchange_rates gives how many units of each asset one BTC is worth, so any
 * pair is a ratio: USD→PEN = pen / usd, one ounce of gold in USD = usd / xau.
 */
internal fun parseMarketSnapshot(ratesJson: String, coinsJson: String): MarketSnapshot {
    val rates = ((parseJson(ratesJson) as? Map<*, *>)?.get("rates") as? Map<*, *>)
        ?: error("Respuesta de tipos de cambio inválida")
    fun rateOrNull(code: String): Double? = ((rates[code] as? Map<*, *>)?.get("value") as? Double)?.takeIf { it > 0 }
    fun rate(code: String): Double = rateOrNull(code) ?: error("Falta el valor de $code")
    val pen = rate("pen")
    val penPerUsd = pen / rate("usd")

    fun perUnit(code: String, symbol: String, name: String): MarketQuote? {
        val inPen = pen / (rateOrNull(code) ?: return null)
        return MarketQuote(code, symbol, name, inPen, inPen / penPerUsd)
    }
    val currencies = Currencies.mapNotNull { (code, name) -> perUnit(code, code.uppercase(), name) }
    val metals = listOfNotNull(
        perUnit("xau", "Au", "Oro"),
        perUnit("xag", "Ag", "Plata"),
    )

    val coins = (parseJson(coinsJson) as? List<*>) ?: error("Respuesta de criptomonedas inválida")
    val crypto = coins.filterIsInstance<Map<*, *>>().mapNotNull { coin ->
        val id = coin["id"] as? String ?: return@mapNotNull null
        val priceUsd = coin["current_price"] as? Double ?: return@mapNotNull null
        MarketQuote(
            id = id,
            symbol = (coin["symbol"] as? String).orEmpty().uppercase(),
            name = coin["name"] as? String ?: id,
            pricePen = priceUsd * penPerUsd,
            priceUsd = priceUsd,
            change24h = coin["price_change_percentage_24h"] as? Double,
            trend = ((coin["sparkline_in_7d"] as? Map<*, *>)?.get("price") as? List<*>)?.filterIsInstance<Double>().orEmpty(),
            change7d = coin["price_change_percentage_7d_in_currency"] as? Double,
            rank = (coin["market_cap_rank"] as? Double)?.toInt(),
            marketCapUsd = coin["market_cap"] as? Double,
            volumeUsd = coin["total_volume"] as? Double,
            high24hUsd = coin["high_24h"] as? Double,
            low24hUsd = coin["low_24h"] as? Double,
        )
    }
    return MarketSnapshot(penPerUsd, currencies, metals, crypto)
}

/**
 * Minimal JSON reader for the market responses: objects become maps, arrays
 * lists, every number a Double. Avoids adding a serialization library.
 */
internal fun parseJson(text: String): Any? = JsonReader(text).run {
    val value = readValue()
    skipSpace()
    require(index == text.length) { "JSON con datos extra" }
    value
}

private class JsonReader(private val text: String) {
    var index = 0

    fun skipSpace() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    fun readValue(): Any? {
        skipSpace()
        require(index < text.length) { "JSON incompleto" }
        return when (val c = text[index]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c.isDigit()) readNumber() else error("JSON inválido en $index")
        }
    }

    private fun literal(word: String, value: Any?): Any? {
        require(text.startsWith(word, index)) { "JSON inválido en $index" }
        index += word.length
        return value
    }

    private fun readObject(): Map<String, Any?> {
        index++
        val result = LinkedHashMap<String, Any?>()
        skipSpace()
        if (text.getOrNull(index) == '}') { index++; return result }
        while (true) {
            skipSpace()
            val key = readString()
            skipSpace()
            require(text.getOrNull(index) == ':') { "Falta ':' en $index" }
            index++
            result[key] = readValue()
            skipSpace()
            when (text.getOrNull(index)) {
                ',' -> index++
                '}' -> { index++; return result }
                else -> error("Falta ',' o '}' en $index")
            }
        }
    }

    private fun readArray(): List<Any?> {
        index++
        val result = ArrayList<Any?>()
        skipSpace()
        if (text.getOrNull(index) == ']') { index++; return result }
        while (true) {
            result += readValue()
            skipSpace()
            when (text.getOrNull(index)) {
                ',' -> index++
                ']' -> { index++; return result }
                else -> error("Falta ',' o ']' en $index")
            }
        }
    }

    private fun readString(): String {
        require(text.getOrNull(index) == '"') { "Se esperaba texto en $index" }
        index++
        val out = StringBuilder()
        while (true) {
            require(index < text.length) { "Texto sin cerrar" }
            when (val c = text[index++]) {
                '"' -> return out.toString()
                '\\' -> {
                    when (val escaped = text[index++]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> { out.append(text.substring(index, index + 4).toInt(16).toChar()); index += 4 }
                        else -> out.append(escaped)
                    }
                }
                else -> out.append(c)
            }
        }
    }

    private fun readNumber(): Double {
        val start = index
        while (index < text.length && (text[index].isDigit() || text[index] in "+-.eE")) index++
        return text.substring(start, index).toDouble()
    }
}
