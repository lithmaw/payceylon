package com.payceylon.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.payceylon.app.R
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.PayCeylonPrimary

private val DropdownShape = RoundedCornerShape(20.dp)
private val ItemShape = RoundedCornerShape(14.dp)

enum class DropdownAnchor { Start, Center, End }

/** The white, softly shadowed card that every PayCeylon dropdown is drawn on. */
@Composable
internal fun PayCeylonDropdownSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .dropShadow(
                shape = DropdownShape,
                shadow = Shadow(
                    radius = 30.dp,
                    color = Color.Black.copy(alpha = 0.10f),
                    offset = DpOffset(0.dp, 8.dp),
                ),
            )
            .clip(DropdownShape)
            .background(LocalWalletColors.current.card)
            .border(1.dp, LocalWalletColors.current.subtle, DropdownShape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** A selectable row inside [PayCeylonDropdownSurface]. */
@Composable
internal fun PayCeylonDropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = when {
            isPressed -> LocalWalletColors.current.content.copy(alpha = 0.08f)
            selected -> LocalWalletColors.current.subtle
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "dropdown item background",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(background)
            .bounceClick(
                pressedScale = 0.98f,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = text,
                color = LocalWalletColors.current.content,
                fontFamily = InterFontFamily,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    color = LocalWalletColors.current.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
        if (selected) {
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "Selected",
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(PayCeylonPrimary),
            )
        }
    }
}

/**
 * PayCeylon-styled replacement for Material's DropdownMenu. Place it inside the same
 * Box as its anchor; it opens below (or above, when there is no room) with a short
 * fade-and-scale from the anchor edge.
 */
@Composable
internal fun <T> PayCeylonDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    supportingText: (T) -> String? = { null },
    leading: (@Composable (T) -> Unit)? = null,
    anchor: DropdownAnchor = DropdownAnchor.Start,
    width: Dp = 230.dp,
    gap: Dp = 8.dp,
) {
    PayCeylonPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        anchor = anchor,
        width = width,
        gap = gap,
    ) {
        options.forEach { option ->
            PayCeylonDropdownItem(
                text = label(option),
                supportingText = supportingText(option),
                selected = option == selected,
                leading = leading?.let { { it(option) } },
                onClick = {
                    onSelect(option)
                    onDismissRequest()
                },
            )
        }
    }
}

/**
 * The animated popup behind [PayCeylonDropdownMenu], for panels with custom content (such as
 * the notification feed). Place it inside the same Box as its anchor.
 */
@Composable
internal fun PayCeylonPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: DropdownAnchor = DropdownAnchor.Start,
    width: Dp = 230.dp,
    gap: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    if (!visibleState.currentState && !visibleState.targetState) return

    val density = LocalDensity.current
    val gapPx = with(density) { gap.roundToPx() }
    val insetPx = with(density) { ShadowInset.roundToPx() }
    val positionProvider = remember(anchor, gapPx, insetPx) {
        DropdownPositionProvider(anchor, gapPx, insetPx)
    }
    val transformOrigin = TransformOrigin(
        pivotFractionX = when (anchor) {
            DropdownAnchor.Start -> 0f
            DropdownAnchor.Center -> 0.5f
            DropdownAnchor.End -> 1f
        },
        pivotFractionY = if (positionProvider.opensAbove) 1f else 0f,
    )

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(160, easing = PayCeylonEasing)) +
                scaleIn(tween(220, easing = PayCeylonEasing), initialScale = 0.92f, transformOrigin = transformOrigin),
            exit = fadeOut(tween(110)) +
                scaleOut(tween(140), targetScale = 0.96f, transformOrigin = transformOrigin),
        ) {
            // Padding leaves room for the drop shadow inside the popup window.
            PayCeylonDropdownSurface(
                modifier = modifier
                    .padding(ShadowInset)
                    .width(width),
                content = content,
            )
        }
    }
}

/** Transparent margin around the card inside the popup window so its shadow isn't clipped. */
private val ShadowInset = 12.dp

private class DropdownPositionProvider(
    private val anchor: DropdownAnchor,
    private val gapPx: Int,
    private val insetPx: Int,
) : PopupPositionProvider {
    var opensAbove = false
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Shift by the shadow inset so the visible card, not the popup window, meets the anchor.
        val x = when (anchor) {
            DropdownAnchor.Start -> anchorBounds.left - insetPx
            DropdownAnchor.Center -> anchorBounds.center.x - popupContentSize.width / 2
            DropdownAnchor.End -> anchorBounds.right - popupContentSize.width + insetPx
        }
        val below = anchorBounds.bottom + gapPx - insetPx
        opensAbove = below + popupContentSize.height > windowSize.height
        val y = if (opensAbove) {
            (anchorBounds.top - gapPx + insetPx - popupContentSize.height).coerceAtLeast(0)
        } else {
            below
        }
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y,
        )
    }
}
