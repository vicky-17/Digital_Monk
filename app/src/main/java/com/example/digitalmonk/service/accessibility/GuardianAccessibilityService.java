package com.example.digitalmonk.service.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import com.example.digitalmonk.core.utils.UiDumper;
import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.accessibility.handlers.AppBlockHandler;
import com.example.digitalmonk.service.accessibility.handlers.ShortsBlockHandler;





public class GuardianAccessibilityService extends AccessibilityService {

    private static final String TAG = "GuardianService";

    // ─────────────────────────────────────────────────────────────────────────
    // Public Static State  (read by other components from background threads)
    // ─────────────────────────────────────────────────────────────────────────

    /** System.currentTimeMillis() of the last accessibility event received */
    public static volatile long   lastEventTimestamp        = 0L;

    /** System.currentTimeMillis() when the service successfully connected */
    public static volatile long   serviceConnectedTimestamp = 0L;

    /** Package name of the most recently foregrounded app */
    public static volatile String lastForegroundPackage     = null;

    // ─────────────────────────────────────────────────────────────────────────
    // Debug Flag
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * DEBUG — set true to dump the full UI tree on every window change.
     * Filter logcat by tag MONK_UI_DUMP to see the output.
     * Remember to set back to false before release.
     */
    public static volatile boolean DEBUG_DUMP_UI = true;

    // ─────────────────────────────────────────────────────────────────────────
    // Internal State
    // ─────────────────────────────────────────────────────────────────────────

    /** Held so getCurrentRootNode() can call getRootInActiveWindow() statically */
    private static volatile GuardianAccessibilityService instance = null;

    private PrefsManager       prefs;
    private ShortsBlockHandler shortsBlockHandler;
    private AppBlockHandler    appBlockHandler;

    // =========================================================================
    // Static API  (used by SettingsPageReader)
    // =========================================================================

    /**
     * Returns the root AccessibilityNodeInfo for the currently active window,
     * or null if the service is not connected / has been recycled.
     *
     * Safe to call from any thread — getRootInActiveWindow() is documented as
     * thread-safe by the Android framework.
     */
    public static AccessibilityNodeInfo getCurrentRootNode() {
        GuardianAccessibilityService svc = instance;
        if (svc == null) return null;
        try {
            return svc.getRootInActiveWindow();
        } catch (Exception e) {
            return null;
        }
    }

    // Add this public static getter (add alongside getCurrentRootNode):
    public static GuardianAccessibilityService getInstance() {
        return instance;
    }

    // =========================================================================
    // Service Lifecycle
    // =========================================================================

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        instance = this;
        prefs    = new PrefsManager(this);

        shortsBlockHandler = new ShortsBlockHandler(prefs, this::performGlobalAction);
        appBlockHandler    = new AppBlockHandler(prefs, this::performGlobalAction);

        serviceConnectedTimestamp = System.currentTimeMillis();
        lastEventTimestamp        = 0L;

        Log.i(TAG, "Guardian accessibility service connected");
    }

    @Override
    public void onInterrupt() {
        lastEventTimestamp = 0L;
        Log.w(TAG, "Service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.w(TAG, "Service destroyed");
    }

    // =========================================================================
    // Event Handling
    // =========================================================================

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        lastEventTimestamp = System.currentTimeMillis();

        if (event == null) return;

        int eventType = event.getEventType();
        CharSequence pkgSeq = event.getPackageName();

        // Track foreground package
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgSeq != null) {
            lastForegroundPackage = pkgSeq.toString();
        }

        // ── PRIORITY 1: Uninstaller guard — runs first ─────────
        // No package filter, no early returns.
        // Only skip our own app to avoid loops.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                // Log the hierarchy for debugging
//                logViewHierarchy(root, 0);

                // Run the detection and blocking logic
                if (findAndPerformBack(root)) return; // dangerous screen → BACK, skip everything
            }
        }
        // ── END PRIORITY 1 ────────────────────────────────────────────────────

        // Everything below is secondary — filter events we don't need
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();
        if (pkg.equals(getApplicationContext().getPackageName())) return;

        // Re-use root (fetch again — previous root may have been from a different window)
        AccessibilityNodeInfo root = getRootInActiveWindow();

        // ── DEBUG ─────────────────────────────────────────────────────────────
        if (DEBUG_DUMP_UI && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            UiDumper.dumpAll(root, pkg);
        }

        // ── Normal handlers ───────────────────────────────────────────────────
        shortsBlockHandler.handle(root, pkg);
        appBlockHandler.handle(root, pkg, eventType, getApplicationContext());
    }


    // ── Uninstaller / Device-Admin Back Guard (ASLeech pattern) ──────────

    /**
     * Recursively walks the accessibility tree.
     * If any node's text matches a known dangerous label, fires GLOBAL_ACTION_BACK
     * immediately and stops traversal.

     * Target texts:
     *   "Digital Monk"          — our app name on App Info / Device Admin page
     *   "Erase all data (factory reset)" — Device Admin deactivation confirm page
     *   "Uninstall"             — stock Android App Info uninstall button
     *   "Force stop"            — App Info page (confirms we are on the right screen)
     *   "Deactivate this device admin app" — Device Admin deactivation button text
     */
    private static final Set<String> BACK_TRIGGER_TEXTS = new HashSet<>(Arrays.asList(
            "Erase all data (factory reset)",
            "Deactivate this device admin app",
            "Uninstall",
            "Force stop"
    ));

    private boolean findAndPerformBack(AccessibilityNodeInfo root) {
        if (root == null) return false;

        // Gate 1 — page must be about our app
        if (!hasText(root, "Digital Monk"))
            return false;

        // Gate 2 — dangerous action must be present
        boolean dangerous =
                hasText(root, "Uninstall")
                        || hasText(root, "Force stop")
                        || hasText(root, "Erase all data (factory reset)")
                        || hasText(root, "Deactivate this device admin app")
                        || hasText(root, "Use Digital Monk")
                        || hasText(root, "Battery details")
                        || hasText(root, "VPN")
                ;


        if (dangerous) {
            Log.w(TAG, "🔒 Dangerous page for Digital Monk → firing BACK");
            performGlobalAction(GLOBAL_ACTION_BACK);
            return true;
        }

        return false;
    }

    private boolean hasText(AccessibilityNodeInfo root, String text) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            return nodes != null && !nodes.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }



    /**
     * logs the view hierarchy to Logcat.
     * Used to understand the UI structure of different windows.
     */
    private void logViewHierarchy(AccessibilityNodeInfo nodeInfo, int depth) {
        if (nodeInfo == null) return;

        // Create indentation based on depth
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            prefix.append("  ");
        }

        // Print the node information
        Log.d(TAG, prefix.toString() + nodeInfo.toString());

        // Iterate through children
        for (int i = 0; i < nodeInfo.getChildCount(); i++) {
            AccessibilityNodeInfo child = nodeInfo.getChild(i);
            if (child != null) {
                logViewHierarchy(child, depth + 1);
            }
        }
    }


}