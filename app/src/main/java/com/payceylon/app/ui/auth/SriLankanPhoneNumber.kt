package com.payceylon.app.ui.auth

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal enum class PhoneNumberValidation {
    Empty,
    InvalidLength,
    InvalidPrefix,
    Valid,
}

internal fun normalizeSriLankanMobileNumber(value: String): String =
    value.filter(Char::isDigit).take(9)

internal fun formatSriLankanMobileNumber(value: String): String {
    val digits = normalizeSriLankanMobileNumber(value)
    return when {
        digits.length <= 2 -> digits
        digits.length <= 5 -> "${digits.take(2)} ${digits.drop(2)}"
        else -> "${digits.take(2)} ${digits.substring(2, 5)} ${digits.drop(5)}"
    }
}

internal object SriLankanPhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = normalizeSriLankanMobileNumber(text.text)
        val formatted = formatSriLankanMobileNumber(digits)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val mapped = when {
                    offset <= 2 -> offset
                    offset <= 5 -> offset + 1
                    else -> offset + 2
                }
                return mapped.coerceIn(0, formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val mapped = when {
                    offset <= 2 -> offset
                    offset <= 6 -> offset - 1
                    else -> offset - 2
                }
                return mapped.coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

internal fun validateSriLankanMobileNumber(value: String): PhoneNumberValidation {
    val digits = normalizeSriLankanMobileNumber(value)
    return when {
        digits.isEmpty() -> PhoneNumberValidation.Empty
        digits.length != 9 -> PhoneNumberValidation.InvalidLength
        !digits.startsWith('7') -> PhoneNumberValidation.InvalidPrefix
        else -> PhoneNumberValidation.Valid
    }
}

internal fun phoneNumberError(value: String): String? = when (validateSriLankanMobileNumber(value)) {
    PhoneNumberValidation.Empty -> "Enter your mobile number."
    PhoneNumberValidation.InvalidLength -> "Mobile number must contain 9 digits."
    PhoneNumberValidation.InvalidPrefix -> "Sri Lankan mobile numbers must start with 7."
    PhoneNumberValidation.Valid -> null
}

internal fun maskSriLankanMobileNumber(value: String): String {
    val digits = normalizeSriLankanMobileNumber(value)
    return if (digits.length == 9) {
        "+94 ${digits.take(2)} *** **${digits.takeLast(2)}"
    } else {
        "+94 ** *** ****"
    }
}
