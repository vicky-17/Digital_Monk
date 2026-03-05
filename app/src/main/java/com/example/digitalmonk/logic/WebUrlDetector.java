package com.example.digitalmonk.logic;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public class WebUrlDetector {

    // Shared list accessed by SyncService
    public static List<String> visitedUrls = new ArrayList<>();
    private String lastUrl = "";

    private final String[] BROWSER_PACKAGES = {
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser"
    };

    public boolean isBrowser(String packageName) {
        for (String browser : BROWSER_PACKAGES) {
            if (packageName.contains(browser)) return true;
        }
        return false;
    }

    public void processBrowserEvent(AccessibilityNodeInfo root) {
        if (root == null) return;

        String capturedUrl = findBrowserUrl(root);

        // Save URL if it's new
        if (capturedUrl != null && !capturedUrl.equals(lastUrl) && !capturedUrl.isEmpty()) {
            lastUrl = capturedUrl;
            synchronized (visitedUrls) {
                visitedUrls.add(capturedUrl + "|" + System.currentTimeMillis());
            }
            // 🐛 FIX: Added Log statement so you can see URLs in Logcat!
            Log.d("WebTracker", "🌐 Captured New URL: " + capturedUrl);
        }
    }

    private String findBrowserUrl(AccessibilityNodeInfo root) {
        String[] urlBarIds = {
                "com.android.chrome:id/url_bar",
                "org.mozilla.firefox:id/url_bar_title",
                "com.microsoft.emmx:id/url_bar",
                "com.sec.android.app.sbrowser:id/location_bar_edit_text"
        };

        for (String id : urlBarIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo node = nodes.get(0);
                if (node.getText() != null) {
                    return node.getText().toString();
                }
            }
        }
        return null;
    }
}