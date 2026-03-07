package com.example.digitalmonk.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.digitalmonk.data.PrefsManager
import com.example.digitalmonk.util.ShortsDetector

/**
 * Single AccessibilityService that handles all on-device enforcement.
 *
 * Phase 1: Shorts blocking (YouTube Shorts, Instagram Reels, TikTok)
 * Future phases: App blocking, screen time limits, etc.
 */
class GuardianAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PrefsManager
    private var lastBlockedPackage: String? = null

    override fun onServiceConnected() {
        prefs = PrefsManager(this)
        Log.d(TAG, "Guardian service connected ✅")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Only act on window/content changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return

        // Skip our own app to avoid interference
        if (packageName == applicationContext.packageName) return

        handleShortsBlocking(packageName)
    }

    private fun handleShortsBlocking(packageName: String) {
        if (!prefs.blockShorts) return

        val shouldBlock = ShortsDetector.shouldBlock(rootInActiveWindow, packageName)

        if (shouldBlock) {
            // Avoid spamming back presses for the same package
            if (lastBlockedPackage != packageName) {
                Log.d(TAG, "🚫 Blocking Shorts in: $packageName")
                lastBlockedPackage = packageName
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
        } else {
            // Reset tracker when user leaves the blocked screen
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