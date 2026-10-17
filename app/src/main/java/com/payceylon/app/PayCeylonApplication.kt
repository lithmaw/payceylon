package com.payceylon.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.payceylon.app.data.AppServices
import com.payceylon.app.push.PushNotifications

class PayCeylonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Configured from local.properties instead of google-services.json, so no Gradle plugin is needed.
        if (FirebaseApp.getApps(this).isEmpty() && BuildConfig.FIREBASE_APP_ID.isNotEmpty()) {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    // Push notifications (FCM) need the project number, which is part of the app ID.
                    .setGcmSenderId(BuildConfig.FIREBASE_APP_ID.split(":").getOrElse(1) { "" })
                    .build(),
            )
        }
        AppServices.init(this, BuildConfig.API_BASE_URL)
        PushNotifications.createChannels(this)
    }
}
