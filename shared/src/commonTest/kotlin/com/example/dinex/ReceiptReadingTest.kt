package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiptReadingTest {
    @Test fun boletaElectronicaTomaElImporteTotalYNoElSubtotalNiElVuelto() {
        val text = """
            POLLERIA EL REY S.A.C.
            RUC: 20512345678
            AV. LOS INCAS 123 - AREQUIPA
            BOLETA DE VENTA ELECTRONICA
            B001-00012345
            FECHA EMISION: 08/11/2026 13:45
            1 POLLO 1/4 C/PAPAS 18.00
            1 INCA KOLA 500ML 4.00
            OP. GRAVADA S/ 18.64
            I.G.V. 18% S/ 3.36
            IMPORTE TOTAL S/ 22.00
            EFECTIVO S/ 50.00
            VUELTO S/ 28.00
        """.trimIndent()
        val r = readReceiptText(text)
        assertEquals(ReceiptKind.BOLETA, r.kind)
        assertEquals(22.0, r.total)
        assertEquals(98, r.totalConfidence)
        assertEquals("POLLERIA EL REY S.A.C.", r.merchant)
        assertEquals("20512345678", r.ruc)
        assertEquals(ReceiptDate(8, 11, 2026), r.date)
        assertEquals("Comida", r.category)
        assertEquals("Efectivo", r.method)
        assertTrue(r.items.any { it.contains("POLLO") }, r.items.toString())
    }

    @Test fun facturaConMilesYTotalEnLaLineaSiguiente() {
        val text = """
            TAI LOY S.A.
            R.U.C. 20100049181
            FACTURA ELECTRONICA F005-0004567
            Fecha: 2026-11-03
            SUBTOTAL 1,016.95
            IGV 183.05
            TOTAL A PAGAR
            S/ 1,200.00
            VISA ****4321
        """.trimIndent()
        val r = readReceiptText(text)
        assertEquals(ReceiptKind.FACTURA, r.kind)
        assertEquals(1200.0, r.total)
        assertEquals(ReceiptDate(3, 11, 2026), r.date)
        assertEquals("Estudios", r.category)
        assertEquals("Tarjeta", r.method)
    }

    @Test fun capturaDeYapeSinDecimales() {
        val text = """
            ¡Yapeaste!
            S/ 25
            a Juan Perez
            12 nov. 2026 - 08:15 pm
            Nro. de operación 03914567
        """.trimIndent()
        val r = readReceiptText(text)
        assertEquals(ReceiptKind.YAPE, r.kind)
        assertEquals(25.0, r.total)
        assertEquals("Yape", r.method)
        assertEquals(ReceiptDate(12, 11, 2026), r.date)
        assertEquals("Juan Perez", r.merchant)
    }

    @Test fun reciboDeLuzIgnoraLaFechaDeVencimiento() {
        val text = """
            LUZ DEL SUR
            RECIBO N° 0012345
            SUMINISTRO 1234567
            FECHA DE EMISION 02/11/2026
            FECHA DE VENCIMIENTO 18/11/2026
            CONSUMO DEL MES 98.40
            TOTAL A PAGAR S/ 112.30
        """.trimIndent()
        val r = readReceiptText(text)
        assertEquals(ReceiptKind.SERVICIO, r.kind)
        assertEquals(112.3, r.total)
        assertEquals(ReceiptDate(2, 11, 2026), r.date)
        assertEquals("Otros", r.category)
        assertEquals("Luz Del Sur", r.merchant)
    }

    @Test fun voucherDeTarjetaConComaDecimalYErroresDelOcr() {
        val text = """
            NIUBIZ
            GRIFO PRIMAX AREQUIPA
            VENTA  VISA ****1234
            COD. AUT. 123456
            TOTAL: 5/. 80,50
            APROBADO
        """.trimIndent()
        val r = readReceiptText(text)
        assertEquals(ReceiptKind.VOUCHER, r.kind)
        assertEquals(80.5, r.total)
        assertEquals("Transporte", r.category)
        assertEquals("Tarjeta", r.method)
    }

    @Test fun ticketSinPalabraTotalUsaElMayorMontoSinContarElEfectivo() {
        val text = """
            BODEGA DOÑA ROSA
            ARROZ 5.50
            ACEITE 11.90
            AZUCAR 4.20
            21.60
            EFECTIVO 30.00
        """.trimIndent()
        val r = readReceiptText(text)
        assertEquals(21.6, r.total)
        assertEquals("Comida", r.category)
    }

    @Test fun cineEsEntretenimiento() {
        val text = """
            CINEPLANET MALL AVENTURA
            RUC 20429683581
            BOLETA B210-0098765
            FUNCION 19:30 SALA 5 BUTACA F12
            ENTRADA GENERAL 2 X 14.00
            TOTAL S/ 28.00
        """.trimIndent()
        val r = readReceiptText(text)
        assertEquals(28.0, r.total)
        assertEquals("Entretenimiento", r.category)
    }

    @Test fun montosConFormatosPeruanos() {
        assertEquals(1234.5, parseSolesAmount("1,234.50"))
        assertEquals(1234.5, parseSolesAmount("1.234,50"))
        assertEquals(12.5, parseSolesAmount("12,50"))
        assertEquals(12.5, parseSolesAmount("12.5"))
    }
}
