package com.payceylon.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.payceylon.app.ui.account.AccountSetting
import com.payceylon.app.ui.account.AppNotification
import com.payceylon.app.ui.account.CardBrand
import com.payceylon.app.ui.account.NotificationKind
import com.payceylon.app.ui.account.PaymentMethod
import com.payceylon.app.ui.account.PaymentMethodType
import com.payceylon.app.ui.account.UserProfile
import com.payceylon.app.ui.wallet.Asset
import com.payceylon.app.ui.wallet.CryptoToken
import com.payceylon.app.ui.wallet.TransactionDirection
import com.payceylon.app.ui.wallet.WalletTransaction
import com.payceylon.app.ui.wallet.WalletType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Runs the real Room database on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class RoomWalletCacheTest {
    private lateinit var db: PayCeylonDatabase
    private lateinit var cache: RoomWalletCache

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, PayCeylonDatabase::class.java).build()
        cache = RoomWalletCache(db.walletDao())
    }

    @After
    fun tearDown() = db.close()

    private val snapshot = WalletSnapshot(
        profile = UserProfile("Kasun Perera", "kasun.p", "771234567", "kasun@example.com", "http://x/v1/photos/1"),
        photo = byteArrayOf(1, 2, 3),
        balances = mapOf(Asset.LKR to BigDecimal("32483.75"), Asset.BTC to BigDecimal("0.042150")),
        transactions = listOf(
            WalletTransaction(
                "tx2", "PayCeylon Swap", "payceylon", TransactionDirection.Swap, "LKR", BigDecimal("3000.00"),
                WalletType.Fiat, 2_000L, toCurrency = "USDT", toAmount = BigDecimal("9.95"),
                fee = BigDecimal("15.00"), feeCurrency = "LKR", reference = "PC-2",
            ),
            WalletTransaction("tx1", "Nimal Silva", "nimal", TransactionDirection.Incoming, "LKR", BigDecimal("5"), WalletType.Fiat, 1_000L, reference = "PC-1"),
        ),
        notifications = listOf(AppNotification("n1", NotificationKind.Transaction, "Payment received", "LKR 5", 1_000L, "tx1", read = false)),
        paymentMethods = listOf(
            PaymentMethod("pm2", PaymentMethodType.Bank, "Commercial Bank", "2207", "Kasun Perera", "Savings account"),
            PaymentMethod("pm1", PaymentMethodType.Card, "Visa", "4242", "Kasun Perera", "Expires 08/29", CardBrand.Visa),
        ),
        settings = mapOf(AccountSetting.HideBalance to true, AccountSetting.Promotions to false),
        addresses = mapOf(CryptoToken.USDT to "TabcdefghijkmnopqrstuvwxyzABCDEFGH", CryptoToken.ETH to "0x" + "a".repeat(40)),
        savedAt = 42L,
    )

    @Test
    fun aSavedWalletComesBackTheSame() = runBlocking {
        cache.save(snapshot)
        val loaded = cache.load()!!
        assertEquals(snapshot.profile, loaded.profile)
        assertArrayEquals(snapshot.photo, loaded.photo)
        assertEquals(snapshot.balances, loaded.balances)
        assertEquals(snapshot.transactions, loaded.transactions) // newest first
        assertEquals(snapshot.notifications, loaded.notifications)
        assertEquals(snapshot.paymentMethods, loaded.paymentMethods) // server order kept
        assertEquals(snapshot.settings, loaded.settings)
        assertEquals(snapshot.addresses, loaded.addresses)
        assertEquals(42L, loaded.savedAt)
    }

    @Test
    fun savingReplacesThePreviousSnapshot() = runBlocking {
        cache.save(snapshot)
        cache.save(snapshot.copy(transactions = snapshot.transactions.take(1), paymentMethods = emptyList()))
        val loaded = cache.load()!!
        assertEquals(listOf("tx2"), loaded.transactions.map { it.id })
        assertEquals(emptyList<PaymentMethod>(), loaded.paymentMethods)
    }

    @Test
    fun clearingLeavesNothing() = runBlocking {
        cache.save(snapshot)
        cache.clear()
        assertNull(cache.load())
    }
}
