package com.payceylon.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset

private enum class AuthDestination { Onboarding, Otp }

/** Demo-only verification code until Firebase phone auth is connected. */
internal const val DemoOtpCode = "123456"

@Composable
fun PayCeylonAuthFlow(onAuthenticated: (phoneNumber: String) -> Unit) {
    var destination by rememberSaveable { mutableStateOf(AuthDestination.Onboarding) }
    var onboardingPage by rememberSaveable { mutableIntStateOf(0) }
    var phoneNumber by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = destination == AuthDestination.Otp) {
        destination = AuthDestination.Onboarding
        onboardingPage = 2
    }
    BackHandler(
        enabled = destination == AuthDestination.Onboarding && onboardingPage > 0,
    ) {
        onboardingPage--
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            val slideSpec = spring<IntOffset>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
            if (targetState == AuthDestination.Otp) {
                (slideInHorizontally(slideSpec) { it } + fadeIn(tween(160))) togetherWith
                    (slideOutHorizontally(slideSpec) { -it / 3 } + fadeOut(tween(120)))
            } else {
                (slideInHorizontally(slideSpec) { -it / 3 } + fadeIn(tween(160))) togetherWith
                    (slideOutHorizontally(slideSpec) { it } + fadeOut(tween(120)))
            }
        },
        label = "authentication navigation",
    ) { target ->
        when (target) {
            AuthDestination.Onboarding -> OnboardingScreen(
                currentPage = onboardingPage,
                phoneNumber = phoneNumber,
                onPhoneNumberChange = { phoneNumber = it },
                onPageChange = { onboardingPage = it },
                onFinished = { destination = AuthDestination.Otp },
            )

            AuthDestination.Otp -> OtpScreen(
                maskedPhoneNumber = maskSriLankanMobileNumber(phoneNumber),
                onBack = {
                    destination = AuthDestination.Onboarding
                    onboardingPage = 2
                },
                // Replace with Firebase/remote OTP verification.
                onCodeComplete = { code ->
                    (code == DemoOtpCode).also { accepted -> if (accepted) onAuthenticated(phoneNumber) }
                },
                onLogIn = { /* Existing-account authentication entry point. */ },
            )
        }
    }
}
