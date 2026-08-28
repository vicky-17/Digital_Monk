package com.digitalmonk.app.service.accessibility.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.digitalmonk.app.data.local.prefs.Settings
import com.digitalmonk.app.service.monitor.SettingsAppMonitor

class AppBlockHandler(
    private val actionPerformer: ActionPerformer
) {
    fun interface ActionPerformer {
        fun performAction(action: Int): Boolean
    }

    fun handle(
        root: AccessibilityNodeInfo?,
        packageName: String?,
        eventType: Int,
        settings: Settings
    ) {
        if (packageName == null) return

        if (SettingsAppMonitor.SETTINGS_PACKAGES.contains(packageName)) {
            return
        }

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        if (!settings.blockedPackages.contains(packageName)) return

        Log.d(TAG, "🚫 Blocked: " + packageName + " → HOME")
        actionPerformer.performAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    companion object {
        private const val TAG = "AppBlockHandler"
    }
}