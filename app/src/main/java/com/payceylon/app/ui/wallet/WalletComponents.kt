package com.payceylon.app.ui.wallet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.payceylon.app.R
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.InterFontFamily

/** Text style that approximates Figma's cap-trimmed text boxes. */
internal val TrimmedText = TextStyle(
    fontFamily = InterFontFamily,
    lineHeight = 1.em,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Translucent white surface used by the Home header, balance card, and action bar. */
internal fun Modifier.frostedSurface(shape: Shape = RoundedCornerShape(20.dp)): Modifier = composed {
    val colors = LocalWalletColors.current
    this
        .clip(shape)
        .background(colors.frosted, shape)
        .border(1.dp, colors.frostedBorder, shape)
}

@Composable
internal fun CircleBackButton(
    onClick: () -> Unit,
    background: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = "Back",
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(iconTint),
        )
    }
}

/** Initials avatar for PayCeylon users without a profile photo. */
@Composable
internal fun UserAvatar(user: PayCeylonUser, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(LocalWalletColors.current.subtle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = user.initials,
            color = LocalWalletColors.current.content,
            fontFamily = InterFontFamily,
            fontSize = (size * 0.32f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Wallet glyph: a wallet for fiat, the Bitcoin mark for crypto. */
@Composable
internal fun WalletIcon(wallet: WalletType, modifier: Modifier = Modifier, tint: Color = LocalWalletColors.current.content) {
    Image(
        painter = painterResource(if (wallet == WalletType.Crypto) R.drawable.ic_bitcoin else R.drawable.ic_wallet),
        contentDescription = null,
        modifier = modifier.size(18.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Circular wallet glyph used as the leading element in wallet dropdowns. */
@Composable
internal fun WalletBadge(wallet: WalletType) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(LocalWalletColors.current.subtle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        WalletIcon(wallet)
    }
}

/** Branded token icon (web3icons) on a soft circle. */
@Composable
internal fun TokenBadge(token: CryptoToken, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(LocalWalletColors.current.subtle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(token.icon),
            contentDescription = token.tokenName,
            modifier = Modifier.size((size * 0.72f).dp),
        )
    }
}

/** Small inline mark for an asset: the wallet glyph for LKR, the branded icon for tokens. */
@Composable
internal fun AssetIcon(asset: Asset, modifier: Modifier = Modifier) {
    val token = asset.token
    if (token == null) {
        WalletIcon(WalletType.Fiat, modifier)
    } else {
        Image(
            painter = painterResource(token.icon),
            contentDescription = null,
            modifier = modifier.size(20.dp),
        )
    }
}

/** Leading badge for asset pickers. */
@Composable
internal fun AssetBadge(asset: Asset) {
    val token = asset.token
    if (token == null) WalletBadge(WalletType.Fiat) else TokenBadge(token, size = 36)
}

/**
 * Icon for a transaction: the down-left arrow (flipped for outgoing transfers, as in Figma),
 * the exchange mark for swaps, a card for top-ups and a bank for withdrawals.
 */
@Composable
internal fun TransactionGlyph(direction: TransactionDirection, size: Dp, modifier: Modifier = Modifier) {
    val (icon, description) = when (direction) {
        TransactionDirection.Outgoing -> R.drawable.ic_arrow_down_left to "Sent"
        TransactionDirection.Incoming -> R.drawable.ic_arrow_down_left to "Received"
        TransactionDirection.Swap -> R.drawable.ic_exchange to "Swapped"
        TransactionDirection.TopUp -> R.drawable.ic_top_up to "Top up"
        TransactionDirection.Withdrawal -> R.drawable.ic_withdraw to "Withdrawal"
    }
    Image(
        painter = painterResource(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(LocalWalletColors.current.content),
        modifier = modifier
            .size(if (direction == TransactionDirection.Outgoing || direction == TransactionDirection.Incoming) size else size - 2.dp)
            .rotate(if (direction == TransactionDirection.Outgoing) 180f else 0f),
    )
}
