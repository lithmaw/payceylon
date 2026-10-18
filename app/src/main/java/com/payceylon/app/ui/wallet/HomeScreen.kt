package com.payceylon.app.ui.wallet

import androidx.compose.animation.core.animate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.payceylon.app.ui.theme.PriceRed
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import com.payceylon.app.ui.account.AppNotification
import com.payceylon.app.ui.account.ProfileAvatar
import com.payceylon.app.ui.account.relativeTime
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.payceylon.app.ui.components.DropdownAnchor
import com.payceylon.app.ui.components.PayCeylonDropdownMenu
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.components.staggeredEntrance
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary
import com.payceylon.app.ui.theme.PriceGreen
import kotlinx.coroutines.delay
import java.math.BigDecimal

@Composable
fun HomeScreen(
    selectedWallet: WalletType,
    selectedToken: CryptoToken,
    balanceOf: (Asset) -> BigDecimal,
    transactions: List<WalletTransaction>,
    /** Last balance the card finished showing, so it can count on from there. */
    lastShownBalance: () -> Pair<Asset, BigDecimal>?,
    onBalanceShown: (Asset, BigDecimal) -> Unit,
    /** True while something covers Home (the wallet switch); the balance waits to count. */
    holdBalanceCount: Boolean,
    onWalletChange: (WalletType) -> Unit,
    onTokenChange: (CryptoToken) -> Unit,
    /** Deposit address for a token: the per-user one from the server once loaded. */
    addressOf: (CryptoToken) -> String,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onSwap: () -> Unit,
    onTopUp: () -> Unit,
    onWithdraw: () -> Unit,
    /** Map of ATMs, banks and PayCeylon agents. */
    onNearby: () -> Unit,
    onBills: () -> Unit,
    onTransactionClick: (WalletTransaction) -> Unit,
    onSeeAllTransactions: () -> Unit,
    /** Profile photo for the header avatar; null shows the default. */
    photo: ImageBitmap?,
    /** Shown as an initial when there is no photo. */
    userName: String,
    /** Something picked in the sidebar (wallets are handled by [onWalletChange]). */
    onDrawerItem: (DrawerItem) -> Unit,
    hideBalanceByDefault: Boolean,
    /** Percentage change in an asset's balance over the last month, or null if unknown. */
    monthlyChangeOf: (Asset) -> BigDecimal?,
    modifier: Modifier = Modifier,
) {
    var isBalanceHidden by rememberSaveable { mutableStateOf(hideBalanceByDefault) }
    val isCrypto = selectedWallet == WalletType.Crypto
    val shownAsset = if (isCrypto) Asset.of(selectedToken) else Asset.LKR
    // Each wallet lists only its own transactions.
    val walletTransactions = transactions.filter { it.belongsTo(selectedWallet) }
    // Everything behind the action bar is captured here so the bar can blur it.
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 0 = collapsed action bar, 1 = expanded with all quick actions.
    // Bounded so the settle spring can't overshoot into a negative sheet height.
    val sheetProgress = remember { Animatable(0f).apply { updateBounds(0f, 1f) } }
    BackHandler(enabled = sheetProgress.targetValue > 0f) {
        scope.launch { sheetProgress.animateTo(0f, SheetSpring) }
    }

    // Exact scroll distance from the top. LazyColumn only reports the first visible
    // item's offset, so the sizes of items that scrolled away are remembered and summed.
    var scrollPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(listState) {
        val itemSizes = HashMap<Int, Int>()
        snapshotFlow { listState.layoutInfo }.collect { info ->
            info.visibleItemsInfo.forEach { itemSizes[it.index] = it.size }
            val first = info.visibleItemsInfo.firstOrNull() ?: return@collect
            val above = (0 until first.index).sumOf { itemSizes[it] ?: 0 }
            scrollPx = (above - first.offset).toFloat().coerceAtLeast(0f)
        }
    }
    // The red backdrop follows the scroll on a soft spring, so it intentionally trails
    // the content by a few pixels and settles just after it.
    val backdropOffset by animateFloatAsState(
        targetValue = -scrollPx,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "backdrop parallax",
    )

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Pull down past the top of the list to switch to the other wallet (as in Binance).
    val otherWallet = if (isCrypto) WalletType.Fiat else WalletType.Crypto
    val density = LocalDensity.current
    val pullThresholdPx = with(density) { PullToSwitchThreshold.toPx() }
    var pullPx by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val pullToSwitch = remember(otherWallet) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Scrolling back up first undoes the pull.
                if (pullPx <= 0f || available.y >= 0f) return Offset.Zero
                val used = maxOf(available.y, -pullPx)
                pullPx += used
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                val before = pullPx
                // Resistance grows as the pull lengthens.
                pullPx += available.y * (0.5f - 0.3f * (pullPx / (pullThresholdPx * 2)).coerceIn(0f, 1f))
                if (before < pullThresholdPx && pullPx >= pullThresholdPx) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullPx <= 0f) return Velocity.Zero
                if (pullPx >= pullThresholdPx) onWalletChange(otherWallet)
                animate(pullPx, 0f, animationSpec = tween(320, easing = PayCeylonEasing)) { value, _ -> pullPx = value }
                return available
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            HomeDrawer(
                selectedWallet = selectedWallet,
                onWalletChange = { wallet ->
                    scope.launch { drawerState.close() }
                    onWalletChange(wallet)
                },
                onItem = { item ->
                    scope.launch { drawerState.close() }
                    onDrawerItem(item)
                },
            )
        },
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalWalletColors.current.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
        // 412 x 700 render of the dithered Figma background; the remainder is WalletBackground.
        LocalWalletColors.current.backdrop?.let { backdrop ->
            Image(
                painter = painterResource(backdrop),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = backdropOffset },
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
            )
        }

        LazyColumn(
            state = listState,
            // Inset (and clipped) below the status bar so cards never scroll under the clock.
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .nestedScroll(pullToSwitch)
                .graphicsLayer { translationY = pullPx },
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                // Leaves room for the floating Send / Receive bar.
                bottom = 150.dp,
            ),
        ) {
            item(key = "top bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(0),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MenuButton(onClick = { scope.launch { drawerState.open() } })
                    ProfileAvatar(
                        photo = photo,
                        name = userName,
                        size = 44.dp,
                        modifier = Modifier.bounceClick(pressedScale = 0.9f) { onDrawerItem(DrawerItem.Settings) },
                    )
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
            item(key = "balance") {
                BalanceCard(
                    asset = shownAsset,
                    balance = balanceOf(shownAsset),
                    hidden = isBalanceHidden,
                    changePercent = monthlyChangeOf(shownAsset),
                    lastShownBalance = lastShownBalance,
                    onBalanceShown = onBalanceShown,
                    holdCount = holdBalanceCount,
                    onToggleHidden = { isBalanceHidden = !isBalanceHidden },
                    modifier = Modifier.staggeredEntrance(1),
                ) {
                    if (isCrypto) {
                        TokenAddressRow(token = selectedToken, address = addressOf(selectedToken), onTokenChange = onTokenChange)
                    } else {
                        FiatShortcutRow(onNearby = onNearby, onBills = onBills)
                    }
                }
            }
            if (isCrypto) {
                item { Spacer(Modifier.height(16.dp)) }
                item(key = "token cards") {
                    TokenCardRow(
                        balanceOf = balanceOf,
                        hidden = isBalanceHidden,
                        onTokenClick = onTokenChange,
                        pagePadding = 20.dp,
                        modifier = Modifier.staggeredEntrance(2),
                    )
                }
            }
            item { Spacer(Modifier.height(35.dp)) }
            item(key = "transactions header") {
                TransactionsHeader(onSeeAll = onSeeAllTransactions, modifier = Modifier.staggeredEntrance(2))
            }
            item { Spacer(Modifier.height(20.dp)) }
            if (walletTransactions.isEmpty()) {
                item(key = "no transactions") {
                    Text(
                        text = "No ${selectedWallet.transactionLabel.lowercase()} transactions yet",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        color = LocalWalletColors.current.contentSecondary,
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            itemsIndexed(walletTransactions, key = { _, transaction -> transaction.id }) { index, transaction ->
                TransactionRow(
                    transaction = transaction,
                    onClick = { onTransactionClick(transaction) },
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = tween(260, easing = PayCeylonEasing),
                            placementSpec = tween(360, easing = PayCeylonEasing),
                        )
                        .staggeredEntrance(3 + index)
                        .padding(bottom = 10.dp),
                )
            }
        }
        }

        PullToSwitchHint(
            target = otherWallet,
            progress = pullPx / pullThresholdPx,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp),
        )

        // Dims the page while the action sheet is open; tapping it closes the sheet.
        if (sheetProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = sheetProgress.value }
                    .background(Color.Black.copy(alpha = 0.28f))
                    .pointerInput(Unit) {
                        detectTapGestures { scope.launch { sheetProgress.animateTo(0f, SheetSpring) } }
                    },
            )
        }

        ActionBar(
            progress = sheetProgress,
            hazeState = hazeState,
            onSend = onSend,
            onReceive = onReceive,
            onSwap = onSwap,
            onTopUp = onTopUp,
            onWithdraw = onWithdraw,
            isCrypto = isCrypto,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }
}

/** How far to pull down past the top of Home to switch wallet. */
private val PullToSwitchThreshold = 96.dp

/** Shown in the gap a pull opens: which wallet a release switches to. */
@Composable
private fun PullToSwitchHint(target: WalletType, progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f) return
    val ready = progress >= 1f
    Row(
        modifier = modifier.graphicsLayer {
            alpha = (progress * 1.4f - 0.2f).coerceIn(0f, 1f)
            translationY = (progress.coerceAtMost(1f) - 1f) * 12.dp.toPx()
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_chevron_up_15),
            contentDescription = null,
            modifier = Modifier
                .size(14.dp)
                .rotate(if (ready) 0f else 180f),
            colorFilter = ColorFilter.tint(LocalWalletColors.current.content),
        )
        Text(
            text = if (ready) "Release for ${target.label}" else "Pull down for ${target.label}",
            color = LocalWalletColors.current.content,
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Opens the sidebar; top left, with the profile button top right. */
@Composable
private fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .frostedSurface(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_menu),
            contentDescription = "Menu",
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(LocalWalletColors.current.content),
        )
    }
}

/**
 * The figure shown on the balance card. A newly shown asset counts up from half its
 * balance; a changed balance (after a payment) counts from the old value to the new one;
 * returning to an unchanged balance shows it as-is.
 */
@Composable
private fun rememberCountingBalance(
    asset: Asset,
    target: BigDecimal,
    lastShownBalance: () -> Pair<Asset, BigDecimal>?,
    onBalanceShown: (Asset, BigDecimal) -> Unit,
    hold: Boolean,
): BigDecimal {
    val start = remember(asset, target) {
        val last = lastShownBalance()
        when {
            last == null || last.first != asset -> target.multiply(BigDecimal("0.5"))
            else -> last.second
        }
    }
    val progress = remember(asset, target) { Animatable(if (start.compareTo(target) == 0) 1f else 0f) }
    LaunchedEffect(asset, target, hold) {
        if (hold) return@LaunchedEffect
        onBalanceShown(asset, target)
        progress.animateTo(1f, tween(durationMillis = 1_100, easing = PayCeylonEasing))
    }
    return start + (target - start) * progress.value.toBigDecimal()
}

@Composable
private fun BalanceCard(
    asset: Asset,
    balance: BigDecimal,
    hidden: Boolean,
    changePercent: BigDecimal?,
    lastShownBalance: () -> Pair<Asset, BigDecimal>?,
    onBalanceShown: (Asset, BigDecimal) -> Unit,
    holdCount: Boolean,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit,
) {
    val isHidden = hidden
    // 0 = figure visible, 1 = hidden. The figure and the mask cross-fade through a blur.
    val hideProgress by animateFloatAsState(
        targetValue = if (hidden) 1f else 0f,
        animationSpec = tween(320, easing = PayCeylonEasing),
        label = "balance hide",
    )
    val shownBalance = rememberCountingBalance(asset, balance, lastShownBalance, onBalanceShown, hold = holdCount)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .dropShadow(
                shape = RoundedCornerShape(20.dp),
                shadow = Shadow(
                    radius = 25.dp,
                    color = Color.Black.copy(alpha = 0.05f),
                    offset = DpOffset(0.dp, 4.dp),
                ),
            )
            .frostedSurface()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(50.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Total Balance",
                    color = LocalWalletColors.current.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Image(
                    painter = painterResource(if (isHidden) R.drawable.ic_eye else R.drawable.ic_eye_off),
                    colorFilter = ColorFilter.tint(LocalWalletColors.current.contentSecondary),
                    contentDescription = if (isHidden) "Show balance" else "Hide balance",
                    modifier = Modifier
                        .bounceClick(pressedScale = 0.85f, onClick = onToggleHidden)
                        .size(14.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${asset.code} ",
                        color = LocalWalletColors.current.contentTertiary,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp,
                    )
                    val (whole, fraction) = shownBalance.toMoneyParts(asset.decimals)
                    // Figure and mask share one slot whose width eases between the two, so the
                    // trend pill glides along instead of jumping.
                    Layout(
                        content = {
                        // Both stay laid out so the trend pill never jumps; the figure is fully
                        // transparent once hidden (blur needs Android 12+; older devices just fade).
                        run {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontSize = 40.sp, letterSpacing = (-2).sp)) { append(whole) }
                                    withStyle(SpanStyle(color = LocalWalletColors.current.contentTertiary, fontSize = 24.sp, letterSpacing = (-1.2).sp)) {
                                        append(fraction)
                                    }
                                },
                                modifier = Modifier
                                    .graphicsLayer { alpha = 1f - hideProgress }
                                    .blur((hideProgress * 14).dp, BlurredEdgeTreatment.Unbounded)
                                    // Room inside the blur layer; without it the blur is cut at the glyph edges.
                                    .padding(BlurBleed),
                                color = LocalWalletColors.current.content,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                // Tabular figures keep the width steady while the number counts.
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                maxLines = 1,
                            )
                        }
                        run {
                            Text(
                                text = "\u2022\u2022\u2022\u2022\u2022\u2022",
                                modifier = Modifier
                                    .graphicsLayer { alpha = hideProgress }
                                    .blur(((1f - hideProgress) * 14).dp, BlurredEdgeTreatment.Unbounded)
                                    .padding(BlurBleed),
                                color = LocalWalletColors.current.content,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 40.sp,
                                letterSpacing = 2.sp,
                                maxLines = 1,
                            )
                        }
                        },
                    ) { measurables, constraints ->
                        // Both children carry BlurBleed padding; lay out by their text size and let
                        // the padding hang outside the slot so nothing shifts.
                        val bleed = BlurBleed.roundToPx()
                        val loose = constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + bleed * 2)
                        val figure = measurables[0].measure(loose)
                        val mask = measurables[1].measure(loose)
                        val figureWidth = figure.width - bleed * 2
                        val maskWidth = mask.width - bleed * 2
                        val width = (figureWidth + (maskWidth - figureWidth) * hideProgress).toInt()
                        val height = maxOf(figure.height, mask.height) - bleed * 2
                        layout(width, height) {
                            figure.placeRelative(-bleed, (height - figure.height) / 2)
                            mask.placeRelative(-bleed, (height - mask.height) / 2)
                        }
                    }
                }
                if (changePercent != null) {
                    val isDown = changePercent.signum() < 0
                    Row(
                        modifier = Modifier
                            .background(if (isDown) PriceRed else PriceGreen, RoundedCornerShape(13.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${if (isDown) "Down" else "Up"} ${changePercent.abs().toPlainString()}% from last month"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "${changePercent.abs().stripTrailingZeros().toPlainString()}%",
                            style = TrimmedText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Image(
                            painter = painterResource(R.drawable.ic_trend_up),
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 7.dp, height = 4.dp)
                                .rotate(if (isDown) 180f else 0f),
                        )
                    }
                }
            }
        }
        footer()
    }
}

/** Fiat footer: shortcuts to the map of ATMs and agents, and to bill payments. */
@Composable
private fun FiatShortcutRow(onNearby: () -> Unit, onBills: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShortcutPill(label = "Nearby", icon = R.drawable.ic_location, onClick = onNearby, modifier = Modifier.weight(1f))
        ShortcutPill(label = "Pay bills", icon = R.drawable.ic_bills, onClick = onBills, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ShortcutPill(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    Row(
        modifier = modifier
            .bounceClick(pressedScale = 0.95f, onClick = onClick)
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.subtle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(colors.content),
        )
        Text(
            text = label,
            color = colors.content,
            fontFamily = InterFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TransactionsHeader(onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Transactions",
            style = TrimmedText,
            color = LocalWalletColors.current.content,
            fontSize = SectionHeadingSize,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "See all",
            modifier = Modifier.bounceClick(pressedScale = 0.92f, onClick = onSeeAll),
            style = TrimmedText,
            color = LocalWalletColors.current.contentSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun TransactionRow(
    transaction: WalletTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOutgoing = transaction.direction.isDebit
    val isSwap = transaction.direction == TransactionDirection.Swap
    // Swaps headline what arrived.
    val shownCurrency = if (isSwap) transaction.toCurrency.orEmpty() else transaction.currency
    val shownAmount = if (isSwap) transaction.toAmount ?: BigDecimal.ZERO else transaction.amount
    val (whole, fraction) = shownAmount.toAmountParts(shownCurrency)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(LocalWalletColors.current.card)
            .padding(start = 10.dp, end = 15.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TransactionIcon(transaction, size = 50.dp, glyphSize = 24.dp, background = LocalWalletColors.current.subtle)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Long names end in "…" at the space available, so the row never wraps.
                Text(
                    text = transaction.title,
                    style = TrimmedText,
                    color = LocalWalletColors.current.content,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = when {
                            isSwap -> "${transaction.currency} \u2192 ${transaction.toCurrency}"
                            // Top-ups, withdrawals and bills show the masked account or account number.
                            transaction.direction.isExternal || transaction.direction == TransactionDirection.Bill ->
                                transaction.counterpartyHandle
                            else -> "@${transaction.counterpartyHandle}"
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        style = TrimmedText,
                        color = LocalWalletColors.current.contentSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_dot_separator),
                        colorFilter = ColorFilter.tint(LocalWalletColors.current.contentSecondary),
                        contentDescription = null,
                        modifier = Modifier.size(2.5.dp),
                    )
                    Text(
                        text = transaction.wallet.transactionLabel,
                        style = TrimmedText,
                        color = LocalWalletColors.current.contentSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(start = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${if (isOutgoing) "-" else "+"}$shownCurrency $whole$fraction",
                style = TrimmedText,
                color = if (isOutgoing) LocalWalletColors.current.priceDown else LocalWalletColors.current.priceUp,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            // A withdrawal waiting on the bank shows "Pending" until it settles.
            val isPending = transaction.status == "PENDING"
            Text(
                text = if (isPending) "Pending" else relativeTime(transaction.timestamp),
                style = TrimmedText,
                color = if (isPending) PendingAmber else LocalWalletColors.current.contentSecondary,
                fontSize = 12.sp,
                fontWeight = if (isPending) FontWeight.SemiBold else null,
            )
        }
    }
}

/** Crypto footer: the selected token's deposit address (tap to copy) and the token picker. */
@Composable
private fun TokenAddressRow(
    token: CryptoToken,
    address: String,
    onTokenChange: (CryptoToken) -> Unit,
) {
    val colors = LocalWalletColors.current
    val context = LocalContext.current
    var isMenuOpen by remember { mutableStateOf(false) }
    var showCopied by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 0f else 180f,
        animationSpec = tween(220, easing = PayCeylonEasing),
        label = "token chevron",
    )
    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(1_500)
            showCopied = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .bounceClick(pressedScale = 0.97f) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("${token.symbol} address", address))
                    showCopied = true
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${token.symbol} Address · ${token.network}",
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (showCopied) "Copied" else shortAddress(address),
                    color = colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Image(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = "Copy address",
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(colors.contentSecondary),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box {
            Row(
                modifier = Modifier
                    .bounceClick(role = Role.DropdownList, pressedScale = 0.95f) { isMenuOpen = true }
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.subtle)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Image(
                    painter = painterResource(token.icon),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = token.symbol,
                    color = colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Image(
                    painter = painterResource(R.drawable.ic_chevron_up_small),
                    contentDescription = "Change token",
                    modifier = Modifier
                        .size(width = 9.5.dp, height = 5.5.dp)
                        .rotate(chevronRotation),
                    colorFilter = ColorFilter.tint(colors.content),
                )
            }
            PayCeylonDropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                options = CryptoToken.entries,
                selected = token,
                onSelect = onTokenChange,
                label = { it.tokenName },
                supportingText = { "${it.symbol} · ${it.network}" },
                leading = { TokenBadge(it, size = 36) },
                anchor = DropdownAnchor.End,
                width = 220.dp,
            )
        }
    }
}

private val SheetSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)

/** Space around the balance figure for its hide/show blur to spread into (the 14dp blur needs ~2x). */
private val BlurBleed = 16.dp

/** "Pending" on transactions still settling; readable on both the light and dark palettes. */
internal val PendingAmber = Color(0xFFE39B1B)

private data class ExtraAction(
    val label: String,
    @DrawableRes val icon: Int,
)

/**
 * Top Up and Withdraw move LKR to and from cards and banks, so they are fiat-only; crypto is
 * bought and sold by swapping with LKR.
 */
private fun extraActions(isCrypto: Boolean) = if (isCrypto) {
    listOf(ExtraAction("Swap", R.drawable.ic_exchange))
} else {
    listOf(
        ExtraAction("Top Up", R.drawable.ic_top_up),
        ExtraAction("Swap", R.drawable.ic_exchange),
        ExtraAction("Withdraw", R.drawable.ic_withdraw),
    )
}

@Composable
private fun ActionBar(
    progress: Animatable<Float, AnimationVector1D>,
    hazeState: HazeState,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onSwap: () -> Unit,
    onTopUp: () -> Unit,
    onWithdraw: () -> Unit,
    isCrypto: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val colors = LocalWalletColors.current
    // Glass tint over the blurred backdrop; thickens a little as the sheet opens so the
    // extra actions stay readable over busy content.
    val glassTint = if (colors.isDark) colors.card else Color.White
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    var optionsHeightPx by remember { mutableIntStateOf(1) }

    fun settle(target: Float, velocity: Float = 0f) {
        scope.launch { progress.animateTo(target, SheetSpring, initialVelocity = velocity) }
    }

    // Slides up from the bottom edge once, shortly after the screen appears.
    var hasEntered by rememberSaveable { mutableStateOf(false) }
    val entrance = remember { Animatable(if (hasEntered) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!hasEntered) {
            delay(180)
            entrance.animateTo(1f, tween(480, easing = PayCeylonEasing))
            hasEntered = true
        }
    }

    // Dragging the bar (from the notch or anywhere on it) maps finger travel 1:1 onto
    // the height of the extra actions; release settles open or closed.
    val dragState = rememberDraggableState { delta ->
        scope.launch {
            progress.snapTo((progress.value - delta / optionsHeightPx).coerceIn(0f, 1f))
        }
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                translationY = (1f - entrance.value) * size.height
                alpha = 0.4f + 0.6f * entrance.value
            }
            .fillMaxWidth()
            .clip(sheetShape)
            .hazeEffect(state = hazeState) {
                blurRadius = 28.dp
                backgroundColor = colors.background
                noiseFactor = 0.06f
                tints = if (colors.isDark) {
                    listOf(HazeTint(glassTint.copy(alpha = 0.35f + 0.25f * progress.value)))
                } else {
                    // The blurred content isn't dimmed by the page scrim, so the glass carries the
                    // same dim (plus a thin white frost) to read as glass rather than a white panel.
                    listOf(
                        HazeTint(Color.Black.copy(alpha = 0.28f * progress.value)),
                        HazeTint(glassTint.copy(alpha = 0.18f)),
                    )
                }
            }
            .border(1.dp, colors.frostedBorder.copy(alpha = if (colors.isDark) 0.08f else 0.6f), sheetShape)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    val target = when {
                        velocity < -600f -> 1f
                        velocity > 600f -> 0f
                        progress.value > 0.5f -> 1f
                        else -> 0f
                    }
                    settle(target, velocity = -velocity / optionsHeightPx)
                },
            )
            .navigationBarsPadding()
            .padding(start = 15.dp, end = 15.dp, bottom = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Notch: generous touch target around the 60 x 5 Figma handle; tap toggles.
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 40.dp)
                .bounceClick(pressedScale = 0.9f) {
                    settle(if (progress.targetValue > 0.5f) 0f else 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = 1f - 0.25f * progress.value }
                    .size(width = 60.dp, height = 5.dp)
                    .background(
                        LocalWalletColors.current.content.copy(alpha = 0.1f + 0.1f * progress.value),
                        RoundedCornerShape(21.dp),
                    ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillActionButton(
                label = "Send",
                icon = R.drawable.ic_action_arrow_down_left,
                iconRotation = 180f,
                onClick = onSend,
                modifier = Modifier.weight(1f),
            )
            PillActionButton(
                label = "Receive",
                icon = R.drawable.ic_action_arrow_down_left,
                iconRotation = 0f,
                onClick = onReceive,
                modifier = Modifier.weight(1f),
            )
        }

        // Extra actions unfold below Send / Receive, pushing them up as the sheet opens.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
                    )
                    val height = (placeable.height * progress.value).roundToInt()
                    layout(placeable.width, height) { placeable.place(0, 0) }
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { optionsHeightPx = it.height.coerceAtLeast(1) }
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                extraActions(isCrypto).chunked(2).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    ) {
                        row.forEachIndexed { columnIndex, action ->
                            val order = rowIndex * 2 + columnIndex
                            PillActionButton(
                                label = action.label,
                                icon = action.icon,
                                iconRotation = 0f,
                                secondary = true,
                                onClick = {
                                    settle(0f)
                                    when (action.label) {
                                        "Swap" -> onSwap()
                                        "Top Up" -> onTopUp()
                                        "Withdraw" -> onWithdraw()
                                        else -> Unit
                                    }
                                },
                                // Pairs match Send / Receive; a lone action spans the whole row.
                                modifier = Modifier
                                    .then(if (row.size == 1) Modifier.fillMaxWidth() else Modifier.weight(1f))
                                    .graphicsLayer {
                                        // Pills fade and lift in, slightly staggered, as the sheet opens.
                                        val r = ((progress.value - order * 0.08f) / 0.75f).coerceIn(0f, 1f)
                                        alpha = r
                                        translationY = (1f - r) * 14.dp.toPx()
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillActionButton(
    label: String,
    @DrawableRes icon: Int,
    iconRotation: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
) {
    val colors = LocalWalletColors.current
    // On the fiat glass sheet the secondary pills are frosted white so they lift off the blur.
    val background = when {
        !secondary -> colors.action
        colors.isDark -> colors.subtle
        else -> Color.White.copy(alpha = 0.45f)
    }
    val content = if (secondary) colors.content else colors.onAction
    Row(
        modifier = modifier
            .bounceClick(pressedScale = 0.95f, onClick = onClick)
            .height(60.dp)
            .clip(RoundedCornerShape(62.dp))
            .background(background)
            .padding(horizontal = 25.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .rotate(iconRotation),
            colorFilter = ColorFilter.tint(content),
        )
        Text(
            text = label,
            color = content,
            fontFamily = InterFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.32).sp,
        )
    }
}
