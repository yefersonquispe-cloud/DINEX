package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportAdviceTest {
    private fun item(id: Int, title: String, category: String, amount: Double, income: Boolean = false, day: Int = 5) =
        Movement(id, title, category, amount, income, day, "Yape", month = 11, year = 2026)

    @Test
    fun lowSavingsGetsPersonalPlanWithRealNumbers() {
        val items = listOf(item(1, "Sueldo", "Ingreso", 1000.0, income = true)) +
            (2..9).map { item(it, "Snack", "Comida", 8.0, day = it) } +
            listOf(item(10, "Menú", "Comida", 600.0), item(11, "Taxi", "Transporte", 236.0))
        val advice = reportAdvice(ReportStats(items), "Yeferson Quispe", ReportContext(goals = listOf(SavingsGoal(1, "Celular", 1333.0, 134.0))))
        // Gastó 900 de 1000: ahorró 10 %.
        assertEquals("Yeferson, ahorraste el 10% de tus ingresos", advice.headline)
        assertEquals("Vas bien", advice.level)
        assertTrue(advice.items.first().title.startsWith("Págate primero"))
        assertTrue(advice.items.any { "Comida" in it.title }, advice.items.toString())
        assertTrue(advice.items.any { "hormiga" in it.title }, advice.items.toString())
        assertTrue(advice.items.any { "Celular" in it.title }, advice.items.toString())
        assertTrue(advice.items.size <= 5)
        assertTrue(advice.potentialMonthly > 0)
        assertTrue("S/ 500.00" in advice.lesson && "S/ 200.00" in advice.lesson, advice.lesson)
    }

    @Test
    fun overspendingIsFlaggedWithoutInventingIncome() {
        val advice = reportAdvice(ReportStats(listOf(item(1, "Pago", "Ingreso", 100.0, income = true), item(2, "Laptop", "Otros", 300.0))), "Ana")
        assertEquals("Ana, gastaste S/ 200.00 más de lo que ingresaste", advice.headline)
        assertEquals("Atención", advice.level)
        val empty = reportAdvice(ReportStats(emptyList()), "")
        assertTrue(empty.items.none { it.impact != null && it.impact!! > 0 })
    }

    @Test
    fun pdfAndWorkbookIncludeThePlan() {
        val items = listOf(item(1, "Sueldo", "Ingreso", 1000.0, income = true), item(2, "Menú", "Comida", 300.0))
        val pdf = exportPdfReport(items, "Noviembre", "Ana").map { (it.toInt() and 255).toChar() }.joinToString("")
        assertTrue("Tu plan para ahorrar más" in pdf)
        assertTrue("Tu dinero, con propósito" in pdf)
        assertTrue("Decide mejor" !in pdf)
        val files = readZip(exportWorkbook(items, "Noviembre", "Ana")).mapValues { it.value.decodeToString() }
        assertTrue("Mi plan de ahorro" in files.getValue("xl/workbook.xml"))
        assertTrue("Protege lo que ya ahorraste" in files.getValue("xl/worksheets/sheet3.xml"))
    }
}
