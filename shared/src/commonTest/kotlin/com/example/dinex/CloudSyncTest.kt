package com.example.dinex

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncTest {
    private val goal = SavingsGoal(1, "Viaje", 500.0, 120.0, "imagen-grande")
    private val data = FinancialData(
        movements = listOf(Movement(1, "Almuerzo \"menú\"", "Comida", 18.5, false, 3, "Yape")),
        savings = 120.0, goals = listOf(goal), goalName = "Viaje", goalTarget = 500.0, goalArtwork = "imagen-grande",
    )

    @Test fun cloudCopyLeavesOutHeavyArtwork() {
        val text = cloudProfileText(data)
        assertTrue("imagen-grande" !in text)
        val restored = unpackProfileOrNull(text)!!
        assertEquals(data.movements, restored.movements)
        assertEquals("", restored.goals.single().artwork)
    }

    @Test fun downloadKeepsLocalArtwork() {
        val cloud = unpackProfileOrNull(cloudProfileText(data))!!
        val merged = mergeCloudProfile(cloud, data)
        assertEquals("imagen-grande", merged.goals.single().artwork)
        assertEquals("imagen-grande", merged.goalArtwork)
    }

    @Test fun brokenCloudDataIsIgnored() {
        assertNull(unpackProfileOrNull("basura"))
        assertNull(unpackProfileOrNull(null))
    }

    @Test fun jsonTextSurvivesRoundTrip() {
        val original = "línea 1\nComillas \" y \\ barra\ttab"
        assertEquals(original, parseJson(jsonString(original)))
    }

    @Test fun firebaseErrorsUseSimpleSpanish() {
        assertEquals("Correo o contraseña incorrectos.", cloudErrorMessage("INVALID_LOGIN_CREDENTIALS"))
        assertTrue(sessionRevoked("TOKEN_EXPIRED"))
    }

    @Test fun googleCredentialCreatesFirebaseSession() = kotlinx.coroutines.test.runTest {
        var requestBody = ""
        val cloud = DinexCloud(http = { method, url, body, _ ->
            assertEquals("POST", method)
            assertTrue(url.contains("signInWithIdp"))
            requestBody = body.orEmpty()
            CloudResponse(200, """{"localId":"uid-1","email":"persona@gmail.com","displayName":"Ana Quispe","idToken":"firebase-token","refreshToken":"refresh","expiresIn":"3600"}""")
        })
        val (session, name, isNew) = cloud.signInWithGoogle(GoogleIdentityToken("google-token", true))
        assertEquals(false, isNew)
        assertTrue(requestBody.contains("id_token=google-token&providerId=google.com"))
        assertEquals("Ana Quispe", name)
        assertEquals("uid-1", session.uid)
        assertEquals("firebase-token", session.idToken)
    }

    @Test fun aiUsageIsStoredApartFromTheProfile() = kotlinx.coroutines.test.runTest {
        val requests = mutableListOf<Triple<String, String, String>>()
        val cloud = DinexCloud(http = { method, url, body, _ ->
            requests += Triple(method, url, body.orEmpty())
            if (method == "GET") CloudResponse(200, """{"fields":{"aiDay":{"integerValue":"20500"},"aiCount":{"integerValue":"2"}}}""")
            else CloudResponse(200, "{}")
        })
        val session = CloudSession("uid-1", "qa@dinex.test", "token", "refresh", Long.MAX_VALUE)
        assertEquals(AiUsage(20500, 2), cloud.aiUsage(session))
        assertTrue(requests.last().second.endsWith("?mask.fieldPaths=aiDay&mask.fieldPaths=aiCount"))

        cloud.saveAiUsage(session, AiUsage(20500, 3))
        val save = requests.last()
        assertTrue(save.second.endsWith("?updateMask.fieldPaths=aiDay&updateMask.fieldPaths=aiCount"))
        assertTrue("\"aiCount\":{\"integerValue\":\"3\"}" in save.third && "profile" !in save.third)

        // Subir el perfil no debe borrar el conteo: solo reemplaza sus propios campos.
        cloud.upload(session, CloudProfile("Ana", "qa@dinex.test", "perfil", 10L))
        assertTrue(requests.last().second.endsWith("?updateMask.fieldPaths=name&updateMask.fieldPaths=email&updateMask.fieldPaths=profile&updateMask.fieldPaths=updatedAt"))
    }

    @Test fun aiUsageMergeKeepsTheHighestCountOfTheLatestDay() {
        val store = object : DinexStore {
            val values = mutableMapOf<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        }
        val repo = DinexRepository(store, migrateLegacy = false)
        repo.recordAiQuery("qa@dinex.test", today = 100)
        repo.mergeAiUsage("qa@dinex.test", AiUsage(100, 3))   // se usó 3 veces en otro equipo
        assertEquals(3, repo.aiQueriesToday("qa@dinex.test", 100))
        repo.mergeAiUsage("qa@dinex.test", AiUsage(100, 1))   // un dato viejo no baja el conteo
        assertEquals(3, repo.aiQueriesToday("qa@dinex.test", 100))
        repo.mergeAiUsage("qa@dinex.test", AiUsage(99, 0))    // un día anterior no cuenta
        assertEquals(3, repo.aiQueriesToday("qa@dinex.test", 100))
        repo.mergeAiUsage("qa@dinex.test", AiUsage(101, 1))   // día nuevo
        assertEquals(1, repo.aiQueriesToday("qa@dinex.test", 101))
    }

    private class MemoryStore : DinexStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    }

    private object FakeGoogle : GoogleIdentity {
        override val available = true
        override suspend fun requestToken() = GoogleIdentityToken("google-token", true)
    }

    /** Firebase de mentira: [hasProfile] indica si la cuenta ya existía. */
    private class GoogleFixture(hasProfile: Boolean) {
        val requests = mutableListOf<Pair<String, String>>()
        val store = MemoryStore()
        val cloud = DinexCloud(http = { method, url, body, _ ->
            requests += method to url + " " + body.orEmpty()
            when {
                "signInWithIdp" in url -> CloudResponse(200, """{"localId":"uid-1","email":"ana@gmail.com","displayName":"Ana","idToken":"t","refreshToken":"r","expiresIn":"3600","isNewUser":${!hasProfile}}""")
                method == "GET" && !hasProfile -> CloudResponse(404, "{}")
                method == "GET" -> CloudResponse(200, """{"fields":{"name":{"stringValue":"Ana"},"email":{"stringValue":"ana@gmail.com"},"profile":{"stringValue":${jsonString(cloudProfileText(FinancialData(onboardingCompleted = true)))}},"updatedAt":{"integerValue":"5"},"termsVersion":{"stringValue":"2026-01-a"},"termsAcceptedAt":{"integerValue":"7"}}}""")
                else -> CloudResponse(200, "{}")
            }
        })
        val model = DinexViewModel(DinexRepository(store, migrateLegacy = false), cloud)
    }

    private fun googleTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        try { block() } finally { kotlinx.coroutines.Dispatchers.resetMain() }
    }

    @Test fun existingGoogleAccountEntersWithoutAskingForTermsAgain() = googleTest {
        val fixture = GoogleFixture(hasProfile = true)
        fixture.model.signInWithGoogle(FakeGoogle)
        assertEquals("ana@gmail.com", fixture.model.state.value.email)
        assertEquals(false, fixture.model.state.value.googleTermsRequired)
        // Se conserva la constancia original: entrar no cuenta como una nueva aceptación.
        assertEquals("2026-01-a", DinexRepository(fixture.store, migrateLegacy = false).termsConsent("ana@gmail.com")?.version)
    }

    @Test fun newGoogleAccountMustAcceptTermsBeforeEntering() = googleTest {
        val fixture = GoogleFixture(hasProfile = false)
        fixture.model.signInWithGoogle(FakeGoogle)
        assertNull(fixture.model.state.value.email)
        assertTrue(fixture.model.state.value.googleTermsRequired)
        fixture.model.acceptGoogleTerms()
        assertEquals("ana@gmail.com", fixture.model.state.value.email)
        assertEquals(false, fixture.model.state.value.googleTermsRequired)
        assertEquals(DinexLegal.VERSION, DinexRepository(fixture.store, migrateLegacy = false).termsConsent("ana@gmail.com")?.version)
        assertTrue(fixture.requests.any { it.first == "PATCH" && "termsVersion" in it.second })
    }

    @Test fun decliningTermsRemovesTheNewGoogleAccount() = googleTest {
        val fixture = GoogleFixture(hasProfile = false)
        fixture.model.signInWithGoogle(FakeGoogle)
        fixture.model.declineGoogleTerms()
        assertNull(fixture.model.state.value.email)
        assertEquals(false, fixture.model.state.value.googleTermsRequired)
        assertTrue(fixture.requests.any { ":delete" in it.second })
    }

    @Test fun termsAcceptedInTheFormAreNotAskedTwice() = googleTest {
        val fixture = GoogleFixture(hasProfile = false)
        fixture.model.signInWithGoogle(FakeGoogle, acceptedTerms = true)
        assertEquals("ana@gmail.com", fixture.model.state.value.email)
        assertEquals(false, fixture.model.state.value.googleTermsRequired)
    }
}
