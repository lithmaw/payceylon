package com.payceylon.app.ui.wallet

import com.payceylon.app.data.ApiException
import com.payceylon.app.data.FakeBackend
import com.payceylon.app.data.FakeWalletCache
import com.payceylon.app.data.local.WalletSnapshot
import com.payceylon.app.ui.account.AccountSetting
import com.payceylon.app.ui.account.PaymentMethod
import com.payceylon.app.ui.account.PaymentMethodType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    private val backend = FakeBackend()
    private val cache = FakeWalletCache()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun signedIn(): WalletViewModel = WalletViewModel(backend, cache).also {
        assertEquals(SessionState.SignedIn, it.session)
    }

    @Test
    fun withoutAFirebaseUserTheAppStartsSignedOut() {
        backend.signedIn = false
        assertEquals(SessionState.SignedOut, WalletViewModel(backend, cache).session)
    }

    @Test
    fun aNewAccountPicksANameBeforeTheWallet() = runTest {
        backend.profile = backend.profile.copy(name = PlaceholderProfileName, handle = "pc1a2b3c4d5e")
        val wallet = WalletViewModel(backend, cache)
        assertEquals(SessionState.NeedsProfile, wallet.session)

        assertEquals("That username is already in use.", wallet.completeProfile("Kasun Perera", "nimal"))
        assertEquals(SessionState.NeedsProfile, wallet.session)

        assertEquals(null, wallet.completeProfile("Kasun Perera", "@Kasun_LK"))
        assertEquals(SessionState.SignedIn, wallet.session)
        assertEquals("kasun_lk", wallet.account.profile.handle)
    }

    @Test
    fun signingInLoadsTheWalletFromTheServer() {
        backend.receive("Nimal Silva", "nimal", BigDecimal("250.00"))
        backend.methods += PaymentMethod("pm1", PaymentMethodType.Bank, "Commercial Bank", "2207", "Kasun Perera", "Savings account")
        val wallet = signedIn()
        assertEquals(BigDecimal("1250.00"), wallet.balanceOf(Asset.LKR))
        assertEquals("Receive from Nimal Silva", wallet.transactions.single().title)
        assertEquals("Commercial Bank", wallet.account.banks.single().name)
        assertEquals(1, wallet.account.unreadCount)
    }

    @Test
    fun anUnreachableServerOffersRetry() {
        backend.failNext = ApiException(0, ApiException.NetworkErrorCode, "Can't reach PayCeylon.")
        val wallet = WalletViewModel(backend, cache)
        assertEquals(SessionState.Unreachable("Can't reach PayCeylon."), wallet.session)
        wallet.restoreSession()
        assertEquals(SessionState.SignedIn, wallet.session)
    }

    @Test
    fun aRejectedSignInSignsOut() {
        backend.failNext = ApiException(401, "INVALID_OTP", "The phone verification has expired.")
        val wallet = WalletViewModel(backend, cache)
        assertEquals(SessionState.SignedOut, wallet.session)
        assertFalse(backend.signedIn)
    }

    @Test
    fun sendingRecordsThePaymentAndReloadsBalances() = runTest {
        val wallet = signedIn()
        wallet.send("@nimal", BigDecimal("100.00"), Asset.LKR, "key-1")
        assertEquals(BigDecimal("900.00"), wallet.balanceOf(Asset.LKR))
        assertEquals("Transfer to Nimal Silva", wallet.transactions.first().title)
        assertEquals(1, wallet.account.notifications.size)
    }

    @Test
    fun retryingWithTheSameKeyNeverPaysTwice() = runTest {
        val wallet = signedIn()
        val first = wallet.send("nimal", BigDecimal("100.00"), Asset.LKR, "same-key")
        val retry = wallet.send("nimal", BigDecimal("100.00"), Asset.LKR, "same-key")
        assertEquals(first.id, retry.id)
        assertEquals(BigDecimal("900.00"), wallet.balanceOf(Asset.LKR))
        assertEquals(1, wallet.transactions.size)
    }

    @Test
    fun rejectedPaymentsCarryTheServersMessage() = runTest {
        val wallet = signedIn()
        val error = runCatching { wallet.send("nimal", BigDecimal("5000"), Asset.LKR, "k") }.exceptionOrNull() as ApiException
        assertEquals("Insufficient LKR balance.", error.message)
        assertEquals(BigDecimal("1000.00"), wallet.balanceOf(Asset.LKR))
        assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking { wallet.send("nobody", BigDecimal("1"), Asset.LKR, "k2") }
        }
    }

    @Test
    fun cryptoSendsPayTheNetworkFee() = runTest {
        val wallet = signedIn()
        wallet.send("nimal", BigDecimal("10.00"), Asset.USDT, "k")
        assertEquals(0, BigDecimal("39.00").compareTo(wallet.balanceOf(Asset.USDT)))
    }

    @Test
    fun swapReturnsWhatArrived() = runTest {
        val wallet = signedIn()
        assertEquals(BigDecimal("1.99"), wallet.swap(Asset.LKR, Asset.USDT, BigDecimal("600"), "k"))
        assertEquals(0, BigDecimal("51.99").compareTo(wallet.balanceOf(Asset.USDT)))
    }

    @Test
    fun moneyFromAnotherPhoneArrivesOnTheNextPoll() = runTest {
        val wallet = signedIn()
        backend.receive("Nimal Silva", "nimal", BigDecimal("75.00"))
        wallet.refreshQuietly()
        assertEquals(BigDecimal("1075.00"), wallet.balanceOf(Asset.LKR))
        assertEquals(TransactionDirection.Incoming, wallet.transactions.first().direction)
    }

    @Test
    fun pollingSignsOutWhenTheSessionIsRevoked() = runTest {
        val wallet = signedIn()
        backend.failNext = ApiException(401, "UNAUTHENTICATED", "Please sign in again.")
        wallet.refreshQuietly()
        assertEquals(SessionState.SignedOut, wallet.session)
        assertTrue(wallet.transactions.isEmpty())
    }

    @Test
    fun searchLeavesOutTheSignedInUser() = runTest {
        val wallet = signedIn()
        assertEquals(listOf("nimal", "nimesha99"), wallet.searchUsers("nim").map { it.handle })
        assertTrue(wallet.searchUsers("kasun").isEmpty())
        assertTrue(wallet.searchUsers("  @ ").isEmpty())
    }

    @Test
    fun recentRecipientsAreDistinctPeopleOnly() = runTest {
        backend.methods += PaymentMethod("pm1", PaymentMethodType.Card, "Visa", "4242", "Kasun Perera", "Expires 08/29")
        val wallet = signedIn()
        wallet.send("nimal", BigDecimal("1"), Asset.LKR, "a")
        wallet.send("nimal", BigDecimal("1"), Asset.LKR, "b")
        wallet.topUp(BigDecimal("100"), wallet.account.cards.single(), "c")
        assertEquals(listOf("nimal"), wallet.recentRecipients.map { it.handle })
    }

    @Test
    fun aSettingThatFailsToSaveSwitchesBack() = runTest {
        val wallet = signedIn()
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { wallet.messages.toList(messages) }
        backend.failNext = ApiException(0, ApiException.NetworkErrorCode, "Can't reach PayCeylon.")
        wallet.account.setEnabled(AccountSetting.HideBalance, true)
        assertFalse(wallet.account.isEnabled(AccountSetting.HideBalance))
        assertEquals(listOf("Can't reach PayCeylon."), messages)
    }

    @Test
    fun loggingOutRevokesAndClearsEverything() = runTest {
        backend.receive("Nimal Silva", "nimal", BigDecimal("1"))
        val wallet = signedIn()
        wallet.logOut()
        assertTrue(backend.loggedOut)
        assertFalse(backend.signedIn)
        assertEquals(SessionState.SignedOut, wallet.session)
        assertTrue(wallet.transactions.isEmpty())
        assertEquals(BigDecimal.ZERO, wallet.balanceOf(Asset.LKR))
    }

    // --- Local database (Room cache) ---------------------------------------------------------

    private fun savedWallet(lkr: String) = WalletSnapshot(
        profile = backend.profile,
        photo = null,
        balances = mapOf(Asset.LKR to BigDecimal(lkr)),
        transactions = listOf(
            WalletTransaction("old", "Nimal Silva", "nimal", TransactionDirection.Incoming, "LKR", BigDecimal("5.00"), WalletType.Fiat, 1L),
        ),
        notifications = emptyList(),
        paymentMethods = emptyList(),
        settings = mapOf(AccountSetting.HideBalance to true),
    )

    @Test
    fun whatTheServerSendsIsSavedOnTheDevice() {
        backend.receive("Nimal Silva", "nimal", BigDecimal("250.00"))
        signedIn()
        val saved = cache.snapshot!!
        assertEquals(BigDecimal("1250.00"), saved.balances[Asset.LKR])
        assertEquals("Receive from Nimal Silva", saved.transactions.single().title)
        assertEquals("kasun.p", saved.profile.handle)
    }

    @Test
    fun offlineLaunchShowsTheSavedWallet() {
        cache.snapshot = savedWallet("777.00")
        backend.failNext = ApiException(0, ApiException.NetworkErrorCode, "Can't reach PayCeylon.")
        val wallet = WalletViewModel(backend, cache)
        assertEquals(SessionState.SignedIn, wallet.session)
        assertTrue(wallet.isOffline)
        assertEquals(BigDecimal("777.00"), wallet.balanceOf(Asset.LKR))
        assertEquals("old", wallet.transactions.single().id)
        assertTrue(wallet.account.isEnabled(AccountSetting.HideBalance))
    }

    @Test
    fun theSavedWalletIsReplacedByTheServersOnceOnline() = runTest {
        cache.snapshot = savedWallet("777.00")
        backend.failNext = ApiException(0, ApiException.NetworkErrorCode, "Can't reach PayCeylon.")
        val wallet = WalletViewModel(backend, cache)
        wallet.refreshQuietly() // the next poll reaches the server
        assertFalse(wallet.isOffline)
        assertEquals(BigDecimal("1000.00"), wallet.balanceOf(Asset.LKR))
        assertEquals(BigDecimal("1000.00"), cache.snapshot!!.balances[Asset.LKR])
    }

    @Test
    fun losingTheConnectionWhileOpenShowsTheOfflineNote() = runTest {
        val wallet = signedIn()
        backend.failNext = ApiException(0, ApiException.NetworkErrorCode, "Can't reach PayCeylon.")
        wallet.refreshQuietly()
        assertTrue(wallet.isOffline)
        assertEquals(SessionState.SignedIn, wallet.session)
    }

    @Test
    fun unchangedDataIsNotRewritten() = runTest {
        val wallet = signedIn()
        val saves = cache.saves
        wallet.refresh()
        assertEquals(saves, cache.saves)
    }

    @Test
    fun depositAddressesComeFromTheServerAndAreSaved() {
        val wallet = signedIn()
        assertEquals("USDT-ADDRESS-kasun.p", wallet.addressOf(CryptoToken.USDT))
        assertEquals("USDT-ADDRESS-kasun.p", cache.snapshot!!.addresses[CryptoToken.USDT])
    }

    @Test
    fun offlineLaunchUsesSavedAddresses() {
        cache.snapshot = savedWallet("1.00").copy(addresses = mapOf(CryptoToken.BTC to "bc1qsaved"))
        backend.failNext = ApiException(0, ApiException.NetworkErrorCode, "Can't reach PayCeylon.")
        val wallet = WalletViewModel(backend, cache)
        assertEquals("bc1qsaved", wallet.addressOf(CryptoToken.BTC))
        // Tokens with no saved address fall back to the example one rather than showing nothing.
        assertEquals(CryptoToken.ETH.address, wallet.addressOf(CryptoToken.ETH))
    }

    @Test
    fun signingOutWipesTheDeviceCopy() = runTest {
        val wallet = signedIn()
        wallet.logOut()
        assertEquals(null, cache.snapshot)
    }

    @Test
    fun monthlyChangeWorksBackFromRecentTransactions() = runTest {
        val wallet = signedIn()
        assertEquals(null, wallet.monthlyChangePercent(Asset.BTC))
        backend.receive("Nimal Silva", "nimal", BigDecimal("250.00"))
        wallet.refresh()
        // 1,250 now vs 1,000 before the payment arrived.
        assertEquals(0, BigDecimal("25.0").compareTo(wallet.monthlyChangePercent(Asset.LKR)))
    }
}
