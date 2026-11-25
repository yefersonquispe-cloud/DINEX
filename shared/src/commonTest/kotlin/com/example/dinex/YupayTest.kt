package com.example.dinex

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YupayTest {
    private val estado = """{"estado":{"evento":"cambio","encendido":true,"total":"123.20","total_cent":12320,"monedas":111,"conteo":{"0.10":23,"0.50":11,"2.00":12,"0.20":37,"5.00":14,"1.00":14,"9.99":3},"rssi":-56,"uptime_s":317},"recibido":1795585085264,"conexion":"offline"}"""
    private val historial = """{"ok":true,"hoy":[{"moneda":"0.50","ingresadas":5,"suma_cent":250},{"moneda":"rara"}],"ultimas":[{"hora":"25/11 01:29","moneda":"0.50","cantidad":1,"total_cent":50},{"hora":"24/11 23:49","moneda":"1.00","cantidad":14,"total_cent":12320},{"hora":"24/11 23:48","moneda":"1.00","cantidad":13,"total_cent":12220}]}"""

    private class Server(var status: Int = 200, var body: String = "", val renewed: Int = 200) {
        val calls = mutableListOf<List<String?>>()
        suspend fun http(method: String, url: String, body: String?, bearer: String?): CloudResponse {
            calls += listOf(method, url, body, bearer)
            return CloudResponse(if (calls.size > 1) renewed else status, this.body)
        }
    }

    @Test
    fun readsTheRealFormat() {
        val live = parseYupayLive(estado)
        assertEquals(12320L, live.device?.totalCents)
        assertEquals(111, live.device?.coins)
        assertEquals(12320L, live.device!!.counts.entries.sumOf { it.key.toLong() * it.value })
        assertEquals("offline", live.connection)
        assertNull(parseYupayLive("""{"estado":null,"recibido":null,"conexion":null}""").device)
        assertFailsWith<YupayApiException> { parseYupayLive("<html>") }
        assertFailsWith<YupayApiException> { parseYupayLive("""{"estado":{"total_cent":-1,"monedas":2}}""") }
    }

    @Test
    fun historySkipsStrangeRowsAndSplitsResets() {
        val history = parseYupayHistory(historial)
        assertEquals(250L, history.todayCents())
        assertEquals(1, history.today.size)
        assertEquals(listOf(listOf(12220L, 12320L), listOf(50L)), yupayLineSegments(history.recent).map { part -> part.map { it.totalCents } })
        assertFailsWith<YupayApiException> { parseYupayHistory("""{"ok":false,"error":"sin base"}""") }
    }

    @Test
    fun serverCodesBecomeClearMessages() = runTest {
        val days = parseYupayHistory("""{"ok":true,"hoy":[],"ultimas":[],"dias":[{"dia":"2026-11-24","suma_cent":250},{"dia":"mal","suma_cent":1}]}""").days
        assertEquals(listOf(YupayDay("2026-11-24", 250)), days)
        val unverified = assertFailsWith<YupayAccessException> { YupayApi({ "x" }, Server(403, """{"ok":false,"codigo":"VERIFICA_CORREO"}""")::http).live() }
        assertTrue(unverified.unverified)
        val offline = assertFailsWith<YupayApiException> { YupayApi({ "x" }, Server(200, """{"ok":false,"codigo":"OFFLINE"}""")::http).command(YupayCommand.ON) }
        assertEquals("La alcancía está desconectada: la orden no se envió.", offline.message)
    }

    @Test
    fun goalProgressAndAmounts() {
        val goal = YupayGoal("Bici", 20_000)
        assertEquals(.616f, yupayPercent(12_320, goal), .0001f)
        assertEquals(1f, yupayPercent(99_999, goal))
        assertEquals(0f, yupayPercent(500, null))
        assertEquals("S/ 123.05", yupayMoney(12_305))
        assertEquals(1250, yupayInputCents("12,5"))
        listOf("", "0", "1.234", "a", "1.", ".5", "99999999").forEach { assertNull(yupayInputCents(it), it) }
    }

    @Test
    fun withoutSessionNothingIsRequested() = runTest {
        val server = Server(body = estado)
        val error = assertFailsWith<YupayAccessException> { YupayApi({ null }, server::http).live() }
        assertEquals(false, error.forbidden)
        assertTrue(server.calls.isEmpty())
    }

    @Test
    fun sessionOnlyGoesToTheAlcancia() = runTest {
        val server = Server(body = estado)
        YupayApi({ "sesion" }, server::http).live()
        assertEquals(listOf<String?>("GET", "https://dinex.tecsup20.site/alcancia/api/estado", null, "sesion"), server.calls.single())
    }

    @Test
    fun renewsTheSessionOnlyOnce() = runTest {
        val renewals = mutableListOf<Boolean>()
        val ok = Server(status = 401, body = estado)
        YupayApi({ renewals += it; if (it) "nueva" else "vieja" }, ok::http).live()
        assertEquals(listOf(false, true), renewals)
        assertEquals("nueva", ok.calls.last()[3])
        val bad = Server(status = 401, renewed = 401)
        assertEquals(false, assertFailsWith<YupayAccessException> { YupayApi({ "x" }, bad::http).live() }.forbidden)
        assertEquals(2, bad.calls.size)
    }

    @Test
    fun otherAccountIsRejectedAndErrorsHideTheSession() = runTest {
        val denied = assertFailsWith<YupayAccessException> { YupayApi({ "secreta" }, Server(status = 403)::http).history() }
        assertTrue(denied.forbidden)
        assertTrue("secreta" !in denied.message.orEmpty())
        val offline = assertFailsWith<YupayApiException> { YupayApi({ "secreta" }, { _, _, _, bearer -> error("fallo $bearer") }).live() }
        assertTrue("secreta" !in offline.message.orEmpty())
    }

    @Test
    fun aSlowServerIsANormalError() = runTest {
        // Antes el tiempo agotado cancelaba la actualización de la pantalla para siempre.
        assertFailsWith<YupayApiException> { YupayApi({ "x" }, { _, _, _, _ -> kotlinx.coroutines.awaitCancellation() }).live() }
    }

    @Test
    fun commandsAreSentOnce() = runTest {
        val server = Server(body = """{"ok":true}""")
        YupayApi({ "x" }, server::http).command(YupayCommand.OFF)
        assertEquals(listOf<String?>("POST", YUPAY_BASE + "cmd", """{"cmd":"off"}""", "x"), server.calls.single())
        assertEquals(listOf("on", "off", "estado", "reset"), YupayCommand.entries.map { it.wire })
        val rejected = Server(body = """{"ok":false}""")
        assertFailsWith<YupayApiException> { YupayApi({ "x" }, rejected::http).command(YupayCommand.ON) }
        assertEquals(1, rejected.calls.size)
    }

    @Test
    fun goalTravelsWithTheProfileWithoutTouchingMoney() {
        val before = FinancialData(savings = 50.0, categoryBudgets = mapOf("Comida" to 300.0), goals = listOf(SavingsGoal(1, "Laptop", 3000.0, 50.0)), nextGoalId = 2)
        val data = before.copy(yupay = YupaySettings(true, YupayGoal("Mi bici: 2|3", 25_050)))
        val back = unpackProfile(packProfile(data))
        assertEquals(data, back)
        assertEquals(before, back.copy(yupay = YupaySettings()))
        assertEquals(before, unpackProfile(packProfile(before)))
        assertEquals(before.availableBalance(), back.availableBalance())
        // Sin meta solo se guarda el enlace.
        assertEquals(YupaySettings(linked = true), unpackProfile(packProfile(before.copy(yupay = YupaySettings(true)))).yupay)
    }
}
