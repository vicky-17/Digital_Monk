package com.curbme.app.service.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.service.notification.NotificationHelper
import com.curbme.app.service.notification.NotificationHelper.buildGuardianForegroundNotification
import com.curbme.app.ui.block.AppBlockOverlayManager

class OverlayService : Service() {
    private var overlayManager: AppBlockOverlayManager? = null

    override fun onCreate() {
        super.onCreate()
        val dataStoreManager = DataStoreManager(this)
        overlayManager = AppBlockOverlayManager(this, dataStoreManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotificationHelper.FOREGROUND_SERVICE_ID,
            buildGuardianForegroundNotification(this)
        )

        if (intent != null && intent.getAction() != null) {
            val action = intent.getAction()

            if (ACTION_SHOW_BLOCK == action) {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "Restricted App"
                val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Restricted App"
                val planType = intent.getStringExtra(EXTRA_PLAN_TYPE) ?: "STAY_FOCUSED"
                val reason = intent.getStringExtra(EXTRA_REASON) ?: "App is restricted"

                Log.d(TAG, "Command received: SHOW block for $packageName")
                overlayManager?.show(appName, packageName, reason, planType)
            } else if (ACTION_HIDE_BLOCK == action) {
                Log.d(TAG, "Command received: HIDE block")
                overlayManager?.hide()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        overlayManager?.hide()
    }

    companion object {
        private const val TAG = "OverlayService"

        const val ACTION_SHOW_BLOCK: String = "ACTION_SHOW_BLOCK"
        const val ACTION_HIDE_BLOCK: String = "ACTION_HIDE_BLOCK"
        const val EXTRA_PACKAGE_NAME: String = "EXTRA_PACKAGE_NAME"
        const val EXTRA_APP_NAME: String = "EXTRA_APP_NAME"
        const val EXTRA_PLAN_TYPE: String = "EXTRA_PLAN_TYPE"
        const val EXTRA_REASON: String = "EXTRA_REASON"

        fun showBlockOverlay(context: Context, packageName: String?, appName: String? = null, planType: String? = null, reason: String? = null) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_SHOW_BLOCK
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_PLAN_TYPE, planType)
                putExtra(EXTRA_REASON, reason)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hideBlockOverlay(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            intent.setAction(ACTION_HIDE_BLOCK)
            context.startService(intent)
        }
    }
}
