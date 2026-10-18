package com.payceylon.app.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MarketHistoryTest {
    private val now = 1_790_000_000_000L

    @Test
    fun historyEndsAtTodaysPriceAndStaysNearIt() {
        CryptoToken.entries.forEach { token ->
            val history = MarketHistory.of(token, now)
            val price = SwapRates.lkrPrice(Asset.of(token)).toDouble()
            assertEquals(MarketHistory.POINTS, history.prices.size)
            assertEquals(price, history.last, 0.0)
            assertTrue(token.name, history.prices.all { it in price * 0.7..price * 1.3 })
            assertTrue(token.name, kotlin.math.abs(history.changePercent) >= 0.05)
        }
    }

    @Test
    fun historyIsStableWithinTheHourAndMovesOnAfter() {
        val history = MarketHistory.of(CryptoToken.BTC, now)
        assertEquals(history, MarketHistory.of(CryptoToken.BTC, now + 60_000))
        assertNotEquals(history, MarketHistory.of(CryptoToken.BTC, now + 3_600_000))
    }

    @Test
    fun holdingValueUsesTheSwapPrice() {
        assertEquals(0, BigDecimal("1264500").compareTo(MarketHistory.valueInLkr(CryptoToken.BTC, BigDecimal("0.04215"))))
    }
}