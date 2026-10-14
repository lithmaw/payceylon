package com.payceylon.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.theme.Ink
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.MutedText
import com.payceylon.app.ui.theme.PayCeylonRed
import com.payceylon.app.ui.theme.SoftGrey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OtpScreen(
    maskedPhoneNumber: String,
    onBack: () -> Unit,
    /** Checks the code; returns why it was rejected (the code is then cleared), or null. */
    onCodeComplete: suspend (String) -> String?,
    /** Texts a new code. */
    onResend: () -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var secondsRemaining by rememberSaveable { mutableIntStateOf(30) }
    var isCodeRejected by rememberSaveable { mutableStateOf(false) }
    var rejection by rememberSaveable { mutableStateOf("That code didn't match. Try again.") }
    var isChecking by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(secondsRemaining) {
        if (secondsRemaining > 0) {
            delay(1_000)
            secondsRemaining--
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(26.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(SoftGrey, CircleShape)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = buildAnnotatedString {
                append("We just sent you\nan ")
                withStyle(SpanStyle(color = PayCeylonRed)) { append("SMS") }
                append(".")
            },
            color = Ink,
            fontSize = 42.sp,
            lineHeight = 43.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1.5).sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Enter the security code we sent to $maskedPhoneNumber",
            color = MutedText,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(44.dp))

        BasicTextField(
            value = code,
            onValueChange = { newValue ->
                if (isChecking) return@BasicTextField
                val digits = newValue.filter(Char::isDigit).take(6)
                code = digits
                if (digits.isNotEmpty()) isCodeRejected = false
                if (digits.length == 6) scope.launch {
                    isChecking = true
                    val problem = onCodeComplete(digits)
                    isChecking = false
                    if (problem != null) {
                        rejection = problem
                        isCodeRejected = true
                        shakeOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = keyframes {
                                durationMillis = 360
                                -14f at 60
                                12f at 120
                                -8f at 180
                                6f at 240
                                -3f at 300
                            },
                        )
                        code = ""
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(shakeOffset.value.dp.roundToPx(), 0) }
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent, fontFamily = InterFontFamily),
            cursorBrush = SolidColor(Color.Transparent),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    innerTextField()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(6) { index ->
                            val isFilled = index < code.length
                            val isActive = index == code.length && code.length < 6
                            val slotBackground by animateColorAsState(
                                targetValue = when {
                                    isFilled && isCodeRejected -> Ink
                                    isFilled -> PayCeylonRed
                                    isActive -> Color(0xFFF8F8F8)
                                    else -> SoftGrey
                                },
                                animationSpec = tween(180),
                                label = "OTP slot background $index",
                            )
                            val slotBorder by animateColorAsState(
                                targetValue = if (isActive) PayCeylonRed else Color.Transparent,
                                animationSpec = tween(160),
                                label = "OTP slot border $index",
                            )
                            val digitColor by animateColorAsState(
                                targetValue = if (isFilled) Color.White else PayCeylonRed,
                                animationSpec = tween(140),
                                label = "OTP digit color $index",
                            )
                            val slotScale by animateFloatAsState(
                                targetValue = if (isFilled) 1f else 0.96f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                label = "OTP slot scale $index",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .graphicsLayer {
                                        scaleX = slotScale
                                        scaleY = slotScale
                                    }
                                    .background(
                                        color = slotBackground,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = slotBorder,
                                        shape = RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = code.getOrNull(index)?.toString().orEmpty(),
                                    color = digitColor,
                                    fontFamily = InterFontFamily,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            },
        )

        AnimatedVisibility(visible = isChecking) {
            Text(
                text = "Checking code…",
                modifier = Modifier.padding(top = 14.dp),
                color = MutedText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        AnimatedVisibility(visible = isCodeRejected) {
            Text(
                text = rejection,
                modifier = Modifier.padding(top = 14.dp),
                color = PayCeylonRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(22.dp))
        PayCeylonButton(
            label = if (secondsRemaining > 0) {
                "Resend code in 00:${secondsRemaining.toString().padStart(2, '0')}"
            } else {
                "Resend code"
            },
            onClick = {
                if (secondsRemaining == 0) {
                    secondsRemaining = 30
                    code = ""
                    isCodeRejected = false
                    onResend()
                    focusRequester.requestFocus()
                }
            },
            style = PayCeylonButtonStyle.Secondary,
            enabled = secondsRemaining == 0,
            height = 54.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Already have an account? Log In",
            modifier = Modifier.clickable(onClick = onLogIn),
            color = PayCeylonRed,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
    }
}
