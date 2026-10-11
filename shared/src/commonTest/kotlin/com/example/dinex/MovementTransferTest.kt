package com.example.dinex

import kotlin.test.*

class MovementTransferTest {
    private fun movement(id: Int = 1) = Movement(id, "Café & pan; \"especial\"", "Comida", 12.5, false, 29, "Yape", "Lima", 9, 2026)

    @Test fun csvRoundTripPreservesQuotesAccentsDatesAndTypes() {
        val original = listOf(movement(), movement(2).copy(title = "Pago\nfreelance", income = true, amount = 1200.75, category = "Ingreso", month = 8))
        val review = reviewImportFile(createMovementReport(original, ReportFormat.CSV, "Todo", "QA").let { TransferFile(it.name, it.bytes) })
        assertEquals(original, review.movements)
        assertEquals(0, review.rejectedRows)
    }

    @Test fun spreadsheetRoundTripProducesRealWorkbook() {
        val original = listOf(movement(), movement(2).copy(income = true, category = "Ingreso", title = "Sueldo"))
        val report = createMovementReport(original, ReportFormat.XLSX, "Septiembre", "QA")
        assertEquals("PK", report.bytes.take(2).toByteArray().decodeToString())
        assertTrue("xl/worksheets/sheet2.xml" in readZip(report.bytes))
        assertEquals(original, reviewImportFile(TransferFile(report.name, report.bytes)).movements)
    }

    @Test fun yapeExportWithPreambleAndLocalizedAmountsIsDetected() {
        val csv = "Reporte de movimientos de Yape\nÚltimos 90 días\nFecha;Nombre;Monto;Tipo de operación\n29/09/2026 10:05;Ana;S/ 1.234,50;Recibiste\n01/08/2026;Taxi;25,00;Pagaste\n31/02/2026;Error;10;Pago"
        val result = reviewImportedRows(parseCsvRows(csv))
        assertEquals(2, result.movements.size)
        assertTrue(result.movements[0].income)
        assertFalse(result.movements[1].income)
        assertEquals(1234.5, result.movements[0].amount)
        assertEquals("2026-08-01", result.movements[1].dateIso())
        assertEquals(1, result.rejectedRows)
    }

    @Test fun separateDebitCreditBankColumnsWork() {
        val csv = "Fecha,Descripción,Cargo,Abono\n2026-09-15,Compra,15.90,0\n2026-09-16,Sueldo,0,1200.00"
        val items = reviewImportedRows(parseCsvRows(csv)).movements
        assertEquals(listOf(false, true), items.map { it.income })
        assertEquals(listOf(15.9, 1200.0), items.map { it.amount })
    }

    @Test fun yapeTePagoIsIncomeAndPagasteIsExpenseWithOrWithoutTypeColumn() {
        val rows = parseCsvRows("Fecha;Nombre;Monto;Tipo de operación\n11/10/2026;Ana;50;Te pagó\n11/10/2026;Taxi;15;Pagaste")
        assertEquals(listOf(true, false), reviewImportedRows(rows).movements.map { it.income })
        val descriptions = parseCsvRows("Fecha;Descripción;Monto\n11/10/2026;Luis te pagó;100\n11/10/2026;Pagaste a María;20")
        assertEquals(listOf(true, false), reviewImportedRows(descriptions).movements.map { it.income })
    }

    @Test fun weeklyReportIncludesOnlyLastSevenDatesAcrossMonthBoundaries() {
        val today = civilDateFromEpochDays()
        val older = civilDateFromEpochDays(currentDayIndex() - 7)
        val boundary = civilDateFromEpochDays(currentDayIndex() - 6)
        fun at(id: Int, date: DinexDate) = movement(id).copy(day = date.day, month = date.month, year = date.year)
        val items = listOf(at(1, today), at(2, older), at(3, boundary))
        assertEquals(listOf(3, 1), reportMovements(items, ReportPeriod.WEEK).map { it.id })
    }

    @Test fun moneyFormatsRejectInvalidOrInfiniteAmounts() {
        assertEquals(1234.56, parseImportAmount("S/ 1,234.56"))
        assertEquals(1234.56, parseImportAmount("1.234,56"))
        assertEquals(-10.5, parseImportAmount("(10,50)"))
        assertEquals(1000.0, parseImportAmount("1,000"))
        assertNull(parseImportAmount("NaN"))
        assertNull(parseImportAmount("Infinity"))
        assertNull(parseImportAmount("S/ texto"))
    }

    @Test fun datesValidateLeapYearsAndExcelSerials() {
        assertEquals(DinexDate(2024, 2, 29), parseImportDate("29/02/2024"))
        assertNull(parseImportDate("29/02/2025"))
        assertEquals(DinexDate(2026, 9, 15), parseImportDate("15 de setiembre de 2026"))
        assertEquals(DinexDate(1970, 1, 1), parseImportDate("25569"))
    }

    @Test fun customPeriodDoesNotMergeMonthsSharingSameDay() {
        val items = listOf(movement(), movement(2).copy(month = 8), movement(3).copy(year = 2025))
        assertEquals(listOf(items[0]), reportMovements(items, ReportPeriod.CUSTOM, "2026-09-01", "2026-09-30"))
        assertEquals(listOf(items[2], items[1], items[0]), reportMovements(items, ReportPeriod.ALL))
        assertNotEquals(items[0].importIdentity(), items[1].importIdentity())
    }

    @Test fun fullDatesSurviveStorageSerialization() {
        val original = listOf(movement(), movement(2).copy(month = 12, year = 2024, title = "Café|50%\ncon pan %7C"))
        assertEquals(original, decodeMovements(encodeMovements(original)))
    }

    @Test fun duplicatesCanBeIdentifiedWithoutChangingHistory() {
        assertEquals(movement().importIdentity(), movement(99).copy(title = "  café & pan; \"especial\"  ").importIdentity())
        assertNotEquals(movement().importIdentity(), movement().copy(amount = 13.0).importIdentity())
    }

    @Test fun maliciousSpreadsheetFormulaIsExportedAsText() {
        val original = listOf(movement().copy(title = "=SUM(A1:A2)"))
        val csv = exportMovementsCsv(original)
        assertTrue("'=SUM" in csv)
        assertEquals(original, reviewImportedRows(parseCsvRows(csv)).movements)
        assertFalse("<f>" in readZip(exportWorkbook(original, "Todo", "QA")).getValue("xl/worksheets/sheet1.xml").decodeToString())
    }

    @Test fun damagedFilesAndUnclosedCsvAreRejectedClearly() {
        assertFailsWith<IllegalArgumentException> { parseCsvRows("Fecha;Monto\n\"sin cerrar;10") }
        assertFails { reviewImportFile(TransferFile("roto.xlsx", ByteArray(100))) }
        assertFails { reviewImportFile(TransferFile("antiguo.xls", ByteArray(100))) }
        assertFailsWith<IllegalArgumentException> { reviewImportFile(TransferFile("grande.csv", ByteArray(MAX_TRANSFER_BYTES + 1))) }
    }

    @Test fun pdfIsMultiPageAndHasValidCrossReferenceOffsets() {
        val pdf = exportPdfReport((1..70).map { movement(it) }, "Todo", "QA").decodeToString()
        assertTrue(pdf.startsWith("%PDF-1.4"))
        val pageCount = Regex("/Count (\\d+)").find(pdf)!!.groupValues[1].toInt()
        assertTrue(pageCount >= 2)
        assertTrue("2026-09-29" in pdf)
        assertTrue(pdf.trimEnd().endsWith("%%EOF"))
    }
}
