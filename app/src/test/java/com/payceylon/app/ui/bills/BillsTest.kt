package com.payceylon.app.ui.bills

import org.junit.Assert.assertEquals
import org.junit.Test

class BillsTest {
    private val dialog = Biller("dialog", "Dialog", "Dialog", BillCategory.Mobile, true, "Mobile number", "07X XXX XXXX")
    private val ceb = Biller("ceb", "Ceylon Electricity Board", "CEB", BillCategory.Electricity, false, "Account number", "10 digits")

    @Test
    fun accountsAreSentAsDigitsOnly() {
        assertEquals("0771234567", normalizeAccount("077 123 4567"))
        assertEquals("1234567890", normalizeAccount("12/34-567 890"))
    }

    @Test
    fun mobileNumbersAreGroupedForDisplay() {
        assertEquals("077 123 4567", formatAccount(dialog, "0771234567"))
        assertEquals("1234567890", formatAccount(ceb, "1234567890"))
    }
}
