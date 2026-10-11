package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqliteIOSTest {
    @Test fun sqlitePersistsUnicodeQuotesAndCanDeleteOnlyItsOwnTestRow() {
        val key = "qa_sqlite_unicode"
        val value = "Perú: 'ahorros' | 25.50\nDinex 🎉"
        try {
            writeLocalValue(key, value)
            assertEquals(value, readLocalValue(key))
            writeLocalValue(key, "Actualizado")
            assertEquals("Actualizado", readLocalValue(key))
            writeLocalValue(key, null)
            assertNull(readLocalValue(key))
        } finally {
            writeLocalValue(key, null)
        }
    }
}
