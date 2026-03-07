package com.example.digitalmonk.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.accessibility.detectors.ShortsDetector

/**
 * Single AccessibilityService that handles all on-device enforcement.
 */
class GuardianAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PrefsManager
    private var lastBlockedPackage: String? = null

    override fun onServiceConnected() {
        prefs = PrefsManager(this)
        Log.d(TAG, "Guardian service connected ✅")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        handleShortsBlocking(packageName)
    }

    private fun handleShortsBlocking(packageName: String) {
        if (!prefs.blockShorts) return

        val shouldBlock = ShortsDetector.shouldBlock(rootInActiveWindow, packageName)

        if (shouldBlock) {
            if (lastBlockedPackage != packageName) {
                Log.d(TAG, "🚫 Blocking Shorts in: $packageName")
                lastBlockedPackage = packageName
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
        } else {
            if (lastBlockedPackage == packageName) {
                lastBlockedPackage = null
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    companion object {
        private const val TAG = "GuardianService"
    }
}