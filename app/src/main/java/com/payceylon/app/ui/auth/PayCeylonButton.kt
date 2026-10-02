package com.payceylon.app.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.ui.components.PressSpring
import com.payceylon.app.ui.theme.Ink
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.MutedText
import com.payceylon.app.ui.theme.PayCeylonRed
import com.payceylon.app.ui.theme.SoftGrey

internal enum class PayCeylonButtonStyle { Primary, Accent, Secondary }

@Composable
internal fun PayCeylonButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PayCeylonButtonStyle = PayCeylonButtonStyle.Primary,
    enabled: Boolean = true,
    height: Dp = 62.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = PressSpring,
        label = "button press scale",
    )
    // Follows the wallet palette (defaults to fiat, so onboarding/OTP are unchanged).
    val walletColors = LocalWalletColors.current
    val restingColor = when (style) {
        PayCeylonButtonStyle.Primary -> walletColors.action
        PayCeylonButtonStyle.Accent -> PayCeylonRed
        PayCeylonButtonStyle.Secondary -> if (walletColors.isDark) walletColors.subtle else SoftGrey
    }
    val pressedColor = when (style) {
        PayCeylonButtonStyle.Primary -> walletColors.actionPressed
        PayCeylonButtonStyle.Accent -> Color(0xFFC9003E)
        PayCeylonButtonStyle.Secondary -> if (walletColors.isDark) walletColors.content.copy(alpha = 0.14f) else Color(0xFFE3E3E5)
    }
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> walletColors.disabled
            isPressed -> pressedColor
            else -> restingColor
        },
        animationSpec = tween(if (isPressed) 80 else 220),
        label = "button press color",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> walletColors.onDisabled
            style == PayCeylonButtonStyle.Secondary -> if (walletColors.isDark) walletColors.content else Ink
            else -> Color.White
        },
        animationSpec = tween(220),
        label = "button content color",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(32.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontFamily = InterFontFamily,
            fontSize = 16.sp,
            fontWeight = if (style == PayCeylonButtonStyle.Secondary) {
                FontWeight.Medium
            } else {
                FontWeight.SemiBold
            },
        )
    }
}
