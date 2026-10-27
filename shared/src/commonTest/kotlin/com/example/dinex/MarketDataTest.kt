package com.example.dinex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketDataTest {
    private val rates = """
        {"rates":{
          "usd":{"name":"US Dollar","unit":"$","value":80000.0,"type":"fiat"},
          "pen":{"name":"Peruvian Sol","unit":"S/","value":276000.0,"type":"fiat"},
          "eur":{"name":"Euro","unit":"€","value":72000,"type":"fiat"},
          "xau":{"name":"Gold","unit":"XAU","value":20,"type":"commodity"},
          "xag":{"name":"Silver","unit":"XAG","value":1500,"type":"commodity"}
        }}
    """.trimIndent()
    private val coins = """
        [{"id":"bitcoin","symbol":"btc","name":"Bitcoin","current_price":80000,
          "price_change_percentage_24h":-1.5,"sparkline_in_7d":{"price":[79000,80500.5,80000]}}]
    """.trimIndent()

    @Test
    fun ratesBecomePricesInSoles() {
        val market = parseMarketSnapshot(rates, coins)
        val dollar = market.currencies.first { it.id == "usd" }
        assertEquals(3.45, dollar.pricePen, 1e-9)
        assertEquals(1.0, dollar.priceUsd, 1e-9)
        val gold = market.metals.first { it.id == "xau" }
        assertEquals(4000.0, gold.priceUsd, 1e-9)
        assertEquals(13_800.0, gold.pricePen, 1e-9)
    }

    @Test
    fun cryptoKeepsChangeAndTrend() {
        val bitcoin = parseMarketSnapshot(rates, coins).crypto.single()
        assertEquals("BTC", bitcoin.symbol)
        assertEquals(276_000.0, bitcoin.pricePen, 1e-6)
        assertEquals(-1.5, bitcoin.change24h)
        assertEquals(listOf(79000.0, 80500.5, 80000.0), bitcoin.trend)
    }

    @Test
    fun jsonReaderHandlesEscapesAndNesting() {
        val value = parseJson("""{"a":[1,-2.5e1,true,null],"b":"S\/ ñ\n"}""") as Map<*, *>
        assertEquals(listOf(1.0, -25.0, true, null), value["a"])
        assertEquals("S/ ñ\n", value["b"])
    }

    @Test
    fun amountsUseThousandsSeparators() {
        assertEquals("276,380.50", formatAmount(276380.499, 2))
        assertEquals("3.448", formatAmount(3.4478, 3))
        assertEquals("0.2683", formatAmount(0.26834, 4))
        assertEquals("-1,000", formatAmount(-1000.0, 0))
        assertTrue(formatAmount(0.0, 2) == "0.00")
    }
}
