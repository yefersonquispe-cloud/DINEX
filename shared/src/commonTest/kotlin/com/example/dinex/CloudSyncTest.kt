package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
