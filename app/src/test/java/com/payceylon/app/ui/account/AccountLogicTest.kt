package com.payceylon.app.ui.account

import com.payceylon.app.data.ApiException
import com.payceylon.app.data.FakeBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AccountLogicTest {
    private val backend = FakeBackend()
    private val reported = mutableListOf<String>()

    private suspend fun loadedAccount() = AccountState(backend, CoroutineScope(UnconfinedTestDispatcher())) { reported += it }
        .also { it.load(backend.profile) }

    @Test
    fun validatesProfileFields() {
        assertNull(validateProfileField(ProfileField.Name, "  Kasun   Perera "))
        assertNotNull(validateProfileField(ProfileField.Name, "K"))
        assertNull(validateProfileField(ProfileField.Email, "Kasun@Example.lk"))
        assertNotNull(validateProfileField(ProfileField.Email, "kasun@"))
        assertNull(validateProfileField(ProfileField.Phone, "77 123 4567"))
        assertNotNull(validateProfileField(ProfileField.Phone, "612345678"))
    }

    @Test
    fun usernamesMustBeWellFormed() {
        assertNull(validateProfileField(ProfileField.Username, "@kasun_p"))
        assertNotNull(validateProfileField(ProfileField.Username, "ab"))
        assertNotNull(validateProfileField(ProfileField.Username, "kasun perera"))
    }

    @Test
    fun profileChangesAreSavedOnTheServer() = runTest {
        val account = loadedAccount()
        assertNull(account.updateProfile(ProfileField.Username, "@Kasun_LK"))
        assertEquals("kasun_lk", account.profile.handle)
        assertEquals("kasun_lk", backend.profile.handle)
        assertEquals("Profile updated", account.notifications.first().title)
        // Invalid values never reach the server; taken usernames come back from it.
        assertNotNull(account.updateProfile(ProfileField.Email, "nope"))
        assertEquals("That username is already in use.", account.updateProfile(ProfileField.Username, "nimal"))
        assertEquals("kasun_lk", account.profile.handle)
    }

    @Test
    fun detectsCardBrandsAndChecksums() {
        assertEquals(CardBrand.Visa, CardBrand.detect("4111 1111 1111 1111"))
        assertEquals(CardBrand.Mastercard, CardBrand.detect("5500000000000004"))
        assertEquals(CardBrand.Mastercard, CardBrand.detect("2221000000000009"))
        assertEquals(CardBrand.Amex, CardBrand.detect("378282246310005"))
        assertEquals(CardBrand.UnionPay, CardBrand.detect("6200000000000005"))
        assertEquals(CardBrand.Jcb, CardBrand.detect("3530111333300000"))
        assertEquals(CardBrand.Discover, CardBrand.detect("6011111111111117"))
        assertEquals(CardBrand.Diners, CardBrand.detect("30569309025904"))
        assertNull(CardBrand.detect("9999000000000000"))
        assertTrue(isLuhnValid("4111111111111111"))
        assertFalse(isLuhnValid("4111111111111112"))
        assertNull(cardNumberError("4111111111111111"))
        assertNotNull(cardNumberError("411111111111"))
    }

    @Test
    fun checksExpiryAndSecurityCode() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 3) }
        assertNull(expiryError("1026", now))
        assertNotNull(expiryError("0926", now))
        assertNotNull(expiryError("1326", now))
        assertNull(cvvError("123", CardBrand.Visa))
        assertNotNull(cvvError("123", CardBrand.Amex))
    }

    @Test
    fun addingACardSendsOnlyBrandLastFourAndExpiry() = runTest {
        val account = loadedAccount()
        val card = account.addCard("5500000000000004", "0829", "Kasun Perera")
        val (token, holder, brand, last4, expiry) = backend.cardsSent.single()
        assertTrue(token.startsWith("demo_tok_"))
        assertFalse("5500000000000004" in backend.cardsSent.single().joinToString())
        assertEquals(listOf("Kasun Perera", "Mastercard", "0004", "08/29"), listOf(holder, brand, last4, expiry))
        assertEquals("Mastercard •••• 0004", card.title)
        assertTrue(account.cards.contains(card))
        account.removePaymentMethod(card.id)
        assertFalse(account.paymentMethods.contains(card))
    }

    @Test
    fun markAllReadClearsUnread() = runTest {
        backend.receive("Nimal Silva", "nimal", java.math.BigDecimal.ONE)
        val account = loadedAccount()
        assertEquals(1, account.unreadCount)
        account.markAllRead()
        assertEquals(0, account.unreadCount)
        assertTrue(backend.feed.all { it.read })
    }

    @Test
    fun aFailedReadMarkIsReported() = runTest {
        backend.receive("Nimal Silva", "nimal", java.math.BigDecimal.ONE)
        val account = loadedAccount()
        backend.failNext = ApiException(0, ApiException.NetworkErrorCode, "Can't reach PayCeylon.")
        account.markRead(account.notifications.single().id)
        assertEquals(listOf("Can't reach PayCeylon."), reported)
    }

    @Test
    fun formatsRelativeTimes() {
        val now = 1_000_000_000_000L
        assertEquals("Just now", relativeTime(now - 20_000, now))
        assertEquals("5m ago", relativeTime(now - 5 * 60_000, now))
        assertEquals("3h ago", relativeTime(now - 3 * 3_600_000, now))
        assertEquals("Yesterday", relativeTime(now - 30 * 3_600_000L, now))
        assertEquals("4d ago", relativeTime(now - 4 * 86_400_000L, now))
    }
}
