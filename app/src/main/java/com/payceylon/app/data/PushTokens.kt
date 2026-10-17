package com.payceylon.app.data

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/** This phone's Firebase Cloud Messaging registration token: where the server sends pushes. */
interface PushTokens {
    /** The current token, or null when push isn't available (no Firebase or Play services). */
    suspend fun current(): String?

    /** Invalidates the token, so nothing more arrives for the account that signed out. */
    suspend fun delete()

    /** No push (unit tests, or before Firebase is set up). */
    object None : PushTokens {
        override suspend fun current(): String? = null
        override suspend fun delete() = Unit
    }
}

internal object FirebasePushTokens : PushTokens {
    override suspend fun current(): String? = messaging()?.token?.await()

    override suspend fun delete() {
        messaging()?.deleteToken()?.await()
    }

    /** Null when Firebase wasn't configured (no Firebase settings in local.properties). */
    private fun messaging(): FirebaseMessaging? = try {
        FirebaseMessaging.getInstance()
    } catch (_: IllegalStateException) {
        null
    }
}