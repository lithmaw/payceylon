package com.payceylon.app.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun sendDebitsWalletAndRecordsTransaction() {
        val wallet = WalletViewModel()
        val before = wallet.balanceOf(Asset.LKR)
        assertTrue(wallet.send("sajjad67", BigDecimal("1000"), Asset.LKR))
        assertEquals(before - BigDecimal("1000"), wallet.balanceOf(Asset.LKR))
        assertEquals("Transfer to Sajjad", wallet.transactions.first().title)
        assertFalse(wallet.send("sajjad67", before, Asset.LKR))
        assertFalse(wallet.send("sajjad67", BigDecimal.ZERO, Asset.LKR))
    }

    @Test
    fun searchRanksHandlePrefixBeforeNameMatches() {
        // "ka" prefixes @kavindi.p and appears inside @anuka67 and Weerasekara.
        assertEquals(
            listOf("kavindi.p", "anuka67", "sachini.w"),
            searchPayCeylonUsers("ka").map { it.handle },
        )
    }

    @Test
    fun searchMatchesNamesAndIgnoresAtSign() {
        assertEquals(listOf("kavindi.p"), searchPayCeylonUsers("@kavi").map { it.handle })
        assertEquals("sajjad67", searchPayCeylonUsers("ahamed").single().handle)
        assertTrue(searchPayCeylonUsers("   ").isEmpty())
        assertTrue(searchPayCeylonUsers("zzzz").isEmpty())
    }

    @Test
    fun recentRecipientsAreDistinctAndNewestFirst() {
        val wallet = WalletViewModel()
        wallet.send("kavindi.p", BigDecimal("10"), Asset.LKR)
        val recents = wallet.recentRecipients.map { it.handle }
        assertEquals("kavindi.p", recents.first())
        assertEquals(recents.distinct(), recents)
    }

    @Test
    fun formatsTokenAmountsAtTheirPrecision() {
        assertEquals("0" to ".0125", BigDecimal("0.0125").toAmountParts("BTC"))
        assertEquals("0" to ".15", BigDecimal("0.15").toAmountParts("ETH"))
        assertEquals("73" to ".54", BigDecimal("73.54").toAmountParts("USDT"))
        assertEquals("10,000" to ".00", BigDecimal("10000").toAmountParts("LKR"))
    }

    @Test
    fun walletsHaveSeparateTransactionsAndTokenAddresses() {
        val wallet = WalletViewModel()
        assertTrue(wallet.transactions.any { it.wallet == WalletType.Crypto })
        assertTrue(wallet.transactions.any { it.wallet == WalletType.Fiat })
        assertEquals(Asset.USDT, Asset.of(CryptoToken.USDT))
        assertEquals("TQn9Y2…KcbLSE", CryptoToken.USDT.shortAddress())
    }

    @Test
    fun sendingATokenDebitsThatTokenOnly() {
        val wallet = WalletViewModel()
        val btcBefore = wallet.balanceOf(Asset.BTC)
        val lkrBefore = wallet.balanceOf(Asset.LKR)
        assertTrue(wallet.send("pasan.lk", BigDecimal("0.01"), Asset.BTC))
        // 0.01 BTC plus the 0.00002 BTC network fee.
        assertEquals(0, (btcBefore - BigDecimal("0.01002")).compareTo(wallet.balanceOf(Asset.BTC)))
        assertEquals(lkrBefore, wallet.balanceOf(Asset.LKR))
        val sent = wallet.transactions.first()
        assertEquals("BTC", sent.currency)
        assertEquals(WalletType.Crypto, sent.wallet)
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
    fun selectedAssetFollowsWalletAndToken() {
        val wallet = WalletViewModel()
        assertEquals(Asset.LKR, wallet.selectedAsset)
        wallet.selectedWallet = WalletType.Crypto
        wallet.selectedToken = CryptoToken.ETH
        assertEquals(Asset.ETH, wallet.selectedAsset)
    }

    @Test
    fun swapMovesFundsAtTheQuotedRateAfterFee() {
        val wallet = WalletViewModel()
        val usdtBefore = wallet.balanceOf(Asset.USDT)
        val btcBefore = wallet.balanceOf(Asset.BTC)
        // 100 USDT - 0.5% fee = 99.50 USDT; at 300 / 30,000,000 LKR that is 0.000995 BTC.
        assertEquals(0, BigDecimal("0.000995").compareTo(SwapRates.quote(Asset.USDT, Asset.BTC, BigDecimal("100"))))
        val received = wallet.swap(Asset.USDT, Asset.BTC, BigDecimal("100"))!!
        assertEquals(0, BigDecimal("0.000995").compareTo(received))
        assertEquals(usdtBefore - BigDecimal("100"), wallet.balanceOf(Asset.USDT))
        assertEquals(0, (btcBefore + received).compareTo(wallet.balanceOf(Asset.BTC)))
        val tx = wallet.transactions.first()
        assertEquals(TransactionDirection.Swap, tx.direction)
        assertEquals("Swap USDT to BTC", tx.title)
        assertTrue(tx.belongsTo(WalletType.Crypto))
    }

    @Test
    fun swapRejectsInvalidRequests() {
        val wallet = WalletViewModel()
        assertNull(wallet.swap(Asset.USDT, Asset.USDT, BigDecimal("1")))
        assertNull(wallet.swap(Asset.USDT, Asset.BTC, BigDecimal("999999")))
        assertNull(wallet.swap(Asset.USDT, Asset.BTC, BigDecimal.ZERO))
    }

    @Test
    fun lkrSwapsShowInBothWallets() {
        val wallet = WalletViewModel()
        wallet.swap(Asset.LKR, Asset.USDT, BigDecimal("3000"))
        val tx = wallet.transactions.first()
        assertTrue(tx.belongsTo(WalletType.Fiat))
        assertTrue(tx.belongsTo(WalletType.Crypto))
    }

    @Test
    fun transactionDetailsAreStable() {
        val tx = WalletViewModel().transactions.first { it.currency == "BTC" }
        assertEquals(tx.reference, tx.reference)
        assertTrue(tx.reference.matches(Regex("""PC-\d{8}""")))
        assertEquals(64, tx.sampleHash.length)
        assertNull(tx.networkFee()) // incoming transfers carry no fee for the receiver
    }
    private val WalletViewModel.visa get() = account.cards.first()
    private val WalletViewModel.commercialBank get() = account.banks.first()

    @Test
    fun cardTopUpCreditsWalletChargesFeeAndNotifies() {
        val wallet = WalletViewModel()
        val before = wallet.balanceOf(Asset.LKR)
        assertTrue(wallet.topUp(BigDecimal("1000"), wallet.visa))
        assertEquals(before + BigDecimal("1000"), wallet.balanceOf(Asset.LKR))
        val tx = wallet.transactions.first()
        assertEquals(TransactionDirection.TopUp, tx.direction)
        assertEquals("Top up from Visa", tx.title)
        assertEquals(0, BigDecimal("15.00").compareTo(tx.fee))
        assertTrue(tx.belongsTo(WalletType.Fiat))
        val notification = wallet.account.notifications.first()
        assertEquals(tx.id, notification.transactionId)
        assertFalse(notification.read)
    }

    @Test
    fun bankTopUpIsFreeAndLimited() {
        val wallet = WalletViewModel()
        assertEquals(0, WalletFees.topUpFee(BigDecimal("1000"), wallet.commercialBank).signum())
        assertFalse(wallet.topUp(BigDecimal("500000.01"), wallet.visa))
        assertFalse(wallet.topUp(BigDecimal.ZERO, wallet.visa))
    }

    @Test
    fun withdrawalDebitsAmountPlusFee() {
        val wallet = WalletViewModel()
        val before = wallet.balanceOf(Asset.LKR)
        assertTrue(wallet.withdraw(BigDecimal("5000"), wallet.commercialBank))
        assertEquals(0, (before - BigDecimal("5025")).compareTo(wallet.balanceOf(Asset.LKR)))
        val tx = wallet.transactions.first()
        assertEquals(TransactionDirection.Withdrawal, tx.direction)
        assertTrue(tx.direction.isDebit)
        assertEquals("Withdraw to Commercial Bank", tx.title)
    }

    @Test
    fun withdrawalRejectsCardsAndOverdrafts() {
        val wallet = WalletViewModel()
        val balance = wallet.balanceOf(Asset.LKR)
        assertFalse(wallet.withdraw(BigDecimal("100"), wallet.visa))
        assertFalse(wallet.withdraw(balance, wallet.commercialBank))
        assertTrue(wallet.withdraw(balance - WalletFees.withdrawalFee, wallet.commercialBank))
        assertEquals(0, wallet.balanceOf(Asset.LKR).signum())
    }

    @Test
    fun externalTransfersAreNotRecentRecipients() {
        val wallet = WalletViewModel()
        wallet.topUp(BigDecimal("1000"), wallet.visa)
        assertTrue(wallet.recentRecipients.none { it.name == "Visa" })
    }

    @Test
    fun cryptoSendsPayTheNetworkFee() {
        val wallet = WalletViewModel()
        val before = wallet.balanceOf(Asset.USDT)
        assertTrue(wallet.send("janidup", BigDecimal("10"), Asset.USDT))
        assertEquals(0, (before - BigDecimal("11.00")).compareTo(wallet.balanceOf(Asset.USDT)))
        assertEquals(0, BigDecimal("1.00").compareTo(wallet.transactions.first().fee))
        // The fee counts towards the balance check.
        assertFalse(wallet.send("janidup", wallet.balanceOf(Asset.USDT), Asset.USDT))
    }

    @Test
    fun monthlyChangeWorksBackFromRecentTransactions() {
        val wallet = WalletViewModel()
        // Seeded LKR: -10,000 -2,500 +6,700 -4,320 = -10,120 over the month.
        // 32,483.75 now vs 42,603.75 a month ago is -23.8%.
        assertEquals(0, BigDecimal("-23.8").compareTo(wallet.monthlyChangePercent(Asset.LKR)))
        wallet.topUp(BigDecimal("10120"), wallet.visa)
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.monthlyChangePercent(Asset.LKR)))
    }

    @Test
    fun turningOffTransactionAlertsSilencesPaymentNotifications() {
        val wallet = WalletViewModel()
        wallet.account.setEnabled(com.payceylon.app.ui.account.AccountSetting.TransactionAlerts, false)
        val count = wallet.account.notifications.size
        wallet.send("janidup", BigDecimal("10"), Asset.LKR)
        assertEquals(count, wallet.account.notifications.size)
    }
}
