package com.digitalmonk.app.service.accessibility.detectors

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Collections

object ShortsDetector {
    private val BLOCKED_PACKAGES: MutableSet<String?> = Collections.unmodifiableSet<String?>(
        HashSet<String?>(
            mutableListOf<String?>(
                "com.ss.android.ugc.trill",
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.aweme"
            )
        )
    )

    private val SHORTS_VIEW_IDS: MutableSet<String> = Collections.unmodifiableSet<String?>(
        HashSet<String?>(
            mutableListOf<String?>(
                "com.google.android.youtube:id/reel_recycler",  // YouTube Shorts
                "app.revanced.android.youtube:id/reel_recycler",  // ReVanced YouTube
                "com.instagram.android:id/root_clips_layout",  // Instagram Reels
                "com.instagram.android:id/reply_bar_container" // Instagram Inbox Reels
            )
        )
    )

    /**
     * Determines if the current screen contains short-form video content.
     */
    @JvmStatic
    fun shouldBlock(rootNode: AccessibilityNodeInfo?, packageName: String?): Boolean {
        if (packageName == null) return false

        // 1. Check if the entire app is blocked (e.g., TikTok)
        if (BLOCKED_PACKAGES.contains(packageName)) return true

        // 2. Check if a specific "Shorts" View ID is on the screen
        if (rootNode == null) return false

        for (viewId in SHORTS_VIEW_IDS) {
            if (hasViewId(rootNode, viewId)) {
                return true
            }
        }

        return false
    }

    /**
     * Searches the Android UI hierarchy for a specific View ID.
     */
    private fun hasViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        try {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            return nodes != null && !nodes.isEmpty()
        } catch (e: Exception) {
            return false
        }
    }
}