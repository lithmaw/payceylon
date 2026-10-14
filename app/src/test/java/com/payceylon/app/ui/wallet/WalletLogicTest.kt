package com.payceylon.app.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class WalletLogicTest {
    @Test
    fun parsesPayCeylonQrPayloads() {
        assertEquals("sajjad67", parsePayCeylonHandle("payceylon://pay?to=sajjad67"))
        assertEquals("sajjad67", parsePayCeylonHandle("payceylon://pay?amount=5&to=%40Sajjad67"))
        assertEquals("janidup", parsePayCeylonHandle("@janidup"))
        assertEquals("ruveen.object", parsePayCeylonHandle("  ruveen.object "))
    }

    @Test
    fun rejectsForeignQrPayloads() {
        assertNull(parsePayCeylonHandle("https://example.com/pay"))
        assertNull(parsePayCeylonHandle("payceylon://pay"))
        assertNull(parsePayCeylonHandle("ab"))
        assertNull(parsePayCeylonHandle("hello world"))
    }

    @Test
    fun keypadLimitsDecimalsAndLeadingZeros() {
        var input = ""
        "0012.345".forEach { input = appendAmountKey(input, it) }
        assertEquals("12.34", input)
        assertEquals("0.", appendAmountKey("", '.'))
        assertEquals("1.5", appendAmountKey("1.5", '.'))
        assertEquals("123456789", appendAmountKey("123456789", '0'))
    }

    @Test
    fun formatsMoneyParts() {
        assertEquals("32,483" to ".75", BigDecimal("32483.75").toMoneyParts())
        assertEquals("0" to ".00", BigDecimal.ZERO.toMoneyParts())
    }

    @Test
    fun formatsTokenAmountsAtTheirPrecision() {
        assertEquals("0" to ".0125", BigDecimal("0.0125").toAmountParts("BTC"))
        assertEquals("0" to ".15", BigDecimal("0.15").toAmountParts("ETH"))
        assertEquals("73" to ".54", BigDecimal("73.54").toAmountParts("USDT"))
        assertEquals("10,000" to ".00", BigDecimal("10000").toAmountParts("LKR"))
    }

    @Test
    fun tokensMapToAssets() {
        assertEquals(Asset.USDT, Asset.of(CryptoToken.USDT))
        assertEquals("TQn9Y2…KcbLSE", CryptoToken.USDT.shortAddress())
    }

    @Test
    fun keypadAllowsTheAssetsPrecision() {
        var input = ""
        "0.0001234".forEach { input = appendAmountKey(input, it, Asset.BTC.decimals) }
        assertEquals("0.000123", input)
        assertEquals("0.00", "0.000123".limitDecimals(Asset.LKR.decimals))
        assertEquals("12", "12".limitDecimals(2))
    }

    @Test
    fun swapQuoteMatchesTheServer() {
        // 100 USDT - 0.5% fee = 99.50 USDT; at 300 / 30,000,000 LKR that is 0.000995 BTC.
        assertEquals(0, BigDecimal("0.000995").compareTo(SwapRates.quote(Asset.USDT, Asset.BTC, BigDecimal("100"))))
        // A pre-rounded 1/300 rate would give 9.94; the server gives 9.95.
        assertEquals(BigDecimal("9.95"), SwapRates.quote(Asset.LKR, Asset.USDT, BigDecimal("3000")))
    }

    @Test
    fun lkrSwapsShowInBothWallets() {
        val swap = tx(TransactionDirection.Swap, "LKR", "3000").copy(toCurrency = "USDT", toAmount = BigDecimal("9.95"))
        assertTrue(swap.belongsTo(WalletType.Fiat))
        assertTrue(swap.belongsTo(WalletType.Crypto))
        assertEquals("Swap LKR to USDT", swap.title)
        val cryptoOnly = tx(TransactionDirection.Swap, "USDT", "1").copy(toCurrency = "BTC", wallet = WalletType.Crypto)
        assertFalse(cryptoOnly.belongsTo(WalletType.Fiat))
    }

    @Test
    fun netChangeCountsFeesOnDebits() {
        val withdrawal = tx(TransactionDirection.Withdrawal, "LKR", "5000").copy(fee = BigDecimal("25.00"), feeCurrency = "LKR")
        assertEquals(0, BigDecimal("-5025").compareTo(withdrawal.netChange(Asset.LKR)))
        val topUp = tx(TransactionDirection.TopUp, "LKR", "1000").copy(fee = BigDecimal("15.00"))
        // The card fee is charged to the card, not the wallet.
        assertEquals(0, BigDecimal("1000").compareTo(topUp.netChange(Asset.LKR)))
        assertTrue(withdrawal.direction.isDebit)
    }

    @Test
    fun incomingTransfersCarryNoFeeForTheReceiver() {
        assertNull(tx(TransactionDirection.Incoming, "BTC", "0.01").networkFee())
        val sent = tx(TransactionDirection.Outgoing, "USDT", "10").copy(fee = BigDecimal("1.00"), feeCurrency = "USDT")
        assertEquals(Asset.USDT to BigDecimal("1.00"), sent.networkFee())
    }

    private fun tx(direction: TransactionDirection, currency: String, amount: String) = WalletTransaction(
        id = "tx",
        counterpartyName = "Nimal Silva",
        counterpartyHandle = "nimal",
        direction = direction,
        currency = currency,
        amount = BigDecimal(amount),
        wallet = if (currency == "LKR") WalletType.Fiat else WalletType.Crypto,
        timestamp = 0,
    )
}
