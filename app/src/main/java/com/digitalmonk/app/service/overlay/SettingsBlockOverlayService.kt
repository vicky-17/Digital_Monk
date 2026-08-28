package com.digitalmonk.app.service.overlay

import android.R
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.digitalmonk.app.core.utils.Constants
import com.digitalmonk.app.ui.overlay.OverlayBridge
import com.digitalmonk.app.ui.overlay.OverlayBridge.setStage
import com.digitalmonk.app.ui.overlay.OverlayLifecycleOwner
import com.digitalmonk.app.ui.overlay.SettingsOverlayStage
import kotlin.concurrent.Volatile

class SettingsBlockOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var mainHandler: Handler? = null


    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager?
        mainHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(Constants.NOTIFICATION_ID_SETTINGS_BLOCK, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }

        val action = intent.getAction()
        if (action == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // If full overlay is locked, only allow HIDE
        if (isFullOverlay && ACTION_HIDE != action) {
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_SHOW_BOTTOM -> if (!isRunning) {
                isRunning = true
                // Show the overlay at HALF stage (650dp initial blocker)
                mainHandler!!.post(Runnable {
                    showOverlay()
                    updateStage(SettingsOverlayStage.HALF)
                })
            }

            ACTION_SHOW_FULL -> {
                isFullOverlay = true
                mainHandler!!.post(Runnable {
                    if (!isRunning) showOverlay()
                    updateStage(SettingsOverlayStage.FULL)
                    // Make overlay interactive so "Go Home" button works
                    if (composeView != null) {
                        overlayParams!!.flags = (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        try {
                            windowManager!!.updateViewLayout(composeView, overlayParams)
                        } catch (ignored: Exception) {
                        }
                    }
                })
            }

            ACTION_SHRINK_BOTTOM -> mainHandler!!.postDelayed(Runnable {
                if (!isFullOverlay && isRunning) {
                    updateStage(SettingsOverlayStage.STRIP)
                }
            }, 1000L)

            ACTION_HIDE -> mainHandler!!.post(Runnable {
                updateStage(SettingsOverlayStage.HIDE)
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isRunning = false
                isFullOverlay = false
            })
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler!!.removeCallbacksAndMessages(null)
        removeOverlay()
        isRunning = false
        isFullOverlay = false
    }

    // ── Core: show the ComposeView overlay once ───────────────────────────────
    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission")
            return
        }
        if (composeView != null) return  // already showing


        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL),
            PixelFormat.TRANSLUCENT
        )
        overlayParams!!.gravity = Gravity.BOTTOM or Gravity.START

        // Set up Compose lifecycle so ComposeView renders outside an Activity
        lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner!!.onCreate()

        composeView = ComposeView(this)

        // Attach lifecycle and saved-state owners (required by Compose)
        composeView!!.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView!!.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        // Delegate content to the Kotlin bridge
        OverlayBridge.setContent(composeView!!, Runnable {
            // "Go Home" callback
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(homeIntent)
            isFullOverlay = false
            hide(this)
        })

        try {
            windowManager!!.addView(composeView, overlayParams)
            lifecycleOwner!!.onStart()
            lifecycleOwner!!.onResume()
            Log.i(TAG, "Compose overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
            composeView = null
        }
    }

    // ── Single method to change stage — this is all WatchdogService calls ─────
    private fun updateStage(stage: SettingsOverlayStage) {
        setStage(stage)
        Log.d(TAG, "Overlay stage → " + stage.name)
    }

    private fun removeOverlay() {
        if (lifecycleOwner != null) {
            lifecycleOwner!!.onPause()
            lifecycleOwner!!.onStop()
            lifecycleOwner!!.onDestroy()
            lifecycleOwner = null
        }
        if (composeView != null && windowManager != null) {
            try {
                windowManager!!.removeView(composeView)
            } catch (ignored: Exception) {
            }
            composeView = null
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_SILENT)
            .setContentTitle("").setContentText("")
            .setSmallIcon(R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true).setShowWhen(false).setOngoing(false)
            .build()
    }

    companion object {
        private const val TAG = "SettingsBlockOverlay"

        const val ACTION_SHOW_BOTTOM: String = "ACTION_SETTINGS_BLOCK_BOTTOM"
        const val ACTION_SHOW_FULL: String = "ACTION_SETTINGS_BLOCK_FULL"
        const val ACTION_SHRINK_BOTTOM: String = "ACTION_SETTINGS_BLOCK_SHRINK"
        const val ACTION_HIDE: String = "ACTION_SETTINGS_BLOCK_HIDE"

        @JvmField
        @Volatile
        var isRunning: Boolean = false

        @JvmField
        @Volatile
        var isFullOverlay: Boolean = false

        // ── Static helpers ────────────────────────────────────────────────────────
        @JvmStatic
        fun showBottom(context: Context) {
            val i = Intent(context, SettingsBlockOverlayService::class.java)
            i.setAction(ACTION_SHOW_BOTTOM)
            context.startForegroundService(i)
        }

        fun expandFull(context: Context) {
            val i = Intent(context, SettingsBlockOverlayService::class.java)
            i.setAction(ACTION_SHOW_FULL)
            context.startForegroundService(i)
        }

        @JvmStatic
        fun shrinkToBottom(context: Context) {
            val i = Intent(context, SettingsBlockOverlayService::class.java)
            i.setAction(ACTION_SHRINK_BOTTOM)
            context.startForegroundService(i)
        }

        @JvmStatic
        fun hide(context: Context) {
            val i = Intent(context, SettingsBlockOverlayService::class.java)
            i.setAction(ACTION_HIDE)
            context.startService(i)
        }
    }
}