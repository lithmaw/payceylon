package com.payceylon.app.ui.wallet

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.payceylon.app.R
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.theme.CryptoWalletColors
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.PayCeylonPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val GlyphSize = 76.dp

/**
 * Full-screen wallet switch. A soft-edged gradient wipe in the current wallet's colour
 * sweeps down the screen, the current wallet's glyph appears, then morphs (scale, turn and
 * blur crossfade) into the other glyph while a second wipe in the other palette follows.
 * The app re-themes underneath and the overlay dissolves.
 *
 * [onCovered] fires once the screen is fully covered — the moment to swap the palette.
 */
@Composable
fun WalletSwitchOverlay(
    target: WalletType,
    onCovered: () -> Unit,
    onFinished: () -> Unit,
) {
    val toCrypto = target == WalletType.Crypto
    val light = Color.White
    val dark = CryptoWalletColors.background
    val washColor = if (toCrypto) light else dark
    val floodColor = if (toCrypto) dark else light
    val washCaption = if (toCrypto) Color.Black else Color.White
    val floodCaption = if (toCrypto) Color.White else Color.Black
    val source = if (toCrypto) WalletType.Fiat else WalletType.Crypto

    val wash = remember { Animatable(0f) }
    val appear = remember { Animatable(0f) }
    val morph = remember { Animatable(0f) }
    val flood = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    val haptics = LocalHapticFeedback.current
    val currentOnCovered by rememberUpdatedState(onCovered)
    val currentOnFinished by rememberUpdatedState(onFinished)

    // Input and Back are swallowed while the transition runs.
    BackHandler { }

    LaunchedEffect(Unit) {
        launch { wash.animateTo(1f, tween(460, easing = PayCeylonEasing)) }
        delay(160)
        appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f))
        delay(80)
        launch {
            morph.animateTo(0.5f, tween(330, easing = FastOutSlowInEasing))
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            morph.animateTo(1f, tween(330, easing = FastOutSlowInEasing))
        }
        delay(140)
        flood.animateTo(1f, tween(640, easing = PayCeylonEasing))
        currentOnCovered()
        delay(240)
        exit.animateTo(1f, tween(420, easing = PayCeylonEasing))
        currentOnFinished()
    }

    // Status bar icons flip with the flood rather than waiting for the palette swap.
    val view = LocalView.current
    LaunchedEffect(view) {
        snapshotFlow { flood.value > 0.2f }
            .map { flooded -> if (flooded) floodColor == light else washColor == light }
            .distinctUntilChanged()
            .collect { lightBackground ->
                val window = (view.context as? Activity)?.window ?: return@collect
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightBackground
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val featherPx = heightPx * 0.45f

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Each wipe is a solid block with a long gradient leading edge; its edge travels
            // from just above the screen to the bottom, so it fully covers at progress 1.
            fun wipe(color: Color, progress: Float) {
                if (progress <= 0f) return
                val edge = -featherPx + progress * (heightPx + featherPx)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0f)),
                        startY = edge,
                        endY = edge + featherPx,
                    ),
                    size = Size(widthPx, (edge + featherPx).coerceAtMost(heightPx)),
                )
            }
            wipe(washColor, wash.value)
            wipe(floodColor, flood.value)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Balances the caption so the glyph sits at the exact centre of the flood.
            Spacer(Modifier.height(34.dp))
            Box(
                modifier = Modifier.graphicsLayer {
                    val enter = 0.7f + 0.3f * appear.value
                    val leave = 1f + 0.12f * exit.value
                    scaleX = enter * leave
                    scaleY = enter * leave
                    alpha = appear.value.coerceIn(0f, 1f)
                },
                contentAlignment = Alignment.Center,
            ) {
                // Outgoing glyph shrinks, turns and blurs away as the incoming one resolves.
                MorphGlyph(icon = glyphFor(source), progress = 1f - morph.value, turn = -36f)
                MorphGlyph(icon = glyphFor(target), progress = morph.value, turn = 36f)
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) },
                contentAlignment = Alignment.Center,
            ) {
                val captionColor = lerp(washCaption, floodCaption, ((flood.value - 0.45f) / 0.35f).coerceIn(0f, 1f))
                    .copy(alpha = 0.6f)
                Caption(source.label, captionColor, visibility = 1f - morph.value)
                Caption(target.label, captionColor, visibility = morph.value)
            }
        }
    }
}

@DrawableRes
private fun glyphFor(wallet: WalletType): Int =
    if (wallet == WalletType.Crypto) R.drawable.ic_bitcoin else R.drawable.ic_payceylon_mark

/** One side of the morph; [progress] 1 = fully resolved, 0 = gone. */
@Composable
private fun MorphGlyph(@DrawableRes icon: Int, progress: Float, turn: Float) {
    val p = progress.coerceIn(0f, 1f)
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier
            .size(GlyphSize)
            .graphicsLayer {
                alpha = p
                val scale = 0.6f + 0.4f * p
                scaleX = scale
                scaleY = scale
                rotationZ = turn * (1f - p)
            }
            .blur(10.dp * (1f - p)),
        colorFilter = ColorFilter.tint(PayCeylonPrimary),
    )
}

@Composable
private fun Caption(text: String, color: Color, visibility: Float) {
    val v = visibility.coerceIn(0f, 1f)
    Text(
        text = text,
        modifier = Modifier.graphicsLayer {
            alpha = v
            translationY = (1f - v) * 6.dp.toPx()
        },
        color = color,
        fontFamily = InterFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    )
}
