package com.payceylon.app.ui.auth

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class SriLankanPhoneNumberTest {
    @Test
    fun formatsNineDigitMobileNumber() {
        assertEquals("70 123 4567", formatSriLankanMobileNumber("701234567"))
    }

    @Test
    fun formatsWithSpacesWhileTypingWithoutChangingStoredDigits() {
        val transformed = SriLankanPhoneVisualTransformation.filter(AnnotatedString("701234567"))

        assertEquals("70 123 4567", transformed.text.text)
        assertEquals(11, transformed.offsetMapping.originalToTransformed(9))
        assertEquals(9, transformed.offsetMapping.transformedToOriginal(11))
    }

    @Test
    fun stripsNonDigitsAndLimitsInputToNineDigits() {
        assertEquals("701234567", normalizeSriLankanMobileNumber("70-123 45678"))
    }

    @Test
    fun requiresNineDigitsStartingWithSeven() {
        assertEquals(PhoneNumberValidation.InvalidLength, validateSriLankanMobileNumber("70123"))
        assertEquals(PhoneNumberValidation.InvalidPrefix, validateSriLankanMobileNumber("601234567"))
        assertEquals(PhoneNumberValidation.Valid, validateSriLankanMobileNumber("701234567"))
    }

    @Test
    fun masksTheValidatedNumberForOtp() {
        assertEquals("+94 70 *** **67", maskSriLankanMobileNumber("70 123 4567"))
    }
}
