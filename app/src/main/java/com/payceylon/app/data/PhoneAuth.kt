package com.payceylon.app.data

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result of asking Firebase to verify a phone number. */
sealed interface CodeRequest {
    /** An SMS code is on its way; confirm it with [PhoneAuth.credential]. */
    data class Sent(val verificationId: String, val resendToken: PhoneAuthProvider.ForceResendingToken) : CodeRequest

    /** Android verified the number without a code (instant verification). */
    data class Verified(val credential: PhoneAuthCredential) : CodeRequest
}

/** Firebase phone-number verification, as suspend calls. */
object PhoneAuth {
    private val auth: FirebaseAuth
        get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()
            ?: throw ApiException(0, "NOT_CONFIGURED", "Firebase isn't set up in this build. Add it to local.properties.")

    /** Texts a code to [phone] (E.164, e.g. +94771234567). */
    suspend fun requestCode(
        activity: Activity,
        phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    ): CodeRequest = suspendCancellableCoroutine { continuation ->
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                if (continuation.isActive) continuation.resume(CodeRequest.Sent(verificationId, token))
            }

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Also fires after onCodeSent when the SMS is auto-read; the typed code still works then.
                if (continuation.isActive) continuation.resume(CodeRequest.Verified(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                if (continuation.isActive) continuation.resumeWithException(e.toApiException())
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .apply { resendToken?.let(::setForceResendingToken) }
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun credential(verificationId: String, code: String): PhoneAuthCredential =
        PhoneAuthProvider.getCredential(verificationId, code)

    suspend fun signIn(credential: PhoneAuthCredential) {
        try {
            auth.signInWithCredential(credential).await()
        } catch (e: FirebaseException) {
            throw e.toApiException()
        }
    }

    /** Moves the signed-in user to a newly verified number. */
    suspend fun changePhone(credential: PhoneAuthCredential) {
        val user = auth.currentUser ?: throw ApiException(401, "UNAUTHENTICATED", "Please sign in again.")
        try {
            user.updatePhoneNumber(credential).await()
        } catch (e: FirebaseException) {
            throw e.toApiException()
        }
    }
}

private fun FirebaseException.toApiException(): ApiException = when (this) {
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == "ERROR_INVALID_VERIFICATION_CODE" || errorCode == "ERROR_SESSION_EXPIRED") {
            ApiException(401, "INVALID_OTP", "That code didn't match. Try again.")
        } else {
            ApiException(400, "INVALID_PHONE", "That phone number can't receive codes. Check it and try again.")
        }
    is FirebaseAuthUserCollisionException -> ApiException(409, "PHONE_TAKEN", "That number is already used by another account.")
    is FirebaseTooManyRequestsException -> ApiException(429, "RATE_LIMITED", "Too many attempts. Try again later.")
    is FirebaseAuthException -> if (errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
        // Phone sign-in off, or SMS to this country blocked, in the Firebase project.
        ApiException(403, "SMS_NOT_ALLOWED", "SMS codes can't be sent to this number yet. Please try again later.")
    } else {
        ApiException(0, "AUTH_FAILED", message ?: "Phone verification failed. Try again.")
    }
    else -> ApiException(0, "AUTH_FAILED", message ?: "Phone verification failed. Try again.")
}
