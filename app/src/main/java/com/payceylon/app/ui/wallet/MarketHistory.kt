package com.payceylon.app.ui.wallet

import java.math.BigDecimal
import java.math.MathContext
import kotlin.random.Random

/** A token's last 24 hours of (simulated) LKR prices, oldest first, ending at today's price. */
data class PriceHistory(val prices: List<Double>) {
    val first: Double get() = prices.first()
    val last: Double get() = prices.last()

    /** Change over the period, in percent. */
    val changePercent: Double get() = (last - first) / first * 100
}

/**
 * Demo price charts for the crypto wallet's token cards. Prices are fixed in [SwapRates], so
 * the history is made up: a 24 h move of a few percent, with a random walk along the way that
 * starts and ends exactly on it (a Brownian bridge). Seeded by token and hour, so it stays the
 * same while the user looks at it and moves on each hour.
 */
object MarketHistory {
    /** Points in the 24 hours (one every 30 minutes). */
    const val POINTS = 48

    /** Largest 24 h move, as a fraction of the price; the wiggle along the way scales with it. */
    private fun range(token: CryptoToken): Double = when (token) {
        CryptoToken.USDT -> 0.002
        CryptoToken.BTC -> 0.05
        CryptoToken.ETH -> 0.07
        CryptoToken.SOL -> 0.1
    }

    fun of(token: CryptoToken, nowMillis: Long = System.currentTimeMillis()): PriceHistory {
        val hour = nowMillis / 3_600_000
        val random = Random(token.ordinal * 1_000_003L + hour)
        val price = SwapRates.lkrPrice(Asset.of(token)).toDouble()
        val range = range(token)
        // At least a third of the range either way, so the change is never a flat 0.0x%.
        val move = (0.35 + 0.65 * random.nextDouble()) * range * (if (random.nextBoolean()) 1 else -1)
        val start = price / (1 + move)
        val walk = DoubleArray(POINTS)
        for (i in 1 until POINTS) walk[i] = walk[i - 1] + (random.nextDouble() * 2 - 1)
        val last = POINTS - 1
        val wiggle = range * 0.12 * price
        val prices = List(POINTS) { i ->
            val t = i.toDouble() / last
            // The bridge term is zero at both ends, so the line starts at [start] and ends at [price].
            start + (price - start) * t + (walk[i] - t * walk[last]) * wiggle
        }
        return PriceHistory(prices)
    }

    /** What [amount] of [token] is worth in LKR at today's price. */
    fun valueInLkr(token: CryptoToken, amount: BigDecimal): BigDecimal =
        amount.multiply(SwapRates.lkrPrice(Asset.of(token)), MathContext.DECIMAL64)
}