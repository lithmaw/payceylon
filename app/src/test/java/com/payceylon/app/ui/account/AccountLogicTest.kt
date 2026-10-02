package com.payceylon.app.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AccountLogicTest {
    @Test
    fun validatesProfileFields() {
        assertNull(validateProfileField(ProfileField.Name, "  Kasun   Perera ", "kasun.p"))
        assertNotNull(validateProfileField(ProfileField.Name, "K", "kasun.p"))
        assertNull(validateProfileField(ProfileField.Email, "Kasun@Example.lk", "kasun.p"))
        assertNotNull(validateProfileField(ProfileField.Email, "kasun@", "kasun.p"))
        assertNull(validateProfileField(ProfileField.Phone, "77 123 4567", "kasun.p"))
        assertNotNull(validateProfileField(ProfileField.Phone, "612345678", "kasun.p"))
    }

    @Test
    fun usernamesMustBeFreeAndWellFormed() {
        assertNull(validateProfileField(ProfileField.Username, "@kasun_p", "kasun.p"))
        // Keeping your own handle is fine; taking someone else's is not.
        assertNull(validateProfileField(ProfileField.Username, "kasun.p", "kasun.p"))
        assertEquals("@janidup is already taken.", validateProfileField(ProfileField.Username, "JaniduP", "kasun.p"))
        assertNotNull(validateProfileField(ProfileField.Username, "ab", "kasun.p"))
        assertNotNull(validateProfileField(ProfileField.Username, "kasun perera", "kasun.p"))
    }

    @Test
    fun profileChangesAreSavedAndNotified() {
        val account = AccountState()
        val before = account.notifications.size
        assertNull(account.updateProfile(ProfileField.Username, "@Kasun_LK"))
        assertEquals("kasun_lk", account.profile.handle)
        assertEquals(before + 1, account.notifications.size)
        assertEquals("Username updated", account.notifications.first().title)
        assertNotNull(account.updateProfile(ProfileField.Email, "nope"))
        assertEquals("kasun.perera@gmail.com", account.profile.email)
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
    fun addingACardKeepsOnlyTheLastFourDigits() {
        val account = AccountState()
        val card = account.addCard("5500000000000004", "0829", "Kasun Perera")
        assertEquals("0004", card.last4)
        assertEquals(CardBrand.Mastercard, card.brand)
        assertEquals("Mastercard •••• 0004", card.title)
        assertEquals("Expires 08/29", card.detail)
        assertTrue(account.cards.contains(card))
        account.removePaymentMethod(card.id)
        assertFalse(account.paymentMethods.contains(card))
    }

    @Test
    fun markAllReadClearsUnread() {
        val account = AccountState()
        assertTrue(account.unreadCount > 0)
        account.markAllRead()
        assertEquals(0, account.unreadCount)
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
