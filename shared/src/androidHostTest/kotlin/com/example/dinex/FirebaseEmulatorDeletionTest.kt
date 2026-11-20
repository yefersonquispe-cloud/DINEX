package com.example.dinex

import kotlinx.coroutines.runBlocking
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Prueba de punta a punta de "Eliminar mi cuenta" contra los emuladores locales de Firebase
 * (Auth en 9099 y Firestore en 8085, con firestore.rules). Nunca toca el proyecto real.
 *
 *   npx firebase-tools emulators:start --only auth,firestore --project demo-dinex
 *
 * Si los emuladores no están encendidos, la prueba se omite.
 */
class FirebaseEmulatorDeletionTest {
    private val project = "demo-dinex"
    private val client = HttpClient.newHttpClient()

    /** Envía a los emuladores lo que la app enviaría a Google. */
    private val emulatorHttp: suspend (String, String, String?, String?) -> CloudResponse = { method, url, body, bearer ->
        val local = url
            .replace("https://identitytoolkit.googleapis.com/", "http://127.0.0.1:9099/identitytoolkit.googleapis.com/")
            .replace("https://securetoken.googleapis.com/", "http://127.0.0.1:9099/securetoken.googleapis.com/")
            .replace("https://firestore.googleapis.com/", "http://127.0.0.1:8085/")
        send(method, local, body, bearer)
    }

    private fun send(method: String, url: String, body: String?, bearer: String?): CloudResponse {
        val request = HttpRequest.newBuilder(URI(url.replace("(", "%28").replace(")", "%29")))
            .method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return CloudResponse(response.statusCode(), response.body())
    }

    /** Lectura con permisos de administrador del emulador (se salta las reglas) para comprobar el resultado. */
    private fun adminGet(path: String): CloudResponse =
        send("GET", "http://127.0.0.1:8085/v1/projects/$project/databases/(default)/documents/$path", null, "owner")

    private fun emulatorsRunning() = listOf(9099, 8085).all { port -> runCatching { Socket("127.0.0.1", port).close() }.isSuccess }

    private fun profile(email: String) = CloudProfile("Prueba", email, cloudProfileText(FinancialData(
        movements = listOf(Movement(1, "Almuerzo", "Comida", 18.5, false, 3, "Yape", latitude = -12.05, longitude = -77.04)),
        goals = listOf(SavingsGoal(1, "Viaje", 500.0, 50.0, "")), categoryBudgets = mapOf("Comida" to 300.0),
    )), epochMillis(), TermsConsent(DinexLegal.VERSION, epochMillis()))

    private fun report(id: String) = AiReport(id, "incorrecta", "", "Respuesta de prueba", "¿Cuánto gasté?", "cloud", epochMillis())

    @Test fun deletingAnAccountRemovesOnlyItsOwnDataAndThenTheAccount() = runBlocking {
        if (!emulatorsRunning()) { println("Emuladores de Firebase apagados: se omite la prueba."); return@runBlocking }
        val cloud = DinexCloud(apiKey = "demo-key", projectId = project, http = emulatorHttp)
        val stamp = epochMillis()
        // Cuentas de prueba solo del emulador local.
        val anaEmail = "ana.$stamp@prueba.dinex.test"
        val beaEmail = "bea.$stamp@prueba.dinex.test"
        val password = "Prueba-Dinex-2026!"
        val ana = cloud.signUp(anaEmail, password)
        val bea = cloud.signUp(beaEmail, password)
        cloud.upload(ana, profile(anaEmail))
        cloud.upload(bea, profile(beaEmail))
        repeat(3) { cloud.reportAiAnswer(ana, report("ana-$stamp-$it")) }
        cloud.reportAiAnswer(bea, report("bea-$stamp"))

        // Ana no puede ver ni borrar el reporte ni el perfil de Bea.
        val foreignReport = emulatorHttp("DELETE", "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents/aiReports/bea-$stamp", null, ana.idToken)
        assertEquals(403, foreignReport.status, foreignReport.body)
        val foreignProfile = emulatorHttp("DELETE", "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents/users/${bea.uid}", null, ana.idToken)
        assertEquals(403, foreignProfile.status, foreignProfile.body)
        val readForeign = emulatorHttp("GET", "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents/aiReports/bea-$stamp", null, ana.idToken)
        assertEquals(403, readForeign.status, readForeign.body)

        assertEquals(setOf("password"), cloud.signInMethods(ana))

        // Eliminar la cuenta de Ana (con una sesión recién iniciada, como hace la app).
        val fresh = cloud.signIn(anaEmail, password)
        assertEquals(3, cloud.deleteAiReports(fresh))
        cloud.deleteProfile(fresh)
        cloud.deleteAuthUser(fresh)

        // Lo de Ana ya no existe; lo de Bea sigue intacto.
        repeat(3) { assertEquals(404, adminGet("aiReports/ana-$stamp-$it").status) }
        assertEquals(404, adminGet("users/${ana.uid}").status)
        assertEquals(200, adminGet("aiReports/bea-$stamp").status)
        assertEquals(200, adminGet("users/${bea.uid}").status)

        // Ana no puede volver a entrar ni renovar su sesión en otro equipo.
        val login = assertFailsWith<CloudAuthException> { cloud.signIn(anaEmail, password) }
        assertTrue(login.code in setOf("EMAIL_NOT_FOUND", "INVALID_LOGIN_CREDENTIALS"), login.code)
        // Firebase responde USER_NOT_FOUND (el emulador, INVALID_REFRESH_TOKEN): ambas cierran la sesión.
        val other = assertFailsWith<CloudAuthException> { cloud.refresh(ana) }
        assertTrue(sessionRevoked(other.code), other.code)

        // Un equipo con un token viejo de Ana no vuelve a crear su documento.
        val recreate = assertFailsWith<CloudAuthException> { cloud.upload(ana, profile(anaEmail), mustExist = true) }
        assertEquals("NOT_FOUND", recreate.code)
        assertEquals(404, adminGet("users/${ana.uid}").status)

        // Volver a ejecutar el borrado de reportes sin reportes no falla (se puede reintentar).
        val bea2 = cloud.signIn(beaEmail, password)
        assertEquals(1, cloud.deleteAiReports(bea2))
        assertEquals(0, cloud.deleteAiReports(bea2))
        cloud.deleteAccount(bea2)
        assertEquals(404, adminGet("users/${bea.uid}").status)
    }
}
