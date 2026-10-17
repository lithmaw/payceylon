package com.payceylon.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import com.payceylon.app.MainActivity
import com.payceylon.app.R
import com.payceylon.app.ui.theme.PayCeylonPrimary

/** A push the user tapped: which notification, and the transaction it is about (if any). */
data class OpenedNotification(val notificationId: String, val transactionId: String?)

/** System notifications for the pushes the API sends (see PayCeylonMessagingService). */
object PushNotifications {
    private const val CHANNEL_PAYMENTS = "payments"
    private const val CHANNEL_ACCOUNT = "account"
    private const val EXTRA_NOTIFICATION_ID = "com.payceylon.app.NOTIFICATION_ID"
    private const val EXTRA_TRANSACTION_ID = "com.payceylon.app.TRANSACTION_ID"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PAYMENTS, "Payments", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Money sent, received, swapped, topped up or withdrawn"
                },
                NotificationChannel(CHANNEL_ACCOUNT, "Account and security", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Profile, card and bank changes"
                },
            ),
        )
    }

    /** False on Android 13+ until the user allows notifications. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** [kind] is the API's notification kind: TRANSACTION, ACCOUNT or SECURITY. */
    fun show(context: Context, id: String, kind: String, title: String, body: String, transactionId: String?) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_NOTIFICATION_ID, id)
            .putExtra(EXTRA_TRANSACTION_ID, transactionId)
        val tap = PendingIntent.getActivity(
            context, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, if (kind == "TRANSACTION") CHANNEL_PAYMENTS else CHANNEL_ACCOUNT)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PayCeylonPrimary.toArgb())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (kind == "TRANSACTION") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (kind == "TRANSACTION") NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post.
        }
    }

    /** The push behind [intent], if the app was opened by tapping one. */
    fun openedFrom(intent: Intent?): OpenedNotification? {
        val id = intent?.getStringExtra(EXTRA_NOTIFICATION_ID) ?: return null
        return OpenedNotification(id, intent.getStringExtra(EXTRA_TRANSACTION_ID)?.takeIf { it.isNotEmpty() })
    }
}