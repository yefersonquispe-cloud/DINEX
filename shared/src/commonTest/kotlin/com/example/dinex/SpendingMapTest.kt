package com.example.dinex

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class SpendingStore : DinexStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

class SpendingMapTest {
    @AfterTest
    fun clearLocationProvider() {
        DinexLocationProvider.current = null
    }

    private fun model(): DinexViewModel {
        DinexLocationProvider.current = { DinexLocation(-12.0464, -77.0428, "Lima") }
        return DinexViewModel(DinexRepository(SpendingStore(), migrateLegacy = false))
    }

    @Test
    fun newExpenseKeepsTheCurrentLocationButIncomeDoesNot() {
        val model = model()
        model.createAccount(UserAccount("Persona", "mapa@dinex.test", "digest"))
        model.setInitialBalance(100.0)

        model.saveMovement(Movement(model.nextMovementId(), "Café", "Comida", 12.0, false, currentDayOfMonth(), "Yape"))
        val expense = model.state.value.data.movements.first { it.title == "Café" }
        assertEquals(-12.0464, expense.latitude)
        assertEquals(-77.0428, expense.longitude)
        assertEquals("Lima", expense.location)

        model.saveMovement(Movement(model.nextMovementId(), "Sueldo", "Ingreso", 50.0, true, currentDayOfMonth(), "Transferencia"))
        val income = model.state.value.data.movements.first { it.title == "Sueldo" }
        assertNull(income.latitude)
        assertNull(income.longitude)
    }

    @Test
    fun movementSerializationKeepsCoordinatesAndReadsOldRows() {
        val located = Movement(1, "Café", "Comida", 12.0, false, 1, "Yape", "Lima", 10, 2026, -12.0464, -77.0428)
        assertEquals(listOf(located), decodeMovements(encodeMovements(listOf(located))))

        val old = "m3|2|Antiguo|Comida|8.0|false|2|Efectivo|Lima|10|2026"
        val decoded = decodeMovements(old).single()
        assertEquals("Antiguo", decoded.title)
        assertNull(decoded.latitude)
        assertNull(decoded.longitude)
    }

    @Test
    fun placesGroupNearbyVisitsAndAddTheirSpending() {
        val first = Movement(1, "Café", "Comida", 10.0, false, 1, "Yape", "Lima", 10, 2026, -12.04641, -77.04281)
        val second = Movement(2, "Almuerzo", "Comida", 15.0, false, 2, "Yape", "Lima", 10, 2026, -12.04642, -77.04280)
        val other = Movement(3, "Taxi", "Transporte", 5.0, false, 3, "Efectivo", "Miraflores", 10, 2026, -12.1200, -77.0300)

        val places = spendingPlaces(listOf(first, second, other))

        assertEquals(2, places.size)
        assertEquals(25.0, places.first().total)
        assertEquals(2, places.first().visits)
        assertEquals("Lima", places.first().label)
    }

    @Test
    fun namedPlacesStillShowTextOnlyLocations() {
        val movements = listOf(
            Movement(1, "Menú", "Comida", 20.0, false, 1, "Yape", "Tecsup, Lima", 10, 2026),
            Movement(2, "Café", "Comida", 8.0, false, 2, "Yape", "Tecsup, Lima", 10, 2026),
            Movement(3, "Sueldo", "Ingreso", 100.0, true, 3, "Transferencia", "Oficina", 10, 2026),
        )
        val named = namedSpendingPlaces(movements)
        assertEquals(1, named.size)
        assertEquals("Tecsup, Lima", named.first().first)
        assertEquals(28.0, named.first().second)
        assertTrue(spendingPlaces(movements).isEmpty())
    }
}
