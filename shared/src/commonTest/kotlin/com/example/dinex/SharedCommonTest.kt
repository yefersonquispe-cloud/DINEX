package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun voiceExpenseUsesPeruvianAmountAndCategory() {
        val movement = assertNotNull(parseVoiceCommand("Dinex registra 20,50 soles en comida", 7, "Lima"))
        assertEquals(7, movement.id)
        assertEquals(20.50, movement.amount)
        assertEquals("Comida", movement.category)
        assertFalse(movement.income)
        assertEquals("Lima", movement.location)
    }

    @Test
    fun metroRideIsNotConfusedWithMetroSupermarket() {
        val ride = assertNotNull(parseVoiceCommand("Gasté 5 soles en pasaje del metro", 1, null))
        val supermarket = assertNotNull(parseVoiceCommand("Gasté 35 soles en Metro", 2, null))
        assertEquals("Transporte", ride.category)
        assertEquals("Comida", supermarket.category)
    }

    @Test
    fun reminderDateIsNotMistakenForAnAmount() {
        val reminder = assertNotNull(parseVoiceReminder("Recuérdame pagar internet el 15", 3))
        assertEquals("15", reminder.date)
        assertEquals(0.0, reminder.amount)

        val priced = assertNotNull(parseVoiceReminder("Agenda pagar internet mañana por 49.90 soles", 4))
        assertEquals("MAÑANA", priced.date)
        assertEquals(49.90, priced.amount)
    }

    @Test
    fun importedNegativeMovementRemainsAnExpense() {
        val csv = """
            Fecha;Descripción;Categoría;Monto;Tipo;Medio
            15/09/2026;Pago Yape;Comida;-20,50;Egreso;Yape
            16/09/2026;Transferencia recibida;Ingreso;100,00;Abono;Yape
        """.trimIndent()
        val movements = parseImportedMovements(csv)
        assertEquals(2, movements.size)
        assertFalse(movements[0].income)
        assertEquals(20.50, movements[0].amount)
        assertTrue(movements[1].income)
        assertEquals(100.0, movements[1].amount)
    }

    @Test
    fun credentialsAreDeterministicWithoutKeepingPlainText() {
        val first = credentialDigest("persona@dinex.pe", "ClaveSegura#9")
        val repeated = credentialDigest("persona@dinex.pe", "ClaveSegura#9")
        val different = credentialDigest("persona@dinex.pe", "OtraClave#9")
        assertEquals(first, repeated)
        assertNotEquals(first, different)
        assertFalse(first.contains("ClaveSegura"))
    }

    @Test
    fun civilDateConversionUsesUnixEpochCorrectly() {
        assertEquals(DinexDate(1970, 1, 1), civilDateFromEpochDays(0))
    }
}
