package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SharedLogicAndroidHostTest {

    @Test
    fun androidHostCanParseVoiceMovement() {
        val movement = assertNotNull(parseVoiceCommand("Recibí 250 soles por trabajo", 10, null))
        assertEquals(250.0, movement.amount)
        assertEquals(true, movement.income)
        assertEquals("Ingreso", movement.category)
    }
}
