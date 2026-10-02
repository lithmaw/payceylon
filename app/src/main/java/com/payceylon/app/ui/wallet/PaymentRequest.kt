package com.payceylon.app.ui.wallet

import java.math.BigDecimal
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A PayCeylon payment code, shared by the Receive QR (encoder), the scanner and deep links
 * (decoders): `payceylon://pay?to=<handle>[&amount=<decimal>]&currency=<LKR|USDT|BTC|ETH|SOL>`.
 */
data class PaymentRequest(
    val handle: String,
    val amount: BigDecimal? = null,
    val asset: Asset? = null,
) {
    fun toUri(): String = buildString {
        append("payceylon://pay?to=").append(URLEncoder.encode(handle, "UTF-8"))
        if (amount != null && amount > BigDecimal.ZERO) {
            append("&amount=").append(amount.stripTrailingZeros().toPlainString())
        }
        // The currency travels even without an amount, so a token QR opens Send Money in
        // that token; an amount on its own defaults to LKR.
        val currency = asset ?: if (amount != null) Asset.LKR else null
        if (currency != null) append("&currency=").append(currency.code)
    }
}

private val HandlePattern = Regex("^[a-z0-9._]{3,30}$")

/** Accepts a full `payceylon://` code, `@handle`, or a bare handle. */
internal fun parsePaymentRequest(raw: String): PaymentRequest? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("payceylon://", ignoreCase = true)) {
        return normalizeHandle(trimmed)?.let { PaymentRequest(it) }
    }
    val params = runCatching { URI(trimmed).rawQuery }.getOrNull()
        ?.split('&')
        ?.mapNotNull { pair ->
            val key = pair.substringBefore('=', missingDelimiterValue = "")
            if (key.isEmpty()) null else key to URLDecoder.decode(pair.substringAfter('='), "UTF-8")
        }
        ?.toMap()
        ?: return null
    val handle = params["to"]?.let(::normalizeHandle) ?: return null
    val asset = Asset.fromCode(params["currency"])
    val amount = params["amount"]?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    return PaymentRequest(handle, amount, asset ?: if (amount != null) Asset.LKR else null)
}

internal fun parsePayCeylonHandle(raw: String): String? = parsePaymentRequest(raw)?.handle

private fun normalizeHandle(value: String): String? =
    value.removePrefix("@").lowercase().takeIf { it.matches(HandlePattern) }

/** The signed-in demo account; replace with the authenticated profile. */
val CurrentUser = PayCeylonUser("Kasun Perera", "kasun.p")
