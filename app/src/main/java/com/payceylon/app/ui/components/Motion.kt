package com.payceylon.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Decelerating curve shared by PayCeylon entrance and content transitions. */
internal val PayCeylonEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Press feedback: a quick shrink while held, then a soft spring back on release. */
internal val PressSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 700f)

/**
 * Clickable without a ripple that scales down slightly while pressed. Used for every
 * PayCeylon button so taps feel consistent across screens.
 */
internal fun Modifier.bounceClick(
    enabled: Boolean = true,
    role: Role = Role.Button,
    pressedScale: Float = 0.95f,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = PressSpring,
        label = "press scale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/**
 * Fades and lifts content into place the first time it appears. [index] staggers
 * siblings; the "played" flag is saved so items don't replay when scrolled back
 * into a lazy list or after a configuration change.
 */
internal fun Modifier.staggeredEntrance(
    index: Int,
    staggerMillis: Long = 45L,
    initialDelayMillis: Long = 60L,
): Modifier = composed {
    var hasPlayed by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (hasPlayed) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!hasPlayed) {
            delay(initialDelayMillis + index.coerceAtMost(10) * staggerMillis)
            progress.animateTo(1f, tween(durationMillis = 420, easing = PayCeylonEasing))
            hasPlayed = true
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 14.dp.toPx()
    }
}

/** A plain click with no press animation or ripple, for quiet settings-style surfaces. */
internal fun Modifier.tap(role: Role? = null, onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = role,
        onClick = onClick,
    )
}
