package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Montos con céntimos y categorías por palabras clave en Oye Dinex. */
class VoiceAmountCategoryTest {
    private fun draft(text: String) = assertNotNull(parseVoiceDraft(text, 1), text).movement

    @Test
    fun understandsCentsSaidInManyWays() {
        mapOf(
            "registra un gasto de 21.50 soles en comida" to 21.5,
            "registra un gasto de 21,50 soles en comida" to 21.5,
            "gasté 21 con 50 en comida" to 21.5,
            "gasté 21 soles con 50 céntimos en comida" to 21.5,
            "gasté veintiún soles con cincuenta céntimos en comida" to 21.5,
            "gasté veintiuno cincuenta en comida" to 21.5,
            "registra 21 50 soles en comida" to 21.5,
            "gasté 21 punto 50 soles en comida" to 21.5,
            "gasté 21 coma 5 soles en comida" to 21.5,
            "gasté 21 soles 50 en comida" to 21.5,
            "pagué 3 soles 20 céntimos de pasaje" to 3.2,
            "gasté dos cincuenta en pan" to 2.5,
            "gasté 18 y medio en almuerzo" to 18.5,
            "registra 18.5 soles en comida" to 18.5,
            "gasté 50 céntimos en un caramelo" to 0.5,
            "gasté ciento veinticinco soles en comida" to 125.0,
            "gasté treinta y cinco soles en taxi" to 35.0,
            "pagué 1,500 soles de alquiler" to 1500.0,
        ).forEach { (text, amount) -> assertEquals(amount, draft(text).amount, text) }
    }

    @Test
    fun keepsTheCurrencyWhenCentsAreSpoken() {
        val movement = parseVoiceDraft("registra un gasto de 21 soles con 50 céntimos en comida", 1)!!
        assertEquals(21.5, movement.movement.amount)
        assertEquals(emptyList(), movement.doubts)
    }

    @Test
    fun saysTheCategoryByName() {
        mapOf(
            "registra un gasto en comida de 20 soles" to "Comida",
            "registra un gasto de 15 soles en comida" to "Comida",
            "registra otros 20 soles en comida" to "Comida",
            "gasto de 10 soles en transporte" to "Transporte",
            "anota 40 soles en estudios" to "Estudios",
            "registra 30 soles en entretenimiento" to "Entretenimiento",
            "registra 12 soles en otros" to "Otros",
            "registra un gasto de 25 soles en comida en el taxi" to "Comida",
        ).forEach { (text, category) -> assertEquals(category, draft(text).category, text) }
    }

    @Test
    fun findsTheCategoryFromKeywords() {
        mapOf(
            // Comida
            "comí 12 soles" to "Comida", "almorcé 15 soles" to "Comida", "gasté 8 soles en el desayuno" to "Comida",
            "registra un gasto en alimento de 10 soles" to "Comida", "pagué 30 soles de pollo a la brasa" to "Comida",
            "gasté 20 soles en Plaza Vea" to "Comida", "pedí delivery por 25 soles" to "Comida", "gasté 35 soles en Metro" to "Comida",
            "compré pan por 4 soles" to "Comida", "gasté 22 soles en Uber Eats" to "Comida", "compré una gaseosa de 3 soles" to "Comida",
            // Transporte
            "pagué 5 soles en pasaje del metro" to "Transporte", "gasté 12 soles en taxi" to "Transporte", "tomé un Uber de 18 soles" to "Transporte",
            "gasté 50 soles en gasolina" to "Transporte", "pagué 2.50 de combi" to "Transporte", "taxi al mercado 10 soles" to "Transporte",
            "recargué 20 soles en la tarjeta del metropolitano" to "Transporte",
            // Estudios
            "compré un cuaderno de 6 soles" to "Estudios", "pagué 450 soles de pensión" to "Estudios", "saqué copias por 2 soles" to "Estudios",
            "pagué 30 soles del curso de Udemy" to "Estudios", "gasté 15 soles en impresiones" to "Estudios",
            // Entretenimiento
            "gasté 25 soles en el cine" to "Entretenimiento", "pagué 45 soles de Netflix" to "Entretenimiento",
            "gasté 60 soles en cervezas" to "Entretenimiento", "compré un juego de 80 soles" to "Entretenimiento",
            "pagué 100 soles por el concierto" to "Entretenimiento",
            // Otros
            "gasté 40 soles en ropa" to "Otros", "compré medicinas en la farmacia por 30 soles" to "Otros",
            "pagué 60 soles de internet" to "Otros", "recargué 10 soles al celular" to "Otros", "gasté 30 soles en el gimnasio" to "Otros",
            "gasté 9 soles en algo" to "Otros",
        ).forEach { (text, category) -> assertEquals(category, draft(text).category, text) }
    }

    @Test
    fun categoryQuestionsStillWork() {
        assertEquals(VoiceQuery.Spent(VoicePeriod.THIS_WEEK, "Comida"), parseVoiceQuery(normalizeVoice("¿cuánto gasté en comida esta semana?")))
        assertEquals(VoiceQuery.Spent(VoicePeriod.THIS_MONTH, null), parseVoiceQuery(normalizeVoice("¿cuánto gasté este mes?")))
        assertEquals(VoiceQuery.Balance, parseVoiceQuery(normalizeVoice("¿cuánto dinero tengo disponible?")))
    }
}
