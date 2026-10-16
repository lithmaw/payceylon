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
import com.payceylon.app.data.ApiException
import com.payceylon.app.data.PhoneAuth
import com.payceylon.app.ui.components.rememberSubmitState

private enum class AuthDestination { Onboarding, Otp }

/** Phone sign-in: the server issues a one-time code, and a confirmed code signs the user in. */
@Composable
fun PayCeylonAuthFlow(onAuthenticated: () -> Unit) {
    var destination by rememberSaveable { mutableStateOf(AuthDestination.Onboarding) }
    var onboardingPage by rememberSaveable { mutableIntStateOf(0) }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmitState()
    val e164 = "+94${normalizeSriLankanMobileNumber(phoneNumber)}"

    /** Has the server issue a code for the entered number (read from the server log). */
    fun requestCode() = submit.run {
        PhoneAuth.requestCode(e164)
        destination = AuthDestination.Otp
    }

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
                onFinished = { requestCode() },
                isSendingCode = submit.busy,
            )

            AuthDestination.Otp -> OtpScreen(
                maskedPhoneNumber = maskSriLankanMobileNumber(phoneNumber),
                onBack = {
                    destination = AuthDestination.Onboarding
                    onboardingPage = 2
                },
                onCodeComplete = { code ->
                    try {
                        PhoneAuth.signIn(e164, code)
                        onAuthenticated()
                        null
                    } catch (e: ApiException) {
                        e.message
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "Something went wrong. Please try again."
                    }
                },
                onResend = { requestCode() },
                // Signing in and signing up are the same phone verification.
                onLogIn = {
                    destination = AuthDestination.Onboarding
                    onboardingPage = 2
                },
            )
        }
    }
}
