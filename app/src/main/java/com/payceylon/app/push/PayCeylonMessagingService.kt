package com.payceylon.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.payceylon.app.data.ApiException
import com.payceylon.app.data.AppServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the API's pushes. They are data-only messages (title, body, kind, transactionId), so
 * the app builds every notification itself, in the foreground and in the background alike.
 */
class PayCeylonMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: return
        PushNotifications.show(
            context = this,
            id = data["id"] ?: message.messageId ?: title,
            kind = data["kind"].orEmpty(),
            title = title,
            body = data["body"].orEmpty(),
            transactionId = data["transactionId"]?.takeIf { it.isNotEmpty() },
        )
    }

    /** FCM rotated this phone's token: tell the server, if someone is signed in. */
    override fun onNewToken(token: String) {
        val backend = AppServices.backend
        if (!backend.hasSession()) return
        scope.launch {
            try {
                backend.registerDevice(token)
            } catch (_: ApiException) {
                // Registered again at the next sign-in or app launch.
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}