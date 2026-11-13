package com.example.dinex

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.*

class WorkbookInteropTest {
    @Test fun readsCompressedXlsxWithSharedStringsAndExcelDates() {
        val files = mapOf(
            "xl/workbook.xml" to "<workbook/>",
            "xl/sharedStrings.xml" to "<sst><si><t>Fecha</t></si><si><t>Descripción</t></si><si><t>Monto</t></si><si><t>Tipo</t></si><si><t>Café &amp; pan</t></si><si><t>Pagaste</t></si></sst>",
            "xl/styles.xml" to "<styleSheet><cellXfs><xf numFmtId=\"0\"/><xf numFmtId=\"14\"/></cellXfs></styleSheet>",
            "xl/worksheets/sheet1.xml" to "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"s\"><v>2</v></c><c r=\"D1\" t=\"s\"><v>3</v></c></row><row r=\"2\"><c r=\"A2\" s=\"1\"><v>25569</v></c><c r=\"B2\" t=\"s\"><v>4</v></c><c r=\"C2\"><v>12.50</v></c><c r=\"D2\" t=\"s\"><v>5</v></c></row></sheetData></worksheet>"
        )
        for (level in listOf(0, 1, 6, 9)) {
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                zip.setLevel(level)
                files.forEach { (name, xml) -> zip.putNextEntry(ZipEntry(name)); zip.write(xml.toByteArray()); zip.closeEntry() }
            }
            val item = reviewImportFile(TransferFile("banco.xlsx", output.toByteArray())).movements.single()
            assertEquals("1970-01-01", item.dateIso())
            assertEquals("Café & pan", item.title)
            assertEquals(12.5, item.amount)
            assertFalse(item.income)
        }
    }

    @Test fun zipInflaterHandlesLargeDynamicHuffmanStreams() {
        val source = (0..5000).joinToString("\n") { "<row><c>Compra número $it de pan y café en Lima, importe ${it % 99}</c></row>" }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> zip.putNextEntry(ZipEntry("datos.xml")); zip.write(source.toByteArray()); zip.closeEntry() }
        assertEquals(source, readZip(output.toByteArray()).getValue("datos.xml").decodeToString())
    }

    @Test fun reportsAreReadableByStandardZipAndGenerateQaArtifacts() {
        val titles = listOf("Menú del día en el mercado", "Taxi al trabajo", "Pasaje (bus)", "Libro de matemáticas", "Cine con amigos",
            "Recarga de celular", "Café & pan", "Útiles para la universidad con descripción bastante larga para probar el ajuste de línea", "Netflix", "Polladas")
        val methods = listOf("Yape", "Plin", "Efectivo", "BCP", "Interbank")
        val cats = listOf("Comida", "Transporte", "Transporte", "Estudios", "Entretenimiento", "Otros", "Comida", "Estudios", "Entretenimiento", "Comida")
        val movements = (1..60).map { i ->
            val month = 6 + (i % 5)
            if (i % 7 == 0) Movement(i, "Sueldo de ${listOf("junio", "julio", "agosto", "setiembre", "octubre")[month - 6]}", "Ingreso", 1500.0 + i * 3, true, 1 + i % 27, "BCP", "", month, 2026)
            else Movement(i, titles[i % titles.size], cats[i % cats.size], 8.5 + (i * 37 % 140), false, 1 + i % 27, methods[i % methods.size], if (i % 3 == 0) "Lima" else "", month, 2026)
        }
        val directory = File("build/qa-transfer").apply { mkdirs() }
        ReportFormat.entries.forEach { format ->
            val report = createMovementReport(movements, format, "Todo el historial", "Persona de prueba")
            File(directory, "reporte.${format.extension}").writeBytes(report.bytes)
            if (format == ReportFormat.XLSX) {
                var entries = 0
                ZipInputStream(ByteArrayInputStream(report.bytes)).use { zip ->
                    while (zip.nextEntry != null) { assertTrue(zip.readBytes().isNotEmpty()); entries++ }
                }
                assertTrue(entries >= 13)
            }
        }
    }
}
