package com.payceylon.app.ui.wallet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.auth.PayCeylonButton
import com.payceylon.app.ui.components.PayCeylonDropdownItem
import com.payceylon.app.ui.components.PayCeylonDropdownSurface
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.components.staggeredEntrance
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.PayCeylonPrimary

@Composable
fun SendToUsernameScreen(
    recentRecipients: List<PayCeylonUser>,
    onBack: () -> Unit,
    onRecipientSelected: (handle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) { searchPayCeylonUsers(query, limit = 4) }
    val exactMatch = remember(query) { findPayCeylonUser(query.trim()) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val fieldInteraction = remember { MutableInteractionSource() }
    val isFocused by fieldInteraction.collectIsFocusedAsState()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun select(user: PayCeylonUser) {
        focusManager.clearFocus()
        onRecipientSelected(user.handle)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalWalletColors.current.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
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
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(30.dp))
            Text(
                text = buildAnnotatedString {
                    append("Send to a\n")
                    withStyle(SpanStyle(color = PayCeylonPrimary)) { append("username") }
                    append(".")
                },
                modifier = Modifier.staggeredEntrance(0),
                color = LocalWalletColors.current.content,
                fontFamily = InterFontFamily,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.2).sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Search anyone on PayCeylon by name or @username.",
                modifier = Modifier.staggeredEntrance(1),
                color = LocalWalletColors.current.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(28.dp))

            UsernameField(
                value = query,
                onValueChange = { query = it.filterNot(Char::isWhitespace).take(31) },
                isFocused = isFocused,
                interactionSource = fieldInteraction,
                onDone = { exactMatch?.let(::select) },
                modifier = Modifier
                    .staggeredEntrance(2)
                    .focusRequester(focusRequester),
            )

            // Suggestions drop down under the field while typing; recents show when it is empty.
            AnimatedVisibility(
                visible = query.isNotBlank(),
                enter = fadeIn(tween(160)) + expandVertically(tween(260, easing = PayCeylonEasing)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(200, easing = PayCeylonEasing)),
            ) {
                PayCeylonDropdownSurface(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                ) {
                    AnimatedContent(
                        targetState = matches,
                        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                        label = "username suggestions",
                    ) { results ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (results.isEmpty()) {
                                Text(
                                    text = "No PayCeylon user matches “${query.trim().removePrefix("@")}”",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                    color = LocalWalletColors.current.contentSecondary,
                                    fontFamily = InterFontFamily,
                                    fontSize = 14.sp,
                                )
                            }
                            results.forEach { user ->
                                PayCeylonDropdownItem(
                                    text = user.name,
                                    supportingText = "@${user.handle}",
                                    selected = user == exactMatch,
                                    leading = { UserAvatar(user, size = 40) },
                                    onClick = { select(user) },
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = query.isBlank() && recentRecipients.isNotEmpty(),
                enter = fadeIn(tween(200, delayMillis = 80)),
                exit = fadeOut(tween(100)),
            ) {
                Column(modifier = Modifier.padding(top = 28.dp)) {
                    Text(
                        text = "Recent",
                        modifier = Modifier.staggeredEntrance(3),
                        color = LocalWalletColors.current.content,
                        fontFamily = InterFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        recentRecipients.take(5).forEachIndexed { index, user ->
                            Column(
                                modifier = Modifier
                                    .width(56.dp)
                                    .staggeredEntrance(4 + index)
                                    .bounceClick(pressedScale = 0.92f) { select(user) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                UserAvatar(user, size = 52)
                                Text(
                                    text = user.name.substringBefore(' '),
                                    color = LocalWalletColors.current.content,
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        PayCeylonButton(
            label = exactMatch?.let { "Continue to @${it.handle}" } ?: "Continue",
            onClick = { exactMatch?.let(::select) },
            enabled = exactMatch != null,
        )
    }
}

@Composable
private fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    isFocused: Boolean,
    interactionSource: MutableInteractionSource,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) PayCeylonPrimary else Color.Transparent,
        animationSpec = tween(180),
        label = "username field border",
    )
    val shape = RoundedCornerShape(31.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = TextStyle(
            color = LocalWalletColors.current.content,
            fontFamily = InterFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
        cursorBrush = SolidColor(PayCeylonPrimary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .background(LocalWalletColors.current.card, shape)
                    .border(1.dp, borderColor, shape)
                    .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_search),
                    colorFilter = ColorFilter.tint(LocalWalletColors.current.contentSecondary),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "@",
                    color = LocalWalletColors.current.contentTertiary,
                    fontFamily = InterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "username or name",
                            color = LocalWalletColors.current.contentTertiary,
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    innerTextField()
                }
                AnimatedVisibility(
                    visible = value.isNotEmpty(),
                    enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.6f),
                    exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.6f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(LocalWalletColors.current.content.copy(alpha = 0.35f), CircleShape)
                            .bounceClick(pressedScale = 0.88f) { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_close_small),
                            colorFilter = ColorFilter.tint(LocalWalletColors.current.page),
                            contentDescription = "Clear",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
    )
}
