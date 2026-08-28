package com.digitalmonk.app.messaging

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Service to handle Firebase Cloud Messaging (FCM) notifications and data payloads.
 */
class MonkMessagingService : FirebaseMessagingService() {

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM Token: $token")
        // TODO: Send this token to your backend if you want to target specific users
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        // Handle Notification payloads
        message.notification?.let {
            Log.d(TAG, "Notification Received: ${it.title} - ${it.body}")
        }

        // Handle Data payloads (this is what you'll use for custom logic)
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Data Payload Received: ${message.data}")
            // Trigger local actions based on data (e.g., refresh data, show custom alert)
        }
    }

    companion object {
        private const val TAG = "MonkMessaging"
    }
}
