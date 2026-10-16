package com.payceylon.app.data

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Phone sign-in with codes issued by the PayCeylon API. There is no SMS: the server prints each
 * code to its log, and a correct code is swapped for a Firebase custom token.
 */
object PhoneAuth {
    private val auth: FirebaseAuth
        get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()
            ?: throw ApiException(0, "NOT_CONFIGURED", "Firebase isn't set up in this build. Add it to local.properties.")

    /** Asks the server for a new code for [phone] (E.164, e.g. +94771234567). */
    suspend fun requestCode(phone: String) {
        AppServices.backend.requestCode(phone)
    }

    /** Confirms [code] for [phone] and signs in to Firebase as that number's user. */
    suspend fun signIn(phone: String, code: String) {
        val customToken = AppServices.backend.confirmCode(phone, code)
        try {
            auth.signInWithCustomToken(customToken).await()
        } catch (e: FirebaseException) {
            throw ApiException(0, "AUTH_FAILED", e.message ?: "Sign-in failed. Try again.")
        }
    }
}
