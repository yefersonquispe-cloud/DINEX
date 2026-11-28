package com.example.dinex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal data class YupayGoal(val name: String, val targetCents: Int)
/**
 * [goalId]: meta de Dinex que recibe lo que entra en la alcancía (null = no se suma a ninguna).
 * [lastCoinId]: última moneda ya guardada como ahorro; -1 = todavía no se tomó el punto de partida.
 */
internal data class YupaySettings(
    val linked: Boolean = false,
    val goal: YupayGoal? = null,
    val goalId: Int? = null,
    val lastCoinId: Int = -1,
)
internal val yupayCoins = listOf(10, 20, 50, 100, 200, 500)
internal fun yupayMoney(cents: Long): String = "S/ ${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
internal fun yupayPercent(total: Long, goal: YupayGoal?): Float =
    if (goal == null || goal.targetCents <= 0) 0f else (total.toDouble() / goal.targetCents).coerceIn(0.0, 1.0).toFloat()

internal data class YupayDevice(
    val event: String?, val on: Boolean?, val totalCents: Long, val coins: Int,
    val counts: Map<Int, Int>, val rssi: Int?, val uptimeSeconds: Long?,
)
internal data class YupayLive(val device: YupayDevice?, val receivedAt: Long?, val connection: String?)
internal data class YupayToday(val coinCents: Int, val quantity: Int, val sumCents: Long)
internal data class YupayRecent(val hour: String, val coinCents: Int, val quantity: Int, val totalCents: Long)
internal data class YupayDay(val day: String, val sumCents: Long)
internal data class YupayHistory(val today: List<YupayToday>, val recent: List<YupayRecent>, val days: List<YupayDay> = emptyList())
internal class YupayApiException(message: String) : Exception(message)

private fun Map<*, *>.int(key: String): Int? = (this[key] as? Number)?.toDouble()?.let { number ->
    number.takeIf { it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()
}
private fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toDouble()?.let { number ->
    number.takeIf { it.isFinite() && it >= 0 && it <= 9_007_199_254_740_991.0 && it % 1.0 == 0.0 }?.toLong()
}
private fun coinCents(value: Any?): Int? {
    val text = value as? String ?: return null
    return yupayCoins.firstOrNull { text == "${it / 100}.${(it % 100).toString().padStart(2, '0')}" }
}
private fun jsonMap(text: String): Map<*, *> = runCatching { parseJson(text) as? Map<*, *> }.getOrNull()
    ?: throw YupayApiException("Respuesta inválida de la alcancía.")

internal fun parseYupayLive(text: String): YupayLive {
    val root = jsonMap(text)
    val raw = root["estado"]
    val received = root.long("recibido")
    val connection = (root["conexion"] as? String)?.take(30)
    if (raw == null) return YupayLive(null, received, connection)
    val state = raw as? Map<*, *> ?: throw YupayApiException("Estado inválido de la alcancía.")
    val total = state.long("total_cent") ?: throw YupayApiException("Total inválido de la alcancía.")
    val coins = state.int("monedas")?.takeIf { it >= 0 } ?: throw YupayApiException("Conteo inválido de la alcancía.")
    val rawCounts = state["conteo"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val counts = rawCounts.mapNotNull { (key, value) ->
        val coin = coinCents(key)
        val count = (value as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()
        if (coin == null || count == null) null else coin to count
    }.toMap()
    val rssi = state.int("rssi")?.takeIf { it in -127..0 }
    val uptime = state.long("uptime_s")
    return YupayLive(YupayDevice(state["evento"] as? String, state["encendido"] as? Boolean,
        total, coins, counts, rssi, uptime), received, connection)
}

internal fun parseYupayHistory(text: String): YupayHistory {
    val root = jsonMap(text)
    if (root["ok"] != true) throw YupayApiException("No pudimos cargar el historial.")
    val rawToday = root["hoy"] as? List<*> ?: throw YupayApiException("Historial inválido.")
    val rawRecent = root["ultimas"] as? List<*> ?: throw YupayApiException("Historial inválido.")
    // Una fila con datos raros se omite; no debe dejar sin historial a la persona.
    val today = rawToday.take(50).mapNotNull { item ->
        val row = item as? Map<*, *> ?: return@mapNotNull null
        YupayToday(coinCents(row["moneda"]) ?: return@mapNotNull null,
            row.int("ingresadas")?.takeIf { it >= 0 } ?: return@mapNotNull null,
            row.long("suma_cent") ?: return@mapNotNull null)
    }
    val recent = rawRecent.take(100).mapNotNull { item ->
        val row = item as? Map<*, *> ?: return@mapNotNull null
        YupayRecent((row["hora"] as? String)?.take(40) ?: return@mapNotNull null,
            coinCents(row["moneda"]) ?: return@mapNotNull null,
            row.int("cantidad")?.takeIf { it > 0 } ?: return@mapNotNull null,
            row.long("total_cent") ?: return@mapNotNull null)
    }
    // Opcional: los servidores anteriores no envían el total por día.
    val days = (root["dias"] as? List<*>).orEmpty().take(31).mapNotNull { item ->
        val row = item as? Map<*, *> ?: return@mapNotNull null
        val day = (row["dia"] as? String)?.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) } ?: return@mapNotNull null
        YupayDay(day, row.long("suma_cent") ?: return@mapNotNull null)
    }
    return YupayHistory(today, recent, days)
}

/** Cada descenso inicia un tramo nuevo; nunca dibujamos una línea que esconda un reinicio. */
internal fun yupayLineSegments(recent: List<YupayRecent>): List<List<YupayRecent>> {
    val segments = mutableListOf<MutableList<YupayRecent>>()
    recent.asReversed().forEach { item ->
        if (segments.isEmpty() || item.totalCents < segments.last().last().totalCents) segments += mutableListOf(item)
        else segments.last() += item
    }
    return segments
}

/** Monedas que entraron a la alcancía después de [YupaySettings.lastCoinId]. */
internal data class YupayCoinRow(val id: Int, val cents: Int, val count: Int)
internal data class YupayNewCoins(val rows: List<YupayCoinRow>, val lastId: Int)

internal fun parseYupayNewCoins(text: String): YupayNewCoins {
    val root = jsonMap(text)
    if (root["ok"] != true) throw YupayApiException("No pudimos leer las monedas nuevas.")
    val raw = root["monedas"] as? List<*> ?: throw YupayApiException("Monedas inválidas de la alcancía.")
    val rows = raw.take(500).mapNotNull { item ->
        val row = item as? Map<*, *> ?: return@mapNotNull null
        val id = row.int("id")?.takeIf { it > 0 } ?: return@mapNotNull null
        val cents = row.int("centimos")?.takeIf { it in yupayCoins } ?: return@mapNotNull null
        YupayCoinRow(id, cents, (row.int("nuevas") ?: 1).coerceIn(1, 100))
    }.sortedBy { it.id }
    // "ultimo" es el id más alto de la tabla: sirve como punto de partida la primera vez.
    val last = maxOf(root.int("ultimo")?.takeIf { it >= 0 } ?: 0, rows.maxOfOrNull { it.id } ?: 0)
    return YupayNewCoins(rows, last)
}

internal enum class YupayCommand(val wire: String) { ON("on"), OFF("off"), STATUS("estado"), RESET("reset") }
internal fun yupayCommandBody(command: YupayCommand) = "{\"cmd\":\"${command.wire}\"}"

/** La cuenta no puede usar la alcancía: sin sesión, sesión vencida o cuenta no autorizada por el servidor. */
internal class YupayAccessException(val forbidden: Boolean, val unverified: Boolean = false) : Exception(
    when {
        unverified -> "Verifica tu correo o entra con Google para abrir tu alcancía."
        forbidden -> "Esta alcancía no está vinculada a tu cuenta."
        else -> "Vuelve a iniciar sesión para ver tu alcancía."
    },
)

/**
 * Cliente de la alcancía (ESP32 → Node-RED). Cada consulta lleva la sesión de la cuenta de DINEX y solo
 * va a esta dirección; sin sesión no se consulta nada. Quién puede entrar lo decide el servidor (401/403).
 */
internal class YupayApi(
    private val token: suspend (renew: Boolean) -> String?,
    private val http: suspend (String, String, String?, String?) -> CloudResponse = ::cloudRequest,
) {
    private suspend fun call(method: String, path: String, body: String?, renew: Boolean): CloudResponse {
        val session = token(renew)?.takeIf { it.isNotBlank() } ?: throw YupayAccessException(forbidden = false)
        return http(method, YUPAY_BASE + path, body, session)
    }

    private suspend fun request(method: String, path: String, body: String? = null): String {
        val response = try {
            // OrNull: un tiempo agotado es un error normal, no debe cancelar la actualización de la pantalla.
            withTimeoutOrNull(8_000) {
                val first = call(method, path, body, renew = false)
                // 401 significa que el servidor no atendió el pedido: se renueva la sesión y se intenta una sola vez más.
                if (first.status == 401) call(method, path, body, renew = true) else first
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (access: YupayAccessException) { throw access }
        catch (_: Throwable) { null } ?: throw YupayApiException("No hay respuesta de la alcancía. Revisa tu internet.")
        if (response.status == 401) throw YupayAccessException(forbidden = false)
        if (response.status == 403) throw YupayAccessException(forbidden = true, unverified = "\"VERIFICA_CORREO\"" in response.body)
        if (response.status !in 200..299) throw YupayApiException("La alcancía no respondió. Inténtalo otra vez.")
        if (response.body.length > 131_072) throw YupayApiException("Respuesta inválida de la alcancía.")
        return response.body
    }

    suspend fun live(): YupayLive = parseYupayLive(request("GET", "estado"))
    suspend fun history(): YupayHistory = parseYupayHistory(request("GET", "historial"))
    suspend fun newCoins(after: Int): YupayNewCoins = parseYupayNewCoins(request("GET", "monedas?desde=${after.coerceAtLeast(0)}"))

    /** Las órdenes mueven el cajón físico: nunca se repiten solas. */
    suspend fun command(command: YupayCommand) {
        val result = jsonMap(request("POST", "cmd", yupayCommandBody(command)))
        if (result["ok"] != true) throw YupayApiException(
            if (result["codigo"] == "OFFLINE") "La alcancía está desconectada: la orden no se envió." else "La alcancía rechazó la orden.",
        )
    }
}

internal const val YUPAY_BASE = "https://dinex.tecsup20.site/alcancia/api/"

/** Total de hoy en céntimos. */
internal fun YupayHistory.todayCents(): Long = today.sumOf { it.sumCents }
