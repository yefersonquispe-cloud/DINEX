package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedCommonTest {
    @Test fun typedAmountsUnderstandThousandsAndDecimals() {
        assertEquals(1500.0, parseMoneyInput("1,500"))
        assertEquals(12.5, parseMoneyInput("12,50"))
        assertEquals(1234.5, parseMoneyInput("1,234.50"))
        assertEquals(12.0, parseMoneyInput("12."))
        assertEquals(null, parseMoneyInput("1.2.3,4567"))
        assertEquals(null, parseMoneyInput(""))
    }

    @Test fun formDayAlwaysExistsInTheCurrentMonth() {
        assertEquals(30, movementDayOfMonth("31", DinexDate(2026, 11, 18)))
        assertEquals(28, movementDayOfMonth("30", DinexDate(2026, 2, 3)))
        assertEquals(18, movementDayOfMonth("", DinexDate(2026, 11, 18)))
    }

    @Test fun voiceCategoriesMatchWholeWordsOnly() {
        assertEquals("Otros", parseVoiceCommand("Gasté 80 soles en un pantalón", 1, null)?.category)
        assertEquals("Comida", parseVoiceCommand("Gasté 4 soles en panes", 2, null)?.category)
        assertEquals("Comida", parseVoiceCommand("Gasté 60 soles en el supermercado", 3, null)?.category)
    }

    @Test fun voiceAmountsKeepTheirThousands() {
        assertEquals(1500.0, parseVoiceCommand("Recibí 1,500 soles de sueldo", 1, null)?.amount)
        assertEquals(20.5, parseVoiceCommand("Gasté 20,50 soles en comida", 2, null)?.amount)
    }

    @Test fun importedDatesAcceptOneDigitDays() {
        assertEquals(DinexDate(2026, 11, 5), parseImportDate("2026-11-5"))
        assertEquals(null, parseImportDate("5/11/6"))
    }

    @Test fun importedCategoriesCountInsideTheBudgets() {
        fun item(category: String) = Movement(1, "Compra", category, 10.0, false, 1, "Yape")
        assertEquals("Comida", item("COMIDA").budgetCategory())
        assertEquals("Otros", item("Alimentos varios").budgetCategory())
    }

    @Test fun repeatedMovementIdsAreRepairedWhenLoading() {
        val item = Movement(4, "Compra", "Otros", 10.0, false, 1, "Yape")
        val restored = unpackProfile(packProfile(FinancialData(movements = listOf(item, item.copy(title = "Otra")))))
        assertEquals(2, restored.movements.map { it.id }.distinct().size)
        assertEquals(listOf("Compra", "Otra"), restored.movements.map { it.title })
    }

    @Test fun labelsUseSingularForOne() {
        assertEquals("1 meta", countLabel(1, "meta", "metas"))
        assertEquals("3 metas", countLabel(3, "meta", "metas"))
        assertEquals("0.00", format2(-0.001))
    }


    @Test
    fun categoryBudgetsAreSuggestedFromMonthlyIncomeAndStartAtZero() {
        assertEquals(0.0, suggestedCategoryBudgets(emptyList())["Comida"])
        val income = Movement(1, "Sueldo", "Ingreso", 1000.0, true, 1, "Yape")
        val suggestions = suggestedCategoryBudgets(listOf(income))
        assertEquals(350.0, suggestions["Comida"])
        assertEquals(200.0, suggestions["Transporte"])
        assertEquals(200.0, suggestions["Estudios"])
        assertEquals(100.0, suggestions["Entretenimiento"])
        assertEquals(150.0, suggestions["Otros"])
        assertEquals(1000.0, suggestions.values.sum())

        val current = civilDateFromEpochDays()
        val pastMonth = if (current.month == 1) 12 else current.month - 1
        val pastYear = if (current.month == 1) current.year - 1 else current.year
        val pastTransport = Movement(2, "Viajes", "Transporte", 200.0, false, 1, "Tarjeta", month = pastMonth, year = pastYear)
        val personalized = suggestedCategoryBudgets(listOf(income), listOf(income, pastTransport))
        assertTrue(personalized.getValue("Transporte") > suggestions.getValue("Transporte"))
        assertEquals(1000.0, personalized.values.sum())
    }

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
        val movements = reviewImportedRows(parseCsvRows(csv)).movements
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
