package com.payceylon.app.ui.wallet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.auth.PayCeylonButton
import com.payceylon.app.ui.components.DropdownAnchor
import com.payceylon.app.ui.components.PayCeylonDropdownMenu
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.components.PressSpring
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.components.rememberSubmitState
import com.payceylon.app.ui.components.staggeredEntrance
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.PayCeylonPrimary
import com.payceylon.app.ui.theme.PriceGreen
import com.payceylon.app.ui.theme.PriceRed
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID

private const val MaxWholeDigits = 9

@Composable
fun SendMoneyScreen(
    recipientHandle: String,
    initialAsset: Asset,
    /** Keypad-format amount to start with, e.g. from a scanned payment request. */
    initialAmount: String = "",
    balanceOf: (Asset) -> BigDecimal,
    /** Sends the money; throws ApiException when rejected. Retries reuse [requestKey]. */
    onSend: suspend (amount: BigDecimal, asset: Asset, requestKey: String) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var amountInput by rememberSaveable { mutableStateOf(initialAmount) }
    var asset by rememberSaveable { mutableStateOf(initialAsset) }
    var sentAmount by rememberSaveable { mutableStateOf<String?>(null) }
    val requestKey = rememberSaveable { UUID.randomUUID().toString() }
    val submit = rememberSubmitState()

    val amount = amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val balance = balanceOf(asset)
    // Token transfers also pay the network fee from the same balance.
    val networkFee = WalletFees.networkFee(asset)
    val exceedsBalance = amount + (networkFee ?: BigDecimal.ZERO) > balance

    AnimatedContent(
        targetState = sentAmount,
        transitionSpec = {
            (fadeIn(tween(260)) + scaleIn(tween(320, easing = PayCeylonEasing), initialScale = 0.96f)) togetherWith
                fadeOut(tween(160))
        },
        label = "send money result",
        modifier = modifier,
    ) { sent ->
        if (sent != null) {
            SendSuccess(
                amountLabel = "${asset.code} ${BigDecimal(sent).toAmountString(asset)}",
                recipientHandle = recipientHandle,
                onDone = onDone,
            )
            return@AnimatedContent
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalWalletColors.current.page)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                CircleBackButton(onClick = onBack, background = LocalWalletColors.current.card, iconTint = LocalWalletColors.current.content)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                Row(
                    modifier = Modifier.staggeredEntrance(0),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "Sending to",
                        color = LocalWalletColors.current.content,
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "@$recipientHandle",
                        modifier = Modifier
                            .background(PayCeylonPrimary, RoundedCornerShape(27.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                AmountDisplay(
                    currency = asset.code,
                    input = amountInput,
                    isOverBalance = exceedsBalance,
                    modifier = Modifier.staggeredEntrance(1),
                )
                AssetPicker(
                    selected = asset,
                    onSelect = { selected ->
                        asset = selected
                        // Drop decimals the new asset can't hold (e.g. BTC 0.0005 -> LKR).
                        amountInput = amountInput.limitDecimals(selected.decimals)
                    },
                    modifier = Modifier.staggeredEntrance(2),
                )
                Text(
                    text = if (exceedsBalance) {
                        "Insufficient balance"
                    } else {
                        "Available: ${asset.code} ${balance.toAmountString(asset)}" +
                            (networkFee?.let { " \u00B7 Network fee ${asset.code} ${it.toPlainString()}" } ?: "")
                    },
                    modifier = Modifier.staggeredEntrance(3),
                    color = if (exceedsBalance) LocalWalletColors.current.priceDown else LocalWalletColors.current.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            AmountKeypad(
                onKey = { key -> amountInput = appendAmountKey(amountInput, key, asset.decimals) },
                onDelete = { amountInput = amountInput.dropLast(1) },
                modifier = Modifier.staggeredEntrance(4),
            )
            Spacer(Modifier.height(20.dp))
            PayCeylonButton(
                label = if (submit.busy) "Sending…" else "Send",
                onClick = {
                    submit.run {
                        onSend(amount, asset, requestKey)
                        sentAmount = amount.toPlainString()
                    }
                },
                enabled = amount > BigDecimal.ZERO && !exceedsBalance && !submit.busy,
                modifier = Modifier.staggeredEntrance(5),
            )
        }
    }
}

/** Applies a keypad press, allowing at most [maxDecimals] decimal places and [MaxWholeDigits] whole digits. */
internal fun appendAmountKey(current: String, key: Char, maxDecimals: Int = 2): String {
    if (key == '.') {
        return when {
            '.' in current -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
    }
    val dot = current.indexOf('.')
    if (dot >= 0) {
        return if (current.length - dot - 1 >= maxDecimals) current else current + key
    }
    if (current == "0") return key.toString()
    return if (current.length >= MaxWholeDigits) current else current + key
}

/** Trims keypad input to [decimals] places ("0.0005" with 2 -> "0.00"). */
internal fun String.limitDecimals(decimals: Int): String {
    val dot = indexOf('.')
    return if (dot < 0 || length - dot - 1 <= decimals) this else substring(0, dot + 1 + decimals)
}

/** Converts a requested amount to keypad input ("1500", "12.5"), or "" if it can't be typed. */
internal fun BigDecimal.toKeypadInput(decimals: Int = 2): String {
    val plain = setScale(decimals, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
    return if (plain.substringBefore('.').length > MaxWholeDigits) "" else plain
}

private val wholeFormat = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))

@Composable
internal fun AmountDisplay(
    currency: String,
    input: String,
    isOverBalance: Boolean,
    modifier: Modifier = Modifier,
) {
    val wholeDigits = input.substringBefore('.').ifEmpty { "0" }
    val typedDecimals = if ('.' in input) input.substringAfter('.') else ""
    val whole = wholeFormat.format(wholeDigits.toBigDecimal())
    // Long amounts shrink so the figure always fits on one line.
    val targetSize = when {
        whole.length > 9 -> 46f
        whole.length > 6 -> 58f
        else -> 74f
    }
    val wholeSize by animateFloatAsState(
        targetValue = targetSize,
        animationSpec = tween(220, easing = PayCeylonEasing),
        label = "amount size",
    )
    val wholeColor by animateColorAsState(
        targetValue = if (isOverBalance) LocalWalletColors.current.priceDown else LocalWalletColors.current.content,
        animationSpec = tween(200),
        label = "amount color",
    )
    // A small "tick" on every keypress so input feels responsive.
    val bump = remember { Animatable(1f) }
    LaunchedEffect(input) {
        if (input.isNotEmpty()) {
            bump.snapTo(1.035f)
            bump.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f))
        }
    }
    val tertiary = LocalWalletColors.current.contentTertiary
    fun amountText(fit: Float) = buildAnnotatedString {
        withStyle(SpanStyle(color = tertiary, fontSize = (36 * fit).sp)) { append("$currency ") }
        withStyle(SpanStyle(color = wholeColor, fontSize = (wholeSize * fit).sp, letterSpacing = (-0.05f * wholeSize * fit).sp)) {
            append(whole)
        }
        withStyle(SpanStyle(color = tertiary, fontSize = (36 * fit).sp, letterSpacing = (-1.8f * fit).sp)) {
            append(".")
            withStyle(SpanStyle(color = if (typedDecimals.isNotEmpty()) wholeColor else tertiary)) {
                append(typedDecimals)
            }
            append("0".repeat((2 - typedDecimals.length).coerceAtLeast(0)))
        }
    }
    // Own style (not the theme's 24sp line height) so the line is as tall as the big glyphs.
    val style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val naturalWidth = measurer.measure(amountText(1f), style, softWrap = false, maxLines = 1).size.width
        // Scale the whole figure down when it's still wider than the screen, so it never wraps or clips.
        val fit = if (naturalWidth > constraints.maxWidth) constraints.maxWidth / naturalWidth.toFloat() else 1f
        Text(
            text = amountText(fit),
            modifier = Modifier.scale(bump.value),
            style = style,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun AssetPicker(
    selected: Asset,
    onSelect: (Asset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 0f else 180f,
        animationSpec = tween(220, easing = PayCeylonEasing),
        label = "asset picker chevron",
    )
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .bounceClick(role = Role.DropdownList) { isMenuOpen = true }
                .clip(RoundedCornerShape(10.dp))
                .background(LocalWalletColors.current.card)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AssetIcon(selected)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = selected.displayName,
                    color = LocalWalletColors.current.content,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Image(
                    painter = painterResource(R.drawable.ic_chevron_up_small),
                    colorFilter = ColorFilter.tint(LocalWalletColors.current.content),
                    contentDescription = "Change asset",
                    modifier = Modifier
                        .size(width = 9.5.dp, height = 5.5.dp)
                        .rotate(chevronRotation),
                )
            }
        }
        PayCeylonDropdownMenu(
            expanded = isMenuOpen,
            onDismissRequest = { isMenuOpen = false },
            options = Asset.entries,
            selected = selected,
            onSelect = onSelect,
            label = { it.displayName },
            supportingText = { it.detail },
            leading = { AssetBadge(it) },
            anchor = DropdownAnchor.Center,
        )
    }
}

@Composable
internal fun AmountKeypad(
    onKey: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf("123", "456", "789", ".0<")
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        onClick = { if (key == '<') onDelete() else onKey(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    key: Char,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (isPressed) LocalWalletColors.current.subtle else Color.Transparent,
        animationSpec = tween(if (isPressed) 60 else 260),
        label = "key background",
    )
    val contentScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = PressSpring,
        label = "key content scale",
    )
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .bounceClick(
                pressedScale = 0.96f,
                interactionSource = interactionSource,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
            },
        ) {
            if (key == '<') {
                Image(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    colorFilter = ColorFilter.tint(LocalWalletColors.current.content),
                    contentDescription = "Delete",
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = key.toString(),
                    color = LocalWalletColors.current.content,
                    fontFamily = InterFontFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SendSuccess(
    amountLabel: String,
    recipientHandle: String,
    onDone: () -> Unit,
) {
    // The badge springs in with a light overshoot, then the copy follows.
    val badgeScale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        delay(120)
        badgeScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalWalletColors.current.page)
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
                    .graphicsLayer {
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                        alpha = ((badgeScale.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    }
                    .background(PriceGreen, CircleShape),
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
                text = amountLabel,
                modifier = Modifier.staggeredEntrance(0, initialDelayMillis = 260),
                color = LocalWalletColors.current.content,
                fontFamily = InterFontFamily,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.staggeredEntrance(1, initialDelayMillis = 260),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "sent to",
                    color = LocalWalletColors.current.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "@$recipientHandle",
                    color = PayCeylonPrimary,
                    fontFamily = InterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        PayCeylonButton(
            label = "Done",
            onClick = onDone,
            modifier = Modifier.staggeredEntrance(2, initialDelayMillis = 260),
        )
    }
}
