package com.curbme.app.service.accessibility.handlers

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.service.accessibility.detectors.ShortsDetector.shouldBlock

class ShortsBlockHandler(
    private val actionPerformer: ActionPerformer
) {
    // State variable to prevent spamming the system logs
    private var lastBlockedPackage: String? = null

    /**
     * Functional interface for the callback.
     */
    fun interface ActionPerformer {
        fun performAction(action: Int): Boolean
    }

    /**
     * Evaluates the current screen state and fires a back press if short-form
     * video is detected.
     */
    fun handle(rootNode: AccessibilityNodeInfo?, packageName: String?, settings: Settings) {
        if (packageName == null || !settings.isBlockShorts) {
            return
        }

        val shouldBlock = shouldBlock(rootNode, packageName)

        if (shouldBlock) {
            // Log only if it's a new block event to prevent logcat flooding
            if (packageName != lastBlockedPackage) {
                // Note: I swapped your custom Logger for standard Android Log,
                // but you can change it back if you converted Logger.kt to Java!
                Log.d(TAG, "🚫 Blocking Shorts in: " + packageName)
                lastBlockedPackage = packageName
            }

            // Simulate pressing the physical/digital "Back" button
            actionPerformer.performAction(GLOBAL_ACTION_BACK)
        } else {
            if (packageName == lastBlockedPackage) {
                lastBlockedPackage = null
            }
        }
    }

    companion object {
        private const val TAG = "ShortsBlockHandler"

        // We use BACK (1) instead of HOME (2) so we don't close the entire app
        private val GLOBAL_ACTION_BACK = AccessibilityService.GLOBAL_ACTION_BACK
    }
}