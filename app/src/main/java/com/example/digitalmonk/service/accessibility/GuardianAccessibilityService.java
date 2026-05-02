package com.example.digitalmonk.service.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

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
    public static volatile boolean DEBUG_DUMP_UI = false;

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
        // Always update the heartbeat so health checks know the service is alive
        lastEventTimestamp = System.currentTimeMillis();

        if (event == null) return;

        int          eventType = event.getEventType();
        CharSequence pkgSeq    = event.getPackageName();

        // Track which app is in the foreground
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgSeq != null) {
            lastForegroundPackage = pkgSeq.toString();
        }

        // We only care about these two event types — ignore everything else
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();

        // Never process events from our own app
        if (pkg.equals(getApplicationContext().getPackageName())) return;

        // Fetch the root node once and share it between all handlers
        AccessibilityNodeInfo root = getRootInActiveWindow();

        // ── DEBUG: log full UI tree on window change ──────────────────────────
        if (DEBUG_DUMP_UI && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            UiDumper.dumpAll(root, pkg);
        }
        // ── END DEBUG ─────────────────────────────────────────────────────────

        // Delegate to feature handlers
        shortsBlockHandler.handle(root, pkg);
        appBlockHandler.handle(root, pkg, eventType, getApplicationContext());
    }
}