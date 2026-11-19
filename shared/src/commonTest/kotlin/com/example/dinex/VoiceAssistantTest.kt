package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceAssistantTest {
    // Miércoles 18 de noviembre de 2026 (lunes de esa semana: 16).
    private val today = epochDaysOf(2026, 11, 18)

    private fun item(id: Int, title: String, category: String, amount: Double, day: Int, month: Int = 11, income: Boolean = false) =
        Movement(id, title, category, amount, income, day, "Yape", month = month, year = 2026)

    private val data = FinancialData(
        movements = listOf(
            item(1, "Sueldo", "Ingreso", 1000.0, 2, income = true),
            item(2, "Almuerzo", "Comida", 30.0, 18),
            item(3, "Taxi", "Transporte", 18.0, 17),
            item(4, "Cena", "Comida", 50.0, 16),
            item(5, "Libro", "Estudios", 60.0, 10),
            item(6, "Pasaje", "Transporte", 30.0, 16),
            item(7, "Cine", "Entretenimiento", 25.0, 28, month = 10),
        ),
        savings = 100.0,
    )

    @Test
    fun understandsSpokenNumbersAndWakePhrase() {
        assertEquals("registra 25 soles en comida", normalizeVoice("Oye DINEX, registra veinticinco soles en comida"))
        assertEquals("recibi 1500 soles", normalizeVoice("Recibí mil quinientos soles"))
        assertEquals("gaste 35 soles", normalizeVoice("Gasté treinta y cinco soles"))
        assertEquals("registra un gasto de 18.50 soles", normalizeVoice("registra un gasto de 18 con 50 soles"))
        assertEquals("pague 1500 soles", normalizeVoice("Pagué 1,500 soles"))
    }

    @Test
    fun recordsClearExpenseWithoutAsking() {
        val outcome = VoiceConversation().interpret("Oye DINEX, registra un gasto de 25 soles en comida", data, today)
        val draft = assertIs<VoiceOutcome.Save>(outcome).draft
        assertEquals(25.0, draft.movement.amount)
        assertEquals("Comida", draft.movement.category)
        assertEquals(false, draft.movement.income)
        assertEquals(18, draft.movement.day)
    }

    @Test
    fun asksBeforeSavingRiskyAmounts() {
        val conversation = VoiceConversation()
        val outcome = conversation.interpret("Oye DINEX, registra un ingreso de 1500 soles", data, today)
        val confirm = assertIs<VoiceOutcome.Confirm>(outcome)
        assertTrue(confirm.draft.movement.income)
        assertEquals(1500.0, confirm.draft.movement.amount)
        assertTrue("1500.00" in confirm.prompt)
        // "Sí" guarda lo pendiente; nada se guarda antes de confirmar.
        val saved = assertIs<VoiceOutcome.Save>(conversation.interpret("sí, guárdalo", data, today))
        assertEquals(1500.0, saved.draft.movement.amount)
        assertNull(conversation.pending)
    }

    @Test
    fun declinedConfirmationSavesNothing() {
        val conversation = VoiceConversation()
        assertIs<VoiceOutcome.Confirm>(conversation.interpret("gasté 40", data, today)) // sin moneda
        assertIs<VoiceOutcome.Cancelled>(conversation.interpret("no", data, today))
        assertNull(conversation.pending)
    }

    @Test
    fun dollarsAndYesterdayNeedConfirmation() {
        val dollars = assertIs<VoiceOutcome.Confirm>(VoiceConversation().interpret("gasté 20 dólares en comida", data, today))
        assertTrue(dollars.draft.doubts.any { "dólares" in it })
        val yesterday = assertIs<VoiceOutcome.Confirm>(VoiceConversation().interpret("ayer gasté 12 soles en taxi", data, today))
        assertEquals(17, yesterday.draft.movement.day)
        assertEquals("Transporte", yesterday.draft.movement.category)
    }

    @Test
    fun answersWithRealDataOnly() {
        val week = assertIs<VoiceOutcome.Answer>(VoiceConversation().interpret("Oye DINEX, ¿cuánto gasté esta semana?", data, today))
        // Semana del 16 al 18: 30 + 18 + 50 + 30 = 128, la mayor categoría es Comida (80).
        assertTrue("S/ 128.00" in week.text, week.text)
        assertTrue("Comida" in week.text, week.text)

        val yesterday = assertIs<VoiceOutcome.Answer>(VoiceConversation().interpret("¿cuánto gasté ayer?", data, today))
        assertTrue("S/ 18.00" in yesterday.text, yesterday.text)

        val month = assertIs<VoiceOutcome.Answer>(VoiceConversation().interpret("¿cuánto dinero gasté este mes?", data, today))
        assertTrue("S/ 188.00" in month.text, month.text)

        val balance = assertIs<VoiceOutcome.Answer>(VoiceConversation().interpret("¿cuánto tengo disponible?", data, today))
        // 1000 - 213 de gastos - 100 en metas = 687
        assertTrue("S/ 687.00" in balance.text, balance.text)
    }

    @Test
    fun keepsContextForFollowUps() {
        val conversation = VoiceConversation()
        assertIs<VoiceOutcome.Answer>(conversation.interpret("¿cuánto gasté esta semana?", data, today))
        val transport = assertIs<VoiceOutcome.Answer>(conversation.interpret("¿Y cuánto gasté en transporte?", data, today))
        assertEquals("Esta semana gastaste S/ 48.00 en Transporte.", transport.text)
    }

    @Test
    fun listsTopExpensesAndRecentMovements() {
        val top = assertIs<VoiceOutcome.Answer>(VoiceConversation().interpret("¿cuáles fueron mis mayores gastos?", data, today))
        assertEquals(3, top.items.size)
        assertTrue(top.items.first().startsWith("Libro"), top.items.toString())

        val recent = assertIs<VoiceOutcome.Answer>(VoiceConversation().interpret("muéstrame mis últimos movimientos", data, today))
        assertEquals(5, recent.items.size)
        assertTrue(recent.items.first().startsWith("Hoy · Almuerzo"), recent.items.toString())
    }

    @Test
    fun savedThisWeekUsesIncomeMinusExpenses() {
        val saved = assertIs<VoiceOutcome.Answer>(VoiceConversation().interpret("¿cuánto ahorré esta semana?", data, today))
        assertTrue("S/ 128.00 más" in saved.text, saved.text)
        assertTrue("S/ 100.00" in saved.text, saved.text)
    }

    @Test
    fun unknownQuestionsGoToDinexIaAndCancelWorks() {
        assertIs<VoiceOutcome.AskAi>(VoiceConversation().interpret("¿me conviene comprar una laptop en cuotas?", data, today))
        assertIs<VoiceOutcome.Cancelled>(VoiceConversation().interpret("cancela", data, today))
        assertIs<VoiceOutcome.SaveReminder>(VoiceConversation().interpret("recuérdame pagar internet mañana por 50 soles", data, today))
    }

    @Test
    fun emptyAccountNeverInventsNumbers() {
        val empty = FinancialData()
        val answer = assertIs<VoiceOutcome.Answer>(VoiceConversation().interpret("¿cuánto gasté este mes?", empty, today))
        assertEquals("No registraste gastos este mes.", answer.text)
    }
}
