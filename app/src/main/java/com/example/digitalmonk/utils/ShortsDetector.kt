package com.example.digitalmonk.util

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects YouTube Shorts, Instagram Reels, TikTok, and similar
 * short-form video content using View IDs and package names.
 */
object ShortsDetector {

// Full-screen short-form video apps (always block entire app)
private val BLOCKED_PACKAGES = setOf(
        "com.ss.android.ugc.trill",       // TikTok (some regions)
        "com.zhiliaoapp.musically",        // TikTok (global)
        "com.ss.android.ugc.aweme"         // TikTok (CN)
)

// Specific View IDs that indicate a Shorts/Reels player is active
private val SHORTS_VIEW_IDS = setOf(
        "com.google.android.youtube:id/reel_recycler",       // YouTube Shorts
        "app.revanced.android.youtube:id/reel_recycler",     // ReVanced YouTube Shorts
        "com.instagram.android:id/root_clips_layout",         // Instagram Reels
        "com.instagram.android:id/reply_bar_container"        // Instagram Inbox Reels
)

/**
 * Returns true if the current screen is showing short-form video content.
 * @param rootNode   Root accessibility node of the current window.
 * @param packageName  Foreground app package name.
 */
fun shouldBlock(rootNode: AccessibilityNodeInfo?, packageName: String?): Boolean {
    if (packageName == null) return false

    // 1. Block by package (e.g. TikTok is always short-form)
    if (packageName in BLOCKED_PACKAGES) return true

    // 2. Block by specific UI element (Shorts player inside YouTube/Instagram)
    if (rootNode == null) return false
    return SHORTS_VIEW_IDS.any { viewId -> hasViewId(rootNode, viewId) }
}

private fun hasViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
    return try {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        nodes != null && nodes.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}
}