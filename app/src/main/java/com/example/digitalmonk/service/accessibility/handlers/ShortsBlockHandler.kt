package com.example.digitalmonk.service.accessibility.handlers

import android.view.accessibility.AccessibilityNodeInfo
import com.example.digitalmonk.core.utils.Logger
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.accessibility.detectors.ShortsDetector

/**
 * Handles YouTube Shorts / Instagram Reels / TikTok blocking.
 * Receives accessibility events from GuardianAccessibilityService.
 *
 * @param prefs            Shared preferences to read the feature toggle.
 * @param performAction    Lambda wrapping [AccessibilityService.performGlobalAction].
 */
class ShortsBlockHandler(
    private val prefs: PrefsManager,
    private val performAction: (Int) -> Boolean
) {

    private var lastBlockedPackage: String? = null

    fun handle(rootNode: AccessibilityNodeInfo?, packageName: String) {
        if (!prefs.blockShorts) return

        val shouldBlock = ShortsDetector.shouldBlock(rootNode, packageName)

        if (shouldBlock) {
            if (lastBlockedPackage != packageName) {
                Logger.d(TAG, "🚫 Blocking Shorts in: $packageName")
                lastBlockedPackage = packageName
            }
            performAction(GLOBAL_ACTION_BACK)
        } else {
            if (lastBlockedPackage == packageName) lastBlockedPackage = null
        }
    }

    companion object {
        private const val TAG = "ShortsBlockHandler"
        private const val GLOBAL_ACTION_BACK = 1   // AccessibilityService.GLOBAL_ACTION_BACK
    }
}