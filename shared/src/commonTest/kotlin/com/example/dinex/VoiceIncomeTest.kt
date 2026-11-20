package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Oye Dinex debe reconocer los ingresos dichos de muchas formas, y no confundir gastos con ingresos. */
class VoiceIncomeTest {
    private val today = epochDaysOf(2026, 11, 18)
    private val data = FinancialData()

    private fun dictate(phrase: String): Movement = when (val outcome = VoiceConversation().interpret(phrase, data, today)) {
        is VoiceOutcome.Save -> outcome.draft.movement
        is VoiceOutcome.Confirm -> outcome.draft.movement
        else -> fail("\"$phrase\" no se registró como movimiento: $outcome")
    }

    @Test
    fun recognizesIncomePhrases() {
        listOf(
            "Oye Dinex, registra un ingreso de 200 soles",
            "Dinex, ingresé 300 soles",
            "ingresa 150 soles por mi trabajo",
            "recibí 80 soles",
            "gané 45 soles vendiendo postres",
            "me pagaron 1200 soles",
            "mi jefe me pagó 900 soles",
            "me yapeó 35 soles mi primo",
            "me plinearon 20 soles",
            "me depositaron 500 soles",
            "me transfirió 250 soles mi mamá",
            "me llegó mi sueldo de 2500 soles",
            "me dieron una propina de 10 soles",
            "cobré 60 soles por una clase",
            "vendí mi bicicleta en 400 soles",
            "anota mi quincena de 1300 soles",
            "me devolvieron 50 soles",
            "agrega 700 soles de gratificación",
            "me regalaron 100 soles por mi cumpleaños",
            "registra una venta de 90 soles",
        ).forEach { phrase ->
            val movement = dictate(phrase)
            assertEquals(true, movement.income, "\"$phrase\" debería ser ingreso")
            assertEquals("Ingreso", movement.category, "\"$phrase\" debería ir a la categoría Ingreso")
        }
    }

    @Test
    fun keepsExpensesAsExpenses() {
        listOf(
            "registra un gasto de 25 soles en comida",
            "gasté 18 soles en taxi",
            "pagué 120 soles de luz",
            "compré un libro de 60 soles",
            "me cobraron 15 soles por el envío",
            "le yapeé 20 soles a Carlos",
            "anota 12 soles en almuerzo",
            "un gasto de 30 soles en propina para el mozo",
        ).forEach { phrase ->
            assertEquals(false, dictate(phrase).income, "\"$phrase\" debería ser gasto")
        }
    }

    @Test
    fun explicitTypeWins() {
        // "ingreso" dicho explícitamente pesa más que "pagar" en la misma frase.
        assertEquals(true, dictate("registra un ingreso de 300 soles que me pagaron por pagar la cuenta").income)
        // "gasto" explícito gana aunque se mencione el sueldo.
        assertEquals(false, dictate("registra un gasto de 40 soles del sueldo en comida").income)
    }

    @Test
    fun countsSignals() {
        val kind = movementKind(normalizeVoice("me pagaron 200 soles"))
        assertEquals(true, kind.isIncome)
        assertEquals(0, kind.expense)
        assertEquals(false, movementKind(normalizeVoice("me cobraron 10 soles")).isIncome)
    }
}
