package com.example.dinex

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationViewModelTest {
    private class FakeQuota(var left: Int) : AiQuota {
        var used = 0
        override fun aiQueriesLeft() = left
        override suspend fun syncedAiQueriesLeft() = left
        override fun recordAiQuery() { used++; left-- }
    }

    private class FakeAssistant(private val reply: suspend (String) -> GeminiReply) : GeminiAssistant {
        var questions = 0
        var context = ""
        override val isConfigured = true
        override suspend fun ask(message: String, financialContext: String): GeminiReply {
            questions++
            context = financialContext
            return reply(message)
        }
    }

    private fun mainTest(block: suspend () -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    @Test fun answeredQuestionSpendsOneQuery() = mainTest {
        val quota = FakeQuota(3)
        val model = AssistantViewModel(quota)
        val assistant = FakeAssistant { GeminiReply("Gasta menos en taxi") }
        model.send("  ¿En qué gasto más?  ", assistant, FinancialData(savings = 25.0))
        val state = model.state.value
        assertEquals(listOf("¿En qué gasto más?", "Gasta menos en taxi"), state.messages.drop(1).map { it.text })
        assertEquals(1, quota.used)
        assertEquals(2, state.queriesLeft)
        assertFalse(state.loading)
        assertTrue("Ahorro acumulado: S/ 25.00" in assistant.context)
    }

    @Test fun failedAnswerDoesNotSpendAQuery() = mainTest {
        val quota = FakeQuota(1)
        val model = AssistantViewModel(quota)
        model.send("Hola", FakeAssistant { error("sin internet") }, FinancialData())
        assertEquals(0, quota.used)
        assertEquals(1, model.state.value.queriesLeft)
        assertTrue(model.state.value.messages.last().error)
    }

    @Test fun noQuestionIsSentAfterTheDailyLimit() = mainTest {
        val model = AssistantViewModel(FakeQuota(0))
        val assistant = FakeAssistant { GeminiReply("No debería responder") }
        model.send("Hola", assistant, FinancialData())
        assertEquals(0, assistant.questions)
        assertTrue(model.state.value.messages.last().text.startsWith("Ya usaste tus"))
    }

    @Test fun secondQuestionWaitsForTheFirstAnswer() = mainTest {
        val answer = CompletableDeferred<GeminiReply>()
        val assistant = FakeAssistant { answer.await() }
        val model = AssistantViewModel(FakeQuota(3))
        model.send("Primera", assistant, FinancialData())
        model.send("Segunda", assistant, FinancialData())
        assertEquals(1, assistant.questions)
        answer.complete(GeminiReply("Listo"))
        assertFalse(model.state.value.loading)
    }

    private val market = MarketSnapshot(3.7, emptyList(), emptyList(), emptyList())

    @Test fun marketKeepsLastPricesWhenAnUpdateFails() = mainTest {
        var fail = false
        val model = MarketViewModel(load = { if (fail) error("HTTP 429") else market })
        model.refresh()
        assertEquals(market, model.state.value.snapshot)
        assertNull(model.state.value.error)
        fail = true
        model.refresh()
        assertEquals(market, model.state.value.snapshot)
        assertTrue(assertNotNull(model.state.value.error).contains("muchas consultas"))
        assertFalse(model.state.value.loading)
    }

    @Test fun marketIgnoresARefreshWhileOneIsRunning() = mainTest {
        var calls = 0
        val pending = CompletableDeferred<MarketSnapshot>()
        val model = MarketViewModel(load = { calls++; pending.await() })
        model.refresh()
        model.refresh()
        assertEquals(1, calls)
        pending.complete(market)
        assertEquals(market, model.state.value.snapshot)
    }
}
