package com.digitalmonk.app.service.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.digitalmonk.app.service.notification.NotificationHelper
import com.digitalmonk.app.service.notification.NotificationHelper.buildGuardianForegroundNotification

/**
 * Why we made this file:
 * This Service manages the lifecycle of our system overlays. It listens for
 * command Intents (like "SHOW" or "HIDE") and delegates the actual UI rendering
 * to the BlockOverlayView.
 * 
 * Running this as a Foreground Service ensures that Android doesn't kill the
 * overlay memory while the child is staring at the blocked screen.
 * 
 * What the file name defines:
 * "Overlay" specifies the feature domain.
 * "Service" identifies it as a long-running Android OS component.
 */
class OverlayService : Service() {
    private var blockOverlayView: BlockOverlayView? = null

    override fun onCreate() {
        super.onCreate()
        // Initialize the view controller when the service starts
        blockOverlayView = BlockOverlayView(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to Foreground Service immediately to prevent crash on Android 8.0+
        startForeground(
            NotificationHelper.FOREGROUND_SERVICE_ID,
            buildGuardianForegroundNotification(this)
        )

        if (intent != null && intent.getAction() != null) {
            val action = intent.getAction()

            if (ACTION_SHOW_BLOCK == action) {
                var packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (packageName == null) packageName = "Restricted App"

                Log.d(TAG, "Command received: SHOW block for " + packageName)
                blockOverlayView!!.show(packageName)
            } else if (ACTION_HIDE_BLOCK == action) {
                Log.d(TAG, "Command received: HIDE block")
                blockOverlayView!!.hide()
                // Stop the service completely to free up RAM when the overlay isn't needed
                stopSelf()
            }
        }

        // If the system kills this service to reclaim memory, DO NOT automatically restart it.
        // We only want the overlay showing when explicitly triggered by a bypass attempt.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // This is a Started Service, not a Bound Service
    }

    override fun onDestroy() {
        super.onDestroy()
        // Failsafe: Ensure the overlay is removed if the service is destroyed
        if (blockOverlayView != null) {
            blockOverlayView!!.hide()
        }
    }

    companion object {
        private const val TAG = "OverlayService"

        // Intent Actions & Extras
        const val ACTION_SHOW_BLOCK: String = "ACTION_SHOW_BLOCK"
        const val ACTION_HIDE_BLOCK: String = "ACTION_HIDE_BLOCK"
        const val EXTRA_PACKAGE_NAME: String = "EXTRA_PACKAGE_NAME"

        // ── Static Helper Methods (Intent Builders) ───────────────────────────────
        /**
         * Triggers the service to show the block overlay.
         */
        fun showBlockOverlay(context: Context, packageName: String?) {
            val intent = Intent(context, OverlayService::class.java)
            intent.setAction(ACTION_SHOW_BLOCK)
            intent.putExtra(EXTRA_PACKAGE_NAME, packageName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Triggers the service to hide the block overlay and shut itself down.
         */
        fun hideBlockOverlay(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            intent.setAction(ACTION_HIDE_BLOCK)
            context.startService(intent) // startService is fine here, it will hit onStartCommand and stopSelf()
        }
    }
}