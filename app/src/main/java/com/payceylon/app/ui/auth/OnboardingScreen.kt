package com.payceylon.app.ui.auth

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.theme.Ink
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.MutedText
import com.payceylon.app.ui.theme.PayCeylonRed
import com.payceylon.app.ui.theme.SoftGrey
import kotlin.math.abs

private data class OnboardingPage(
    val title: AnnotatedString,
    val description: String,
    @param:DrawableRes val background: Int,
    @param:DrawableRes val illustration: Int,
)

private val onboardingPages = listOf(
    OnboardingPage(
        title = buildAnnotatedString {
            append("Top up with\nyour ")
            withStyle(SpanStyle(color = PayCeylonRed)) { append("cards") }
            append(".")
        },
        description = "Add money to your PayCeylon wallet instantly using your linked debit or credit cards.",
        background = R.drawable.onboarding_bg_1,
        illustration = R.drawable.onboarding_art_1,
    ),
    OnboardingPage(
        title = buildAnnotatedString {
            append("From ")
            withStyle(SpanStyle(color = PayCeylonRed)) { append("rupees") }
            append("\nto ")
            withStyle(SpanStyle(color = PayCeylonRed)) { append("crypto") }
            append(".")
        },
        description = "Convert your rupees to crypto and back whenever you want, all without leaving PayCeylon.",
        background = R.drawable.onboarding_bg_2,
        illustration = R.drawable.onboarding_art_2,
    ),
    OnboardingPage(
        title = buildAnnotatedString {
            withStyle(SpanStyle(color = PayCeylonRed)) { append("Cash out") }
            append(" when\nyou want.")
        },
        description = "Withdraw your PayCeylon balance directly to your linked bank account whenever you need it.",
        background = R.drawable.onboarding_bg_3,
        illustration = R.drawable.onboarding_art_3,
    ),
)

@Composable
fun OnboardingScreen(
    currentPage: Int,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** True while the SMS code is being requested. */
    isSendingCode: Boolean = false,
) {
    var displayedPage by remember { mutableIntStateOf(currentPage.coerceIn(onboardingPages.indices)) }
    var transitionDirection by remember { mutableIntStateOf(1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }

    LaunchedEffect(currentPage) {
        val target = currentPage.coerceIn(onboardingPages.indices)
        if (target != displayedPage) {
            transitionDirection = if (target > displayedPage) 1 else -1
            displayedPage = target
        }
    }

    fun goToPage(targetPage: Int) {
        val target = targetPage.coerceIn(onboardingPages.indices)
        if (target != displayedPage) {
            transitionDirection = if (target > displayedPage) 1 else -1
            onPageChange(target)
        }
    }

    // While the keyboard is up (phone entry) the illustration folds away so the field stays visible,
    // and the background rises with the content so its red top doesn't run into the title.
    // The IME inset animates with the keyboard, so both follow it frame by frame.
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    val isKeyboardOpen = keyboardHeight > 0
    val isPhonePage = displayedPage == onboardingPages.lastIndex

    val draggableState = rememberDraggableState { delta ->
        val isPastStart = displayedPage == 0 && dragOffset + delta > 0f
        val isPastEnd = displayedPage == onboardingPages.lastIndex && dragOffset + delta < 0f
        dragOffset += if (isPastStart || isPastEnd) delta * 0.28f else delta
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedOnboardingBackground(
            pageIndex = displayedPage,
            liftPx = if (isPhonePage) keyboardHeight.toFloat() else 0f,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            // The red background has moved up out of the way, so the bars switch to ink.
            ProgressIndicator(currentPage = displayedPage, onLight = isPhonePage && isKeyboardOpen)

            OnboardingCenterContent(
                pageIndex = displayedPage,
                transitionDirection = transitionDirection,
                dragOffset = dragOffset,
                showIllustration = !(isPhonePage && isKeyboardOpen),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        // The last page holds the phone field; swiping there only got in the way.
                        enabled = !isPhonePage,
                        onDragStopped = { velocity ->
                            val projectedOffset = dragOffset + velocity * 0.12f
                            when {
                                projectedOffset < -swipeThreshold && displayedPage < onboardingPages.lastIndex -> {
                                    goToPage(displayedPage + 1)
                                }
                            }
                            animate(
                                initialValue = dragOffset,
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            ) { value, _ -> dragOffset = value }
                        },
                    ),
            )

            AnimatedContent(
                targetState = displayedPage == onboardingPages.lastIndex,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 4 }) togetherWith
                        (fadeOut(tween(140)) + slideOutVertically(tween(220)) { it / 5 })
                },
                label = "onboarding controls",
            ) { isPhonePage ->
                if (isPhonePage) {
                    PhoneEntryControls(
                        value = phoneNumber,
                        onValueChange = onPhoneNumberChange,
                        onContinue = onFinished,
                        isSendingCode = isSendingCode,
                    )
                } else {
                    StandardOnboardingControls(
                        onContinue = { goToPage(displayedPage + 1) },
                        onSkip = { goToPage(onboardingPages.lastIndex) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AnimatedOnboardingBackground(pageIndex: Int, liftPx: Float = 0f) {
    var activePage by remember { mutableIntStateOf(pageIndex) }
    var previousPage by remember { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(pageIndex) {
        if (pageIndex != activePage) {
            previousPage = activePage
            activePage = pageIndex
            progress.snapTo(0f)
            progress.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
            previousPage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .graphicsLayer { translationY = -liftPx },
    ) {
        val previous = previousPage
        if (previous == null) {
            BackgroundImage(page = activePage)
        } else if (activePage == 0) {
            BackgroundImage(page = previous)
            BackgroundImage(
                page = activePage,
                alpha = progress.value,
                translationY = (1f - progress.value) * -42f,
            )
        } else {
            // Render the destination first, then animate the previous background away.
            // This guarantees an opaque frame while revealing the second and third pages.
            BackgroundImage(page = activePage)
            BackgroundImage(
                page = previous,
                alpha = 1f - progress.value,
                translationY = if (activePage == onboardingPages.lastIndex) {
                    progress.value * -42f
                } else {
                    0f
                },
            )
        }
    }
}

@Composable
private fun BackgroundImage(
    page: Int,
    alpha: Float = 1f,
    translationY: Float = 0f,
) {
    Image(
        painter = painterResource(onboardingPages[page].background),
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translationY
            },
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun OnboardingCenterContent(
    pageIndex: Int,
    transitionDirection: Int,
    dragOffset: Float,
    showIllustration: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.graphicsLayer {
            translationX = dragOffset
            alpha = (1f - abs(dragOffset) / 1_800f).coerceIn(0.78f, 1f)
        },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        AnimatedVisibility(
            visible = showIllustration,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enter = fadeIn(tween(200)) + expandVertically(tween(260)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(220)),
        ) {
            KeyedRevealElement(
                pageIndex = pageIndex,
                transitionDirection = transitionDirection,
                delayMillis = 0,
            ) { page ->
                Image(
                    painter = painterResource(onboardingPages[page].illustration),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp)
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        KeyedRevealElement(
            pageIndex = pageIndex,
            transitionDirection = transitionDirection,
            delayMillis = 45,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Text(
                text = onboardingPages[page].title,
                color = Ink,
                fontFamily = InterFontFamily,
                fontSize = 42.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.6).sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        KeyedRevealElement(
            pageIndex = pageIndex,
            transitionDirection = transitionDirection,
            delayMillis = 85,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Text(
                text = onboardingPages[page].description,
                color = MutedText,
                fontFamily = InterFontFamily,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun KeyedRevealElement(
    pageIndex: Int,
    transitionDirection: Int,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    key(pageIndex) {
        var revealed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { revealed = true }
        val revealProgress by animateFloatAsState(
            targetValue = if (revealed) 1f else 0f,
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing,
            ),
            label = "onboarding element reveal",
        )
        Box(
            modifier = modifier.graphicsLayer {
                alpha = revealProgress
                translationX = (1f - revealProgress) * transitionDirection * 28f
            },
        ) {
            content(pageIndex)
        }
    }
}

@Composable
private fun ProgressIndicator(currentPage: Int, onLight: Boolean) {
    var animateProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateProgress = true }
    val barColor by animateColorAsState(if (onLight) Ink else Color.White, tween(220), label = "progress bar color")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(onboardingPages.size) { index ->
            val progress by animateFloatAsState(
                targetValue = if (animateProgress && index <= currentPage) 1f else 0f,
                animationSpec = tween(480, easing = FastOutSlowInEasing),
                label = "onboarding progress $index",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(barColor.copy(alpha = if (onLight) 0.12f else 0.28f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(barColor, RoundedCornerShape(99.dp)),
                )
            }
        }
    }
}

@Composable
private fun StandardOnboardingControls(onContinue: () -> Unit, onSkip: () -> Unit) {
    Column {
        PayCeylonButton(label = "Continue", onClick = onContinue)
        Spacer(Modifier.height(12.dp))
        PayCeylonButton(
            label = "Skip",
            onClick = onSkip,
            style = PayCeylonButtonStyle.Secondary,
            height = 58.dp,
        )
    }
}

@Composable
private fun PhoneEntryControls(
    value: String,
    onValueChange: (String) -> Unit,
    onContinue: () -> Unit,
    isSendingCode: Boolean,
) {
    val isValid = validateSriLankanMobileNumber(value) == PhoneNumberValidation.Valid

    Column {
        PhoneNumberField(
            value = value,
            onValueChange = onValueChange,
            onDone = { if (isValid && !isSendingCode) onContinue() },
        )
        Spacer(Modifier.height(14.dp))
        PayCeylonButton(
            label = if (isSendingCode) "Sending code…" else "Get Started",
            onClick = onContinue,
            style = PayCeylonButtonStyle.Accent,
            enabled = isValid && !isSendingCode,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "By continuing, you agree to our terms of service & privacy policy.",
            modifier = Modifier.fillMaxWidth(),
            color = MutedText,
            fontFamily = InterFontFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PhoneNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Keeps the cursor where the user put it, so a mistyped digit can be fixed in place.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val current = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))

    BasicTextField(
        value = current,
        onValueChange = { newValue ->
            val digits = normalizeSriLankanMobileNumber(newValue.text)
            fieldValue = if (digits == newValue.text) {
                newValue
            } else {
                newValue.copy(text = digits, selection = TextRange(newValue.selection.end.coerceAtMost(digits.length)))
            }
            onValueChange(digits)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(SoftGrey)
            // Taps on the flag or +94 focus the field too, not just taps on the digits.
            .clickable(interactionSource = null, indication = null) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
            .focusRequester(focusRequester)
            .padding(horizontal = 20.dp),
        textStyle = TextStyle(
            color = Ink,
            fontFamily = InterFontFamily,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        ),
        singleLine = true,
        visualTransformation = SriLankanPhoneVisualTransformation,
        cursorBrush = SolidColor(PayCeylonRed),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_sri_lanka),
                    contentDescription = "Sri Lanka",
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "+94",
                    color = Ink,
                    fontFamily = InterFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "70 123 4567",
                            color = MutedText,
                            fontFamily = InterFontFamily,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}
