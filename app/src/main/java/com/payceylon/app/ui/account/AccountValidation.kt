package com.payceylon.app.ui.account

import com.payceylon.app.ui.auth.normalizeSriLankanMobileNumber
import com.payceylon.app.ui.auth.phoneNumberError
import com.payceylon.app.ui.wallet.PayCeylonDirectory
import java.util.Calendar

private val EmailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val UsernamePattern = Regex("^[a-z0-9._]{3,20}$")
private val NamePattern = Regex("^[\\p{L} .'-]{2,40}$")

internal fun normalizeProfileValue(field: ProfileField, value: String): String = when (field) {
    ProfileField.Name -> value.trim().replace(Regex("\\s+"), " ")
    ProfileField.Username -> value.trim().removePrefix("@").lowercase()
    ProfileField.Phone -> normalizeSriLankanMobileNumber(value)
    ProfileField.Email -> value.trim().lowercase()
}

/** Why [value] can't be saved for [field], or null when it can. */
internal fun validateProfileField(field: ProfileField, value: String, ownHandle: String): String? {
    val normalized = normalizeProfileValue(field, value)
    return when (field) {
        ProfileField.Name -> when {
            normalized.length < 2 -> "Enter your full name."
            !NamePattern.matches(normalized) -> "Use letters only."
            else -> null
        }
        ProfileField.Username -> when {
            normalized.length < 3 -> "Usernames need at least 3 characters."
            !UsernamePattern.matches(normalized) -> "Use lowercase letters, numbers, dots and underscores."
            normalized != ownHandle && PayCeylonDirectory.any { it.handle.equals(normalized, ignoreCase = true) } ->
                "@$normalized is already taken."
            else -> null
        }
        ProfileField.Phone -> phoneNumberError(normalized)
        ProfileField.Email -> if (EmailPattern.matches(normalized)) null else "Enter a valid email address."
    }
}

/** Luhn checksum, which every real card number passes. */
internal fun isLuhnValid(number: String): Boolean {
    val digits = number.filter(Char::isDigit)
    if (digits.length !in 12..19) return false
    val sum = digits.reversed().mapIndexed { index, c ->
        val d = c - '0'
        if (index % 2 == 1) (d * 2).let { if (it > 9) it - 9 else it } else d
    }.sum()
    return sum % 10 == 0
}

internal fun cardNumberError(number: String): String? {
    val digits = number.filter(Char::isDigit)
    val brand = CardBrand.detect(digits) ?: return "Check the card number."
    val lengths = brand.lengths
    return when {
        digits.length !in lengths -> if (lengths.first == lengths.last) {
            "${brand.label} numbers have ${lengths.first} digits."
        } else {
            "${brand.label} numbers have ${lengths.first} to ${lengths.last} digits."
        }
        !isLuhnValid(digits) -> "Check the card number."
        else -> null
    }
}

/** [expiry] is "MMYY". Cards are valid through the end of their expiry month. */
internal fun expiryError(expiry: String, now: Calendar = Calendar.getInstance()): String? {
    if (expiry.length != 4 || !expiry.all(Char::isDigit)) return "Enter the expiry as MM/YY."
    val month = expiry.take(2).toInt()
    val year = 2000 + expiry.drop(2).toInt()
    if (month !in 1..12) return "Enter a month from 01 to 12."
    val currentYear = now.get(Calendar.YEAR)
    val currentMonth = now.get(Calendar.MONTH) + 1
    return when {
        year < currentYear || (year == currentYear && month < currentMonth) -> "This card has expired."
        year > currentYear + 20 -> "Check the expiry year."
        else -> null
    }
}

internal fun cvvError(cvv: String, brand: CardBrand?): String? {
    val length = brand?.cvvLength ?: 3
    return if (cvv.length == length && cvv.all(Char::isDigit)) null else "Enter the $length-digit security code."
}

internal fun accountNumberError(number: String): String? =
    if (number.length in 8..16 && number.all(Char::isDigit)) null else "Account numbers have 8 to 16 digits."

internal fun holderNameError(name: String): String? =
    if (NamePattern.matches(name.trim()) && name.trim().length >= 2) null else "Enter the name as it appears on the account."

/** "Just now", "5m ago", "3h ago", "Yesterday", "4d ago". */
internal fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - timestamp).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        minutes < 48 * 60 -> "Yesterday"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
