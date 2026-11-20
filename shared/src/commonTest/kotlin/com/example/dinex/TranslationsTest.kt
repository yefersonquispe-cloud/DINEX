package com.example.dinex

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Las traducciones nuevas deben conservar los valores {0}, {1}… y los botones deben cambiar de idioma. */
class TranslationsTest {
    @AfterTest
    fun backToSpanish() { DinexI18n.language = DinexLanguage.ES }

    private fun placeholders(text: String) = Regex("\\{\\d}").findAll(text).map { it.value }.toSet()

    @Test
    fun everyTranslationKeepsItsPlaceholders() {
        DinexAppTranslations.forEach { (es, pair) ->
            val (en, qu) = pair
            assertTrue(en.isNotBlank() && qu.isNotBlank(), "Traducción vacía para \"$es\"")
            assertEquals(placeholders(es), placeholders(en), "Inglés de \"$es\"")
            assertEquals(placeholders(es), placeholders(qu), "Quechua de \"$es\"")
        }
    }

    @Test
    fun buttonsChangeLanguage() {
        DinexI18n.language = DinexLanguage.EN
        assertEquals("Scan", t("Escanear"))
        assertEquals("Import", t("Importar"))
        assertEquals("Export", t("Exportar"))
        assertEquals("Voice", t("Voz"))
        assertEquals("Cash", t("Efectivo"))
        assertEquals("Food", t("Comida"))
        assertEquals("Expense saved", t("Gasto guardado"))
        assertEquals("You went over by S/ 5.00", t("Te pasaste por {0}", "S/ 5.00"))
        assertEquals("3 days", countLabel(3, "día", "días"))
        DinexI18n.language = DinexLanguage.QU
        assertEquals("Kunka", t("Voz"))
        assertEquals("1 p'unchaw", countLabel(1, "día", "días"))
        // Lo que no está en el diccionario se muestra en español, nunca vacío.
        assertEquals("Texto sin traducir", t("Texto sin traducir"))
    }
}
