package com.payceylon.app.ui.account

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.components.tap
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary

/** The user's photo, or the default PayCeylon avatar. */
@Composable
internal fun ProfileAvatar(photo: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    val imageModifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(Color.Black)
    if (photo != null) {
        Image(bitmap = photo, contentDescription = "Profile photo", modifier = imageModifier, contentScale = ContentScale.Crop)
    } else {
        Image(painter = painterResource(R.drawable.home_avatar), contentDescription = "Profile photo", modifier = imageModifier, contentScale = ContentScale.Crop)
    }
}

/**
 * A line icon in a 24dp slot. The app's icons come from two grids (Figma's 20px icons and
 * the 24px settings set) and the bell sits small inside a 40px canvas, so each is drawn at
 * the size that gives the same 2dp stroke and similar optical size.
 */
@Composable
internal fun LineIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, tint: Color = LocalWalletColors.current.content) {
    val drawSize = when (icon) {
        R.drawable.ic_top_up, R.drawable.ic_withdraw, R.drawable.ic_exchange, R.drawable.ic_amount -> 20.dp
        R.drawable.ic_bell -> 46.dp
        else -> 24.dp
    }
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.requiredSize(drawSize),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

/** A titled group of rows on one card. */
@Composable
internal fun SettingsSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalWalletColors.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(start = 4.dp),
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.card)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            content()
        }
    }
}

/**
 * One settings row: plain icon, title, optional value underneath, and a trailing chevron
 * (when tappable) or [trailing] content such as a switch.
 */
@Composable
internal fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    titleColor: Color = LocalWalletColors.current.content,
    iconTint: Color = LocalWalletColors.current.content,
    /** Replaces the line icon, e.g. with a card network logo. */
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalWalletColors.current
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (leading != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading() }
            } else {
                LineIcon(icon, tint = iconTint)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = titleColor,
                    fontFamily = InterFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (value != null) {
                    Text(
                        text = value,
                        color = colors.contentSecondary,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Image(
                    painter = painterResource(R.drawable.ic_chevron_up_small),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .rotate(90f)
                        .size(width = 9.5.dp, height = 5.5.dp),
                    colorFilter = ColorFilter.tint(colors.contentTertiary),
                )
            }
        }
        if (showDivider) HorizontalDivider(thickness = 1.dp, color = colors.subtle)
    }
}

/** On/off switch in PayCeylon red. */
@Composable
internal fun PayCeylonSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWalletColors.current
    val track = if (checked) PayCeylonPrimary else colors.disabled
    val thumbOffset = if (checked) 20.dp else 2.dp
    Box(
        modifier = modifier
            .tap(role = Role.Switch) { onCheckedChange(!checked) }
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/** Two-option toggle, e.g. Debit / Credit. */
@Composable
internal fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val background = if (selected) colors.content else Color.Transparent
            Box(
                modifier = Modifier
                    .weight(1f)
                    .tap { onSelect(index) }
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    color = if (selected) colors.page else colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * The card network's logo in its own colours, scaled to fit [maxWidth] x [maxHeight]; a
 * generic card icon when the brand is unknown.
 */
@Composable
internal fun CardLogo(brand: CardBrand?, modifier: Modifier = Modifier, maxWidth: Dp = 24.dp, maxHeight: Dp = 16.dp) {
    if (brand == null) {
        LineIcon(R.drawable.ic_top_up, modifier)
        return
    }
    val painter = painterResource(brand.logo)
    val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
    val height = if (maxHeight * ratio > maxWidth) maxWidth / ratio else maxHeight
    Image(
        painter = painter,
        contentDescription = brand.label,
        modifier = modifier.size(width = height * ratio, height = height),
    )
}
