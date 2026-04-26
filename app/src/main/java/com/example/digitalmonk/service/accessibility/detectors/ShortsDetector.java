package com.example.digitalmonk.service.accessibility.detectors;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Why we made this file:
 * One of the core features of Digital Monk is blocking addictive short-form
 * video content (like YouTube Shorts or Instagram Reels) without blocking
 * the entire educational or social app.
 *
 * This class handles the specific detection logic. It checks two things:
 * 1. Is the whole app dedicated to shorts? (e.g., TikTok) -> Block immediately.
 * 2. If it's a mixed app (e.g., YouTube), does the current screen's UI hierarchy
 * contain the specific hidden Android "View ID" for the Shorts player?
 * -> If yes, block.
 *
 * What the file name defines:
 * "Shorts" identifies the type of media being monitored.
 * "Detector" signifies its role as a specialized scanner within the Accessibility layer.
 */
public class ShortsDetector {

    // ── Whole-app blocks ──────────────────────────────────────────────────────
    private static final Set<String> BLOCKED_PACKAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "com.ss.android.ugc.trill",       // TikTok (some regions)
            "com.zhiliaoapp.musically",       // TikTok (global)
            "com.ss.android.ugc.aweme"        // TikTok (CN / Douyin)
    )));

    // ── View-ID detection ─────────────────────────────────────────────────────
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