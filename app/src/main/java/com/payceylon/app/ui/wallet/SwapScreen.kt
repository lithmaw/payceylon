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
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.auth.PayCeylonButton
import com.payceylon.app.ui.components.DropdownAnchor
import com.payceylon.app.ui.components.PayCeylonDropdownMenu
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.components.staggeredEntrance
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.components.rememberSubmitState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

@Composable
fun SwapScreen(
    initialFrom: Asset,
    balanceOf: (Asset) -> BigDecimal,
    /** Performs the swap and returns the amount received; throws ApiException when rejected. */
    onSwap: suspend (from: Asset, to: Asset, amount: BigDecimal, requestKey: String) -> BigDecimal,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    val requestKey = rememberSaveable { UUID.randomUUID().toString() }
    val submit = rememberSubmitState()
    var from by rememberSaveable { mutableStateOf(initialFrom) }
    var to by rememberSaveable { mutableStateOf(defaultSwapTarget(initialFrom)) }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var flipTurns by rememberSaveable { mutableFloatStateOf(0f) }

    val amount = amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val balance = balanceOf(from)
    val exceedsBalance = amount > balance
    val quote = if (amount > BigDecimal.ZERO) SwapRates.quote(from, to, amount) else BigDecimal.ZERO
    val canSwap = amount > BigDecimal.ZERO && !exceedsBalance && quote > BigDecimal.ZERO

    fun choose(side: Boolean, asset: Asset) {
        // Picking the asset already on the other side swaps the two around.
        if (side) {
            if (asset == to) to = from
            from = asset
        } else {
            if (asset == from) from = to
            to = asset
        }
        amountInput = amountInput.limitDecimals(from.decimals)
    }

    AnimatedContent(
        targetState = result,
        transitionSpec = {
            (fadeIn(tween(260)) + scaleIn(tween(320, easing = PayCeylonEasing), initialScale = 0.96f)) togetherWith
                fadeOut(tween(160))
        },
        label = "swap result",
        modifier = modifier,
    ) { received ->
        if (received != null) {
            SwapSuccess(
                paid = "${from.code} ${amount.toAmountString(from)}",
                received = "+${to.code} ${BigDecimal(received).toAmountString(to)}",
                onDone = onDone,
            )
            return@AnimatedContent
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.page)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                CircleBackButton(onClick = onBack, background = colors.card, iconTint = colors.content)
                Text(
                    text = "Swap",
                    modifier = Modifier.align(Alignment.Center),
                    color = colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .staggeredEntrance(0)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwapCard(
                        label = "From",
                        asset = from,
                        amountText = amountInput.ifEmpty { "0" }.toDisplayAmount(),
                        isPlaceholder = amountInput.isEmpty(),
                        isError = exceedsBalance,
                        balance = balance,
                        onMax = { amountInput = balance.toKeypadInput(from.decimals) },
                        onAssetChange = { choose(side = true, asset = it) },
                    )
                    SwapCard(
                        label = "To",
                        asset = to,
                        amountText = if (quote > BigDecimal.ZERO) quote.toAmountString(to) else "0",
                        isPlaceholder = quote <= BigDecimal.ZERO,
                        isError = false,
                        balance = balanceOf(to),
                        onMax = null,
                        onAssetChange = { choose(side = false, asset = it) },
                    )
                }
                FlipButton(
                    turns = flipTurns,
                    onClick = {
                        flipTurns += 0.5f
                        val oldFrom = from
                        from = to
                        to = oldFrom
                        amountInput = amountInput.limitDecimals(from.decimals)
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = if (exceedsBalance) {
                    "Insufficient ${from.code} balance"
                } else {
                    "1 ${from.code} = ${SwapRates.rate(from, to).toRateString(to)} ${to.code}  ·  Fee 0.5%"
                },
                modifier = Modifier
                    .staggeredEntrance(1)
                    .fillMaxWidth(),
                color = if (exceedsBalance) colors.priceDown else colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))
            AmountKeypad(
                onKey = { key -> amountInput = appendAmountKey(amountInput, key, from.decimals) },
                onDelete = { amountInput = amountInput.dropLast(1) },
                modifier = Modifier.staggeredEntrance(2),
            )
            Spacer(Modifier.height(20.dp))
            PayCeylonButton(
                label = if (submit.busy) "Swapping…" else "Swap ${from.code} to ${to.code}",
                onClick = {
                    submit.run { result = onSwap(from, to, amount, requestKey).toPlainString() }
                },
                enabled = canSwap && !submit.busy,
                modifier = Modifier.staggeredEntrance(3),
            )
        }
    }
}

internal fun defaultSwapTarget(from: Asset): Asset = when (from) {
    Asset.USDT -> Asset.BTC
    else -> Asset.USDT
}

/** Groups the whole part of keypad input for display ("12500.5" -> "12,500.5"). */
private fun String.toDisplayAmount(): String {
    val whole = substringBefore('.').ifEmpty { "0" }
    val grouped = whole.toBigDecimal().toMoneyParts(0).first
    return if ('.' in this) grouped + "." + substringAfter('.') else grouped
}

@Composable
private fun SwapCard(
    label: String,
    asset: Asset,
    amountText: String,
    isPlaceholder: Boolean,
    isError: Boolean,
    balance: BigDecimal,
    onMax: (() -> Unit)?,
    onAssetChange: (Asset) -> Unit,
) {
    val colors = LocalWalletColors.current
    val amountColor by animateColorAsState(
        targetValue = when {
            isError -> colors.priceDown
            isPlaceholder -> colors.contentTertiary
            else -> colors.content
        },
        animationSpec = tween(180),
        label = "swap amount colour",
    )
    // Quick "tick" whenever the figure changes, like the Send Money amount.
    val bump = remember { Animatable(1f) }
    LaunchedEffect(amountText) {
        if (!isPlaceholder) {
            bump.snapTo(1.03f)
            bump.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Balance: ${balance.toAmountString(asset)}",
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (onMax != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Max",
                    modifier = Modifier
                        .bounceClick(pressedScale = 0.9f, onClick = onMax)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.action)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    color = colors.onAction,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shrinks for long figures so they never clip against the asset chip.
            val stepSize = when {
                amountText.length > 13 -> 20f
                amountText.length > 9 -> 26f
                else -> 34f
            }
            BoxWithConstraints(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                val measurer = rememberTextMeasurer()
                fun amountStyle(size: Float) = TextStyle(
                    color = amountColor,
                    fontFamily = InterFontFamily,
                    fontSize = size.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-size / 34f).sp,
                    fontFeatureSettings = "tnum",
                )
                val naturalWidth = measurer.measure(amountText, amountStyle(stepSize), softWrap = false, maxLines = 1).size.width
                // Long BTC/ETH figures can still overflow the smallest step; scale them down to fit on one line.
                val fit = if (naturalWidth > constraints.maxWidth) constraints.maxWidth / naturalWidth.toFloat() else 1f
                Text(
                    text = amountText,
                    modifier = Modifier.graphicsLayer {
                        scaleX = bump.value
                        scaleY = bump.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    },
                    style = amountStyle(stepSize * fit),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.width(12.dp))
            SwapAssetChip(selected = asset, onSelect = onAssetChange)
        }
    }
}

@Composable
private fun SwapAssetChip(selected: Asset, onSelect: (Asset) -> Unit) {
    val colors = LocalWalletColors.current
    var isMenuOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 0f else 180f,
        animationSpec = tween(220, easing = PayCeylonEasing),
        label = "swap chip chevron",
    )
    Box {
        Row(
            modifier = Modifier
                .bounceClick(role = Role.DropdownList, pressedScale = 0.95f) { isMenuOpen = true }
                .clip(RoundedCornerShape(20.dp))
                .background(colors.subtle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AssetIcon(selected)
            Text(
                text = selected.code,
                color = colors.content,
                fontFamily = InterFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Image(
                painter = painterResource(R.drawable.ic_chevron_up_small),
                contentDescription = "Change asset",
                modifier = Modifier
                    .size(width = 9.5.dp, height = 5.5.dp)
                    .rotate(chevronRotation),
                colorFilter = ColorFilter.tint(colors.content),
            )
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
            anchor = DropdownAnchor.End,
            width = 220.dp,
        )
    }
}

/** Round button between the cards; spins half a turn each time the pair is flipped. */
@Composable
private fun FlipButton(turns: Float, onClick: () -> Unit) {
    val colors = LocalWalletColors.current
    val rotation by animateFloatAsState(
        targetValue = turns * 360f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "flip rotation",
    )
    Box(
        modifier = Modifier
            .bounceClick(pressedScale = 0.88f, onClick = onClick)
            .size(48.dp)
            .border(5.dp, colors.page, CircleShape)
            .padding(5.dp)
            .background(colors.action, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_exchange),
            contentDescription = "Flip assets",
            modifier = Modifier
                .size(18.dp)
                .rotate(90f + rotation),
            colorFilter = ColorFilter.tint(colors.onAction),
        )
    }
}

@Composable
private fun SwapSuccess(paid: String, received: String, onDone: () -> Unit) {
    val colors = LocalWalletColors.current
    val badgeScale = remember { Animatable(0.4f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        delay(120)
        scope.launch { badgeScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
    }
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
                    .graphicsLayer {
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                        alpha = ((badgeScale.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    }
                    .background(colors.priceUp, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.Color.White),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = received,
                modifier = Modifier.staggeredEntrance(0, initialDelayMillis = 260),
                color = colors.content,
                fontFamily = InterFontFamily,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Swapped from $paid",
                modifier = Modifier.staggeredEntrance(1, initialDelayMillis = 260),
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        PayCeylonButton(
            label = "Done",
            onClick = onDone,
            modifier = Modifier.staggeredEntrance(2, initialDelayMillis = 260),
        )
    }
}
