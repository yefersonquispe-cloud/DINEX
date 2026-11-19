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

    /** Firebase de mentira que permite simular cortes de internet y respuestas de error. */
    private class CloudFixture(googleName: String = "Ana", private val profileExists: Boolean = true) {
        var offline = false
        var resetError = false
        var deleteError: String? = null
        var remoteAt = 5L
        var remote = FinancialData(onboardingCompleted = true)
        val requests = mutableListOf<Pair<String, String>>()
        val store = MemoryStore()
        private val account = """"localId":"uid-1","email":"ana@gmail.com","idToken":"t","refreshToken":"r","expiresIn":"3600""""
        val cloud = DinexCloud(http = { method, url, body, _ ->
            if (offline) throw IllegalStateException("sin internet")
            requests += method to url + " " + body.orEmpty()
            when {
                "signInWithPassword" in url -> CloudResponse(200, "{$account}")
                "signInWithIdp" in url -> CloudResponse(200, """{$account,"displayName":"$googleName","isNewUser":${!profileExists}}""")
                "sendOobCode" in url && resetError -> CloudResponse(400, """{"error":{"message":"EMAIL_NOT_FOUND"}}""")
                ":delete" in url && deleteError != null -> CloudResponse(400, """{"error":{"message":"$deleteError"}}""")
                method == "GET" && !profileExists -> CloudResponse(404, "{}")
                method == "GET" -> CloudResponse(200, """{"fields":{"name":{"stringValue":"Ana"},"email":{"stringValue":"ana@gmail.com"},"profile":{"stringValue":${jsonString(cloudProfileText(remote))}},"updatedAt":{"integerValue":"$remoteAt"}}}""")
                else -> CloudResponse(200, "{}")
            }
        })
        val repository = DinexRepository(store, migrateLegacy = false)
        val model = DinexViewModel(repository, cloud)
    }

    @Test fun acceptingTermsCanBeRetriedAfterLosingConnection() = googleTest {
        val fixture = CloudFixture(profileExists = false)
        fixture.model.signInWithGoogle(FakeGoogle)
        fixture.offline = true
        fixture.model.acceptGoogleTerms()
        assertNull(fixture.model.state.value.email)
        assertTrue(fixture.model.state.value.googleTermsRequired)
        assertTrue(fixture.model.state.value.authError != null)
        fixture.offline = false
        fixture.model.acceptGoogleTerms()
        assertEquals("ana@gmail.com", fixture.model.state.value.email)
    }

    @Test fun passwordResetNeverRevealsWhetherAnEmailIsRegistered() = googleTest {
        val fixture = CloudFixture().apply { resetError = true }
        fixture.model.sendPasswordReset("nadie@gmail.com")
        assertNull(fixture.model.state.value.authError)
        assertTrue(fixture.model.state.value.authNotice.orEmpty().startsWith("Si nadie@gmail.com tiene una cuenta"))
    }

    @Test fun changesMadeOutsideTheAppReachTheCloudWhenReturning() = googleTest {
        val fixture = CloudFixture()
        fixture.model.signIn("ana@gmail.com", "Clave123!")
        val voice = Movement(1, "Por voz", "Comida", 10.0, false, 1, "Voz")
        fixture.repository.saveProfile("ana@gmail.com", fixture.model.state.value.data.copy(movements = listOf(voice)))
        fixture.requests.clear()
        fixture.model.refreshProfile()
        assertTrue(fixture.requests.any { it.first == "PATCH" && "Por voz" in it.second })
    }

    @Test fun changesFromAnotherDeviceArriveWhenReturningToTheApp() = googleTest {
        val fixture = CloudFixture()
        fixture.model.signIn("ana@gmail.com", "Clave123!")
        fixture.remote = FinancialData(onboardingCompleted = true, movements = listOf(Movement(9, "Desde la web", "Otros", 7.0, false, 1, "Yape")))
        fixture.remoteAt = epochMillis() + 60_000
        fixture.model.refreshProfile()
        assertEquals("Desde la web", fixture.model.state.value.data.movements.single().title)
    }

    @Test fun cloudCopyReturnsWhenFirebaseAsksToLoginBeforeDeleting() = googleTest {
        val fixture = CloudFixture().apply { deleteError = "CREDENTIAL_TOO_OLD_LOGIN_AGAIN" }
        fixture.model.signIn("ana@gmail.com", "Clave123!")
        var result: String? = null
        fixture.model.deleteAccount { result = it }
        assertTrue(result.orEmpty().contains("vuelve a entrar"))
        assertEquals("ana@gmail.com", fixture.model.state.value.email)
        val removed = fixture.requests.indexOfLast { it.first == "DELETE" }
        assertTrue(fixture.requests.indexOfLast { it.first == "PATCH" } > removed)
    }

    @Test fun veryLongGoogleNamesFitTheCloudRules() = googleTest {
        val fixture = CloudFixture(googleName = "A".repeat(120), profileExists = false)
        fixture.model.signInWithGoogle(FakeGoogle, acceptedTerms = true)
        val upload = fixture.requests.first { it.first == "PATCH" }.second
        assertTrue("A".repeat(80) in upload && "A".repeat(81) !in upload)
    }
}
