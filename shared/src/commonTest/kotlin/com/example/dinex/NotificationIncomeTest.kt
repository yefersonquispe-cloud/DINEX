package com.example.dinex

import kotlin.test.*

class NotificationIncomeTest {
    @Test fun yapeIncomingVariantsAreIncomeNotExpenses() {
        listOf(
            "¡Te yapearon! · María te envió S/ 50.00",
            "Yape · Juan Pérez te yapeó S/ 50",
            "TE YAPEARON · Has recibido un yape de 50 soles",
            "Ana te ha yapeado S/. 50,00",
            "Carlos te envió un pago de PEN 50.00",
            "Recibiste S/ 50.00 de Luis",
            "Nuevo pago recibido · S/ 50.00",
            "Te han enviado S/ 50.00"
        ).forEach { text ->
            val item = assertNotNull(parseDinexTransactionNotification("Yape", text), text)
            assertTrue(item.income, text)
            assertEquals(50.0, item.amount, text)
        }
    }

    @Test fun bankCreditsAndDepositsAreIncome() {
        listOf(
            "Abono en tu cuenta por S/ 1200.00",
            "Depósito a tu cuenta · S/ 1,200.00",
            "Transferencia recibida · PEN 1200",
            "Dinero recibido: 1.200,00 soles",
            "Depósito S/ 1200 realizado en tu cuenta"
        ).forEach { text ->
            val item = assertNotNull(parseDinexTransactionNotification("BCP", text), text)
            assertTrue(item.income, text)
            assertEquals(1200.0, item.amount, text)
        }
    }

    @Test fun outgoingYapesAndDepositsStayExpenses() {
        listOf("Yapeaste S/ 20 a Juan", "Enviaste S/ 20", "Depositaste S/ 20", "Depósito realizado S/ 20", "Pagaste S/ 20", "Compra aprobada S/ 20")
            .forEach { text -> assertFalse(assertNotNull(parseDinexTransactionNotification("Yape", text), text).income, text) }
    }

    @Test fun balanceIsNotMistakenForAmountReceived() {
        val item = assertNotNull(parseDinexTransactionNotification("BCP", "Saldo S/ 2000.00 · Recibiste S/ 50.00 de Ana"))
        assertTrue(item.income)
        assertEquals(50.0, item.amount)
    }

    @Test fun pendingPromotionsAndRequestsAreNotTransactions() {
        listOf("Solicitud de pago S/ 30", "Ana solicita un yape de S/ 30", "Pago cancelado S/ 30", "Pago pendiente S/ 30", "Compra y gana por S/ 30", "Te yapearon", "Ana te envía saludos")
            .forEach { assertNull(parseDinexTransactionNotification("Yape", it), it) }
    }
}
