package com.payceylon.app.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.payceylon.app.R

/**
 * Colours for the wallet screens. Fiat uses the light Figma palette; the crypto wallet
 * switches the whole app to near-black (#040404) with PayCeylon red accents.
 */
@Immutable
data class WalletColors(
    val isDark: Boolean,
    /** Behind Home / Receive, below the backdrop art. */
    val background: Color,
    /** Plain full-screen pages (Send Money, Send to Username). */
    val page: Color,
    /** Solid cards: transaction rows, dropdown menus. */
    val card: Color,
    val frosted: Color,
    val frostedBorder: Color,
    val content: Color,
    val contentSecondary: Color,
    val contentTertiary: Color,
    /** Low-emphasis fills: chips, icon circles, inputs. */
    val subtle: Color,
    /** Pill buttons (Send, Receive, Share) and the primary button. */
    val action: Color,
    val actionPressed: Color,
    val onAction: Color,
    /** Quick-action icon circles. */
    val tile: Color,
    val disabled: Color,
    val onDisabled: Color,
    val priceUp: Color,
    val priceDown: Color,
    /** Header art behind Home / Receive. */
    @param:DrawableRes val backdrop: Int?,
)

val FiatWalletColors = WalletColors(
    isDark = false,
    background = WalletBackground,
    page = WalletBackground,
    card = Color.White,
    frosted = FrostedWhite,
    frostedBorder = Color.White,
    content = Color.Black,
    contentSecondary = Black60,
    contentTertiary = Black40,
    subtle = Black5,
    action = Color.Black,
    actionPressed = Color(0xFF252525),
    onAction = Color.White,
    tile = Color.Black,
    // A touch darker than SoftGrey so disabled buttons still read on the #F6F6F6 page.
    disabled = Color(0xFFE8E8EA),
    onDisabled = MutedText,
    priceUp = PriceGreen,
    priceDown = PriceRed,
    backdrop = R.drawable.home_bg,
)

val CryptoWalletColors = WalletColors(
    isDark = true,
    background = Color(0xFF040404),
    page = Color(0xFF040404),
    card = Color(0xFF131313),
    frosted = Color(0xD9131313),
    frostedBorder = Color(0x14FFFFFF),
    content = Color.White,
    contentSecondary = Color(0x99FFFFFF),
    contentTertiary = Color(0x66FFFFFF),
    subtle = Color(0x14FFFFFF),
    action = PayCeylonPrimary,
    actionPressed = Color(0xFFB0073A),
    onAction = Color.White,
    tile = Color(0x1FFFFFFF),
    disabled = Color(0x14FFFFFF),
    onDisabled = Color(0x66FFFFFF),
    priceUp = Color(0xFF2FD15B),
    priceDown = Color(0xFFFF4D5E),
    backdrop = R.drawable.crypto_bg,
)

/** Defaults to the fiat palette so screens outside the wallet (onboarding, OTP) are unaffected. */
val LocalWalletColors = staticCompositionLocalOf { FiatWalletColors }
