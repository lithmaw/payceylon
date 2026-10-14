package com.payceylon.app.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.account.ProfileAvatar
import com.payceylon.app.ui.components.DropdownAnchor
import com.payceylon.app.ui.components.PayCeylonDropdownMenu
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.components.PayCeylonQrCode
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.components.staggeredEntrance
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.PayCeylonPrimary
import kotlinx.coroutines.delay
import java.math.BigDecimal

/** Accepts up to 9 whole digits and as many decimals as [decimals] allows. */
private fun amountInputPattern(decimals: Int) = Regex("^\\d{0,9}(\\.\\d{0,$decimals})?$")

@Composable
fun ReceiveScreen(
    user: PayCeylonUser,
    photo: ImageBitmap?,
    initialAsset: Asset,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var asset by rememberSaveable { mutableStateOf(initialAsset) }
    // What's being typed vs. what the QR currently asks for; the QR only changes on Done.
    var amountInput by rememberSaveable { mutableStateOf("") }
    var requestedAmount by rememberSaveable { mutableStateOf("") }
    var isEditingAmount by rememberSaveable { mutableStateOf(false) }
    var showCopied by remember { mutableStateOf(false) }

    val amount = requestedAmount.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    val request = PaymentRequest(handle = user.handle, amount = amount, asset = asset)
    val qrContent = request.toUri()

    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(1_600)
            showCopied = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalWalletColors.current.page),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CircleBackButton(onClick = onBack, background = LocalWalletColors.current.card, iconTint = LocalWalletColors.current.content)
                ReceiveAssetChip(
                    selected = asset,
                    onSelect = { selected ->
                        asset = selected
                        amountInput = amountInput.limitDecimals(selected.decimals)
                        requestedAmount = requestedAmount.limitDecimals(selected.decimals)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .staggeredEntrance(0)
                    .fillMaxWidth()
                    .dropShadow(
                        shape = RoundedCornerShape(28.dp),
                        shadow = Shadow(
                            radius = 30.dp,
                            color = Color.Black.copy(alpha = 0.08f),
                            offset = DpOffset(0.dp, 8.dp),
                        ),
                    )
                    .clip(RoundedCornerShape(28.dp))
                .background(LocalWalletColors.current.card)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileAvatar(photo, size = 60.dp, name = user.name)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = user.name,
                    color = LocalWalletColors.current.content,
                    fontFamily = InterFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                // Tapping the handle copies it; the pill briefly confirms.
                Text(
                    text = if (showCopied) "Copied" else "@${user.handle}",
                    modifier = Modifier
                        .bounceClick(pressedScale = 0.94f) {
                            copyToClipboard(context, "@${user.handle}")
                            showCopied = true
                        }
                        .clip(RoundedCornerShape(27.dp))
                        .background(PayCeylonPrimary)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(22.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(1.dp, LocalWalletColors.current.subtle, RoundedCornerShape(24.dp))
                        .padding(18.dp),
                ) {
                    AnimatedContent(
                        targetState = qrContent,
                        transitionSpec = {
                            (fadeIn(tween(240)) + scaleIn(tween(320, easing = PayCeylonEasing), initialScale = 0.94f)) togetherWith
                                (fadeOut(tween(140)) + scaleOut(tween(200), targetScale = 1.03f))
                        },
                        label = "receive qr",
                    ) { content ->
                        PayCeylonQrCode(
                            content = content,
                            modifier = Modifier.size(220.dp),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                AnimatedContent(
                    targetState = amount,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "request caption",
                ) { requested ->
                    if (requested == null) {
                        Text(
                            text = "Scan with PayCeylon to pay me instantly",
                            color = LocalWalletColors.current.contentSecondary,
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Requesting",
                                color = LocalWalletColors.current.contentSecondary,
                                fontFamily = InterFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${asset.code} ${requested.toAmountString(asset)}",
                                color = LocalWalletColors.current.content,
                                fontFamily = InterFontFamily,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.8).sp,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isEditingAmount,
                enter = fadeIn(tween(180)) + expandVertically(tween(280, easing = PayCeylonEasing)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(220, easing = PayCeylonEasing)),
            ) {
                AmountRequestField(
                    currency = asset.code,
                    value = amountInput,
                    onValueChange = { if (it.matches(amountInputPattern(asset.decimals))) amountInput = it },
                    onDone = {
                        requestedAmount = amountInput
                        isEditingAmount = false
                    },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .staggeredEntrance(1)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val hasAmount = amount != null || isEditingAmount
                // The plus turns into a cross when an amount is set, signalling "clear".
                val iconRotation by animateFloatAsState(
                    targetValue = if (hasAmount) 45f else 0f,
                    animationSpec = tween(260, easing = PayCeylonEasing),
                    label = "amount icon rotation",
                )
                ReceiveActionButton(
                    label = if (hasAmount) "Clear amount" else "Set amount",
                    icon = R.drawable.ic_amount,
                    iconRotation = iconRotation,
                    onClick = {
                        if (hasAmount) {
                            amountInput = ""
                            requestedAmount = ""
                            isEditingAmount = false
                        } else {
                            isEditingAmount = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                ReceiveActionButton(
                    label = "Share",
                    icon = R.drawable.ic_share,
                    onClick = { shareRequest(context, user, request) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReceiveAssetChip(selected: Asset, onSelect: (Asset) -> Unit) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 0f else 180f,
        animationSpec = tween(220, easing = PayCeylonEasing),
        label = "receive asset chevron",
    )
    Box {
        Row(
            modifier = Modifier
                .bounceClick(role = Role.DropdownList) { isMenuOpen = true }
                .clip(RoundedCornerShape(20.dp))
                .background(LocalWalletColors.current.card)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssetIcon(selected)
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

@Composable
private fun AmountRequestField(
    currency: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) PayCeylonPrimary else LocalWalletColors.current.frostedBorder,
        animationSpec = tween(180),
        label = "amount field border",
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val shape = RoundedCornerShape(31.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = TextStyle(
            color = LocalWalletColors.current.content,
            fontFamily = InterFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        cursorBrush = SolidColor(PayCeylonPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
            onDone()
        }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(shape)
                    .background(LocalWalletColors.current.card)
                    .border(1.dp, borderColor, shape)
                    .padding(start = 22.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currency,
                    color = LocalWalletColors.current.contentTertiary,
                    fontFamily = InterFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "0.00",
                            color = LocalWalletColors.current.contentTertiary,
                            fontFamily = InterFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    innerTextField()
                }
                Box(
                    modifier = Modifier
                        .bounceClick(pressedScale = 0.92f) {
                            focusManager.clearFocus()
                            onDone()
                        }
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(LocalWalletColors.current.action)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Done",
                        color = LocalWalletColors.current.onAction,
                        fontFamily = InterFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    )
}

@Composable
private fun ReceiveActionButton(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f,
) {
    Row(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .height(60.dp)
            .clip(RoundedCornerShape(62.dp))
            .background(LocalWalletColors.current.action)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .rotate(iconRotation),
            colorFilter = ColorFilter.tint(LocalWalletColors.current.onAction),
        )
        Text(
            text = label,
            color = LocalWalletColors.current.onAction,
            fontFamily = InterFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.32).sp,
            maxLines = 1,
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("PayCeylon username", text))
}

private fun shareRequest(context: Context, user: PayCeylonUser, request: PaymentRequest) {
    val asset = request.asset ?: Asset.LKR
    val amountLine = request.amount?.let { " ${asset.code} ${it.toAmountString(asset)}" }.orEmpty()
    val text = "Pay me$amountLine on PayCeylon: @${user.handle}\n${request.toUri()}"
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Share payment request"))
}
