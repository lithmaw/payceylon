package com.payceylon.app.ui.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.theme.CryptoWalletColors
import com.payceylon.app.ui.theme.FiatWalletColors
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.PayCeylonPrimary
import kotlinx.coroutines.delay

/**
 * Wallet switch: the screen fades to the other wallet's background with a small loader (its
 * glyph inside a spinning ring and its name), the app re-themes underneath, then the loader
 * fades away to reveal the other wallet.
 *
 * [onCovered] fires once the screen is fully covered — the moment to swap the palette.
 */
@Composable
fun WalletSwitchOverlay(
    target: WalletType,
    onCovered: () -> Unit,
    onFinished: () -> Unit,
) {
    val palette = if (target == WalletType.Crypto) CryptoWalletColors else FiatWalletColors
    val cover = remember { Animatable(0f) }
    val currentOnCovered by rememberUpdatedState(onCovered)
    val currentOnFinished by rememberUpdatedState(onFinished)

    // Input and Back are swallowed while the transition runs.
    BackHandler { }

    LaunchedEffect(Unit) {
        cover.animateTo(1f, tween(280, easing = PayCeylonEasing))
        // The app re-themes (and flips the status bar icons) while it is hidden.
        currentOnCovered()
        delay(420)
        cover.animateTo(0f, tween(360, easing = PayCeylonEasing))
        currentOnFinished()
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = cover.value }
            .background(palette.background)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                val scale = 0.92f + 0.08f * cover.value
                scaleX = scale
                scaleY = scale
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                SpinnerRing()
                Image(
                    painter = painterResource(if (target == WalletType.Crypto) R.drawable.ic_bitcoin else R.drawable.ic_payceylon_mark),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    colorFilter = ColorFilter.tint(PayCeylonPrimary),
                )
            }
            Text(
                text = target.label,
                color = palette.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
            )
        }
    }
}

/** A short arc turning around a faint track. */
@Composable
private fun SpinnerRing() {
    val spin by rememberInfiniteTransition(label = "wallet switch").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "spinner",
    )
    Canvas(Modifier.size(60.dp)) {
        val stroke = 2.5.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(PayCeylonPrimary.copy(alpha = 0.15f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(PayCeylonPrimary, spin - 90f, 90f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}