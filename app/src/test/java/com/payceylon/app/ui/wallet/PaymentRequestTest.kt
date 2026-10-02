package com.payceylon.app.ui.wallet

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.payceylon.app.ui.components.encodeQr
import com.payceylon.app.ui.components.qrLogoZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PaymentRequestTest {
    @Test
    fun uriRoundTripsThroughParser() {
        val plain = PaymentRequest("kasun.p")
        assertEquals("payceylon://pay?to=kasun.p", plain.toUri())
        assertEquals(plain, parsePaymentRequest(plain.toUri()))

        val withAmount = PaymentRequest("kasun.p", BigDecimal("1500.50"), Asset.LKR)
        assertEquals("payceylon://pay?to=kasun.p&amount=1500.5&currency=LKR", withAmount.toUri())
        val parsed = parsePaymentRequest(withAmount.toUri())!!
        assertEquals("kasun.p", parsed.handle)
        assertEquals(0, BigDecimal("1500.5").compareTo(parsed.amount))
        assertEquals(Asset.LKR, parsed.asset)

        val btc = PaymentRequest("kasun.p", BigDecimal("0.0005"), Asset.BTC)
        assertEquals("payceylon://pay?to=kasun.p&amount=0.0005&currency=BTC", btc.toUri())
        assertEquals(Asset.BTC, parsePaymentRequest(btc.toUri())!!.asset)

        // A token QR without an amount still says which token to send.
        val solOnly = PaymentRequest("kasun.p", asset = Asset.SOL)
        assertEquals("payceylon://pay?to=kasun.p&currency=SOL", solOnly.toUri())
        assertEquals(solOnly, parsePaymentRequest(solOnly.toUri()))
    }

    @Test
    fun ignoresInvalidAmounts() {
        val request = parsePaymentRequest("payceylon://pay?to=kasun.p&amount=-4&currency=LKR")!!
        assertNull(request.amount)
        assertNull(parsePaymentRequest("payceylon://pay?amount=10"))
    }

    @Test
    fun keypadInputFromRequestedAmount() {
        assertEquals("1500", BigDecimal("1500.00").toKeypadInput())
        assertEquals("12.5", BigDecimal("12.509").toKeypadInput())
        assertEquals("", BigDecimal("12345678901").toKeypadInput())
        assertEquals("0.0005", BigDecimal("0.0005").toKeypadInput(Asset.BTC.decimals))
        assertEquals("0", BigDecimal("0.0005").toKeypadInput(Asset.LKR.decimals))
    }

    /** Renders the QR exactly as the Receive screen does (logo zone blanked) and decodes it. */
    @Test
    fun receiveQrWithLogoDecodesToSameRequest() {
        val request = PaymentRequest("kasun.p", BigDecimal("2500"), Asset.LKR)
        val matrix = encodeQr(request.toUri())
        val n = matrix.width
        val zone = qrLogoZone(n)
        val scale = 8
        val quiet = 4
        val side = (n + quiet * 2) * scale
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (y in 0 until n) for (x in 0 until n) {
            val dark = matrix[x, y] && !(x in zone && y in zone)
            if (!dark) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0xFF000000.toInt()
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        val decoded = QRCodeReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true)).text
        assertEquals(request, parsePaymentRequest(decoded)?.copy(amount = request.amount))
        assertEquals(0, BigDecimal("2500").compareTo(parsePaymentRequest(decoded)!!.amount))
    }
}
