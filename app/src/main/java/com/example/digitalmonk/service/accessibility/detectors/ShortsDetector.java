package com.example.digitalmonk.service.accessibility.detectors;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShortsDetector {

    private static final Set<String> BLOCKED_PACKAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.aweme"
    )));

    private static final Set<String> SHORTS_VIEW_IDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "com.google.android.youtube:id/reel_recycler",        // YouTube Shorts
            "app.revanced.android.youtube:id/reel_recycler",      // ReVanced YouTube
            "com.instagram.android:id/root_clips_layout",         // Instagram Reels
            "com.instagram.android:id/reply_bar_container"        // Instagram Inbox Reels
    )));

    /**
     * Private constructor for Utility Class.
     */
    private ShortsDetector() {}

    /**
     * Determines if the current screen contains short-form video content.
     */
    public static boolean shouldBlock(AccessibilityNodeInfo rootNode, String packageName) {
        if (packageName == null) return false;

        // 1. Check if the entire app is blocked (e.g., TikTok)
        if (BLOCKED_PACKAGES.contains(packageName)) return true;

        // 2. Check if a specific "Shorts" View ID is on the screen
        if (rootNode == null) return false;

        for (String viewId : SHORTS_VIEW_IDS) {
            if (hasViewId(rootNode, viewId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Searches the Android UI hierarchy for a specific View ID.
     */
    private static boolean hasViewId(AccessibilityNodeInfo root, String viewId) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
            return nodes != null && !nodes.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}