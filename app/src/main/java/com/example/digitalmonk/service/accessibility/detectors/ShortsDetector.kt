package com.example.digitalmonk.service.accessibility.detectors

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects whether the current screen is showing short-form video content.
 *
 * Detection strategy:
 *  1. Package-level block — entire app is short-form (e.g. TikTok).
 *  2. View-ID detection — Shorts/Reels player embedded inside a larger app
 *     (e.g. YouTube Shorts inside the YouTube app).
 *
 * To add support for a new app: append to [BLOCKED_PACKAGES] or [SHORTS_VIEW_IDS].
 */
object ShortsDetector {

    // ── Whole-app blocks ──────────────────────────────────────────────────────
    private val BLOCKED_PACKAGES = setOf(
        "com.ss.android.ugc.trill",       // TikTok (some regions)
        "com.zhiliaoapp.musically",        // TikTok (global)
        "com.ss.android.ugc.aweme"         // TikTok (CN / Douyin)
    )

    // ── View-ID detection ─────────────────────────────────────────────────────
    private val SHORTS_VIEW_IDS = setOf(
        "com.google.android.youtube:id/reel_recycler",        // YouTube Shorts
        "app.revanced.android.youtube:id/reel_recycler",      // ReVanced YouTube
        "com.instagram.android:id/root_clips_layout",          // Instagram Reels
        "com.instagram.android:id/reply_bar_container"         // Instagram Inbox Reels
    )

    fun shouldBlock(rootNode: AccessibilityNodeInfo?, packageName: String?): Boolean {
        if (packageName == null) return false
        if (packageName in BLOCKED_PACKAGES) return true
        if (rootNode == null) return false
        return SHORTS_VIEW_IDS.any { viewId -> hasViewId(rootNode, viewId) }
    }

    private fun hasViewId(root: AccessibilityNodeInfo, viewId: String): Boolean =
        try {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            nodes != null && nodes.isNotEmpty()
        } catch (e: Exception) {
            false
        }
}