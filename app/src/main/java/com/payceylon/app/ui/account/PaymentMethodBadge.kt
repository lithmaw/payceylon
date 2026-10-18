package com.payceylon.app.ui.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.payceylon.app.R
import com.payceylon.app.ui.bills.BillerLogo
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.wallet.MethodBadge

/** The bank's logo for each name in [SriLankanBanks]; null shows the generic bank icon. */
@DrawableRes
internal fun bankLogo(name: String): Int? = when (name) {
    "Bank of Ceylon" -> R.drawable.logo_bank_boc
    "People's Bank" -> R.drawable.logo_bank_peoples
    "Commercial Bank" -> R.drawable.logo_bank_commercial
    "Hatton National Bank" -> R.drawable.logo_bank_hnb
    "Sampath Bank" -> R.drawable.logo_bank_sampath
    "Seylan Bank" -> R.drawable.logo_bank_seylan
    "Nations Trust Bank" -> R.drawable.logo_bank_ntb
    "DFCC Bank" -> R.drawable.logo_bank_dfcc
    "Pan Asia Bank" -> R.drawable.logo_bank_panasia
    else -> null
}

/** The card network named [name] ("Visa", "AMEX"…), as top-up transactions name their card. */
internal fun cardBrandNamed(name: String): CardBrand? = CardBrand.entries.firstOrNull { it.label.equals(name, ignoreCase = true) }

/** True when [name] (a bank or card network, as transactions name them) has a logo to show. */
internal fun hasMethodLogo(name: String): Boolean = bankLogo(name) != null || cardBrandNamed(name) != null

/** The logo of the bank or card network named [name], on a white rounded square. */
@Composable
internal fun MethodLogo(name: String, size: Dp, modifier: Modifier = Modifier) {
    val bank = bankLogo(name)
    val brand = cardBrandNamed(name)
    when {
        bank != null -> BillerLogo(bank, size, modifier)
        brand != null -> {
            val shape = RoundedCornerShape(size * 0.28f)
            Box(
                modifier = modifier
                    .size(size)
                    .clip(shape)
                    .background(Color.White)
                    .border(size * 0.025f, LocalWalletColors.current.subtle, shape),
                contentAlignment = Alignment.Center,
            ) {
                // Card marks are wide; two thirds of the tile keeps them in line with the bank logos.
                CardLogo(brand, maxWidth = size * 0.62f, maxHeight = size * 0.4f)
            }
        }
    }
}

/**
 * A linked card or bank as a logo: the card network (Visa, Mastercard, Amex…) or the bank,
 * on a white rounded square; the generic card or bank icon when there is no logo.
 */
@Composable
internal fun PaymentMethodBadge(method: PaymentMethod, size: Dp) {
    val name = method.brand?.label ?: method.name
    if (hasMethodLogo(name)) MethodLogo(name, size) else MethodBadge(method.icon, size.value.toInt())
}