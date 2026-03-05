package com.example.digitalmonk.logic;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Arrays;
import java.util.List;

public class ShortsDetector {

    // List of View IDs that represent "Shorts" or "Reels" players
    private static final List<String> BLOCKED_VIEW_IDS = Arrays.asList(
            "com.instagram.android:id/root_clips_layout",       // Instagram Reels
            "com.google.android.youtube:id/reel_recycler",      // YouTube Shorts
            "app.revanced.android.youtube:id/reel_recycler",    // ReVanced Shorts
            "com.instagram.android:id/reply_bar_container"      // Instagram Inbox Reels
    );

    // List of Packages that are purely for short-form content (TikTok)
    private static final List<String> BLOCKED_PACKAGES = Arrays.asList(
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.aweme"
    );

    /**
     * Checks if the current screen contains a forbidden view (Shorts/Reels).
     * @param rootNode The root accessibility node of the current window.
     * @param currentPackage The package name of the app currently in foreground.
     * @return true if the view should be blocked (Back button pressed).
     */
    public boolean shouldBlockView(AccessibilityNodeInfo rootNode, String currentPackage) {
        if (rootNode == null || currentPackage == null) return false;

        // 1. Check strict package blocking (e.g. TikTok)
        if (BLOCKED_PACKAGES.contains(currentPackage)) {
            return true;
        }

        // 2. Check for specific UI elements (Reels/Shorts players)
        for (String viewId : BLOCKED_VIEW_IDS) {
            if (isViewVisible(rootNode, viewId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Helper to find if a specific View ID exists in the current hierarchy.
     */
    private boolean isViewVisible(AccessibilityNodeInfo root, String viewId) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
            return nodes != null && !nodes.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}