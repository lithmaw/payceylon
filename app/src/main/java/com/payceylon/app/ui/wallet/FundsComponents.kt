package com.payceylon.app.ui.wallet

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.auth.PayCeylonButton
import com.payceylon.app.ui.components.DropdownAnchor
import com.payceylon.app.ui.components.PayCeylonDropdownMenu
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.components.tap
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary
import kotlinx.coroutines.delay
import java.math.BigDecimal

/** Back button with a centred title, shared by the plain wallet pages. */
@Composable
internal fun PageHeader(title: String, onBack: () -> Unit) {
    val colors = LocalWalletColors.current
    Box(Modifier.fillMaxWidth()) {
        CircleBackButton(onClick = onBack, background = colors.card, iconTint = colors.content)
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            color = colors.content,
            fontFamily = InterFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** PayCeylon-red glyph on a soft red circle, used for cards and bank accounts. */
@Composable
internal fun MethodBadge(@DrawableRes icon: Int, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(PayCeylonPrimary.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size((size * 0.48f).dp),
            colorFilter = ColorFilter.tint(PayCeylonPrimary),
        )
    }
}

/**
 * Card that shows where money comes from or goes to, and opens a picker of the other
 * linked accounts.
 */
@Composable
internal fun <T> MethodSelector(
    caption: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    title: (T) -> String,
    icon: (T) -> Int,
    /** Name shown in the picker; [detail] goes underneath it. */
    menuLabel: (T) -> String,
    detail: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    var isMenuOpen by remember { mutableStateOf(false) }
    val chevronRotation = if (isMenuOpen) 0f else 180f
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tap(role = Role.DropdownList) { isMenuOpen = true }
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MethodBadge(icon(selected))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = caption,
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = title(selected),
                    color = colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (options.size > 1) {
                Image(
                    painter = painterResource(R.drawable.ic_chevron_up_small),
                    contentDescription = "Change",
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(width = 9.5.dp, height = 5.5.dp)
                        .rotate(chevronRotation),
                    colorFilter = ColorFilter.tint(colors.content),
                )
            }
        }
        PayCeylonDropdownMenu(
            expanded = isMenuOpen,
            onDismissRequest = { isMenuOpen = false },
            options = options,
            selected = selected,
            onSelect = onSelect,
            label = menuLabel,
            supportingText = detail,
            leading = { MethodBadge(icon(it), size = 36) },
            width = 310.dp,
        )
    }
}

/** Preset LKR top-up amounts. */
internal val QuickTopUpAmounts: List<BigDecimal> = listOf("1000", "5000", "10000", "25000").map(::BigDecimal)

/** Shown in place of [MethodSelector] when nothing suitable is linked yet. */
@Composable
internal fun AddMethodPrompt(caption: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWalletColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MethodBadge(R.drawable.ic_amount)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(caption, color = colors.contentSecondary, fontFamily = InterFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(label, color = PayCeylonPrimary, fontFamily = InterFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Row of tappable preset amounts; the one matching the current input is highlighted. */
@Composable
internal fun QuickAmountRow(
    amounts: List<BigDecimal>,
    current: BigDecimal,
    onPick: (BigDecimal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        amounts.forEach { value ->
            val isSelected = value.compareTo(current) == 0
            Text(
                text = value.toQuickLabel(),
                modifier = Modifier
                    .tap { onPick(value) }
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) PayCeylonPrimary else colors.card)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = if (isSelected) Color.White else colors.content,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** "1000" -> "1K", "25000" -> "25K", "0.005" -> "0.005". */
private fun BigDecimal.toQuickLabel(): String =
    if (this >= BigDecimal(1000) && remainder(BigDecimal(1000)).signum() == 0) {
        "${divide(BigDecimal(1000)).toPlainString()}K"
    } else {
        stripTrailingZeros().toPlainString()
    }

/**
 * Confirmation shown after a top-up or withdrawal: "[prefix] [account]", with the account in
 * PayCeylon red, and an optional [eta] line.
 */
@Composable
internal fun FundsSuccess(
    headline: String,
    prefix: String,
    account: String,
    onDone: () -> Unit,
    eta: String? = null,
) {
    val colors = LocalWalletColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(colors.priceUp, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = headline,
                modifier = Modifier,
                color = colors.content,
                fontFamily = InterFontFamily,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = prefix,
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = account,
                    color = PayCeylonPrimary,
                    fontFamily = InterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (eta != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "ETA: $eta",
                    modifier = Modifier,
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        PayCeylonButton(
            label = "Done",
            onClick = onDone,
            modifier = Modifier,
        )
    }
}
