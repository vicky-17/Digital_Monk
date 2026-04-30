package com.example.digitalmonk.service.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.accessibility.handlers.AppBlockHandler;
import com.example.digitalmonk.service.accessibility.handlers.ShortsBlockHandler;

/**
 * GuardianAccessibilityService — Updated
 * ─────────────────────────────────────────────────────────────────────────────
 * KEY CHANGE: Added getCurrentRootNode() static method so SettingsPageReader
 * can request the root node without having a direct reference to the service.
 *
 * IMPORTANT: AppBlockHandler no longer handles settings page detection.
 * That is now fully owned by:
 *   WatchdogService → SettingsAppMonitor → SettingsBlockOverlayService
 *   WatchdogService → SettingsPageReader (reads page content for confirmation)
 *
 * Accessibility still handles:
 *   - Shorts blocking (ShortsBlockHandler)
 *   - Standard app blocking for non-settings apps (AppBlockHandler)
 *   - Provides root node to SettingsPageReader when alive
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class GuardianAccessibilityService extends AccessibilityService {

    private static final String TAG = "GuardianService";

    // ── Static state — read by SettingsPageReader, AccessibilityHealthChecker ─
    public static volatile long   lastEventTimestamp        = 0L;
    public static volatile long   serviceConnectedTimestamp = 0L;
    public static volatile String lastForegroundPackage     = null;

    // ── Static reference to self — used by getCurrentRootNode() ──────────────
    private static volatile GuardianAccessibilityService instance = null;

    // ── Handlers ──────────────────────────────────────────────────────────────
    private PrefsManager       prefs;
    private ShortsBlockHandler shortsBlockHandler;
    private AppBlockHandler    appBlockHandler;

    // ── Static API for SettingsPageReader ─────────────────────────────────────

    /**
     * Returns the root accessibility node of the current window.
     * Returns null if the service is not connected or has been recycled.
     *
     * THREAD SAFETY: getRootInActiveWindow() is thread-safe per Android docs.
     * Called from WatchdogService's settings-poll thread.
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        instance = this;
        prefs    = new PrefsManager(this);

        shortsBlockHandler = new ShortsBlockHandler(prefs, this::performGlobalAction);
        appBlockHandler    = new AppBlockHandler(prefs, this::performGlobalAction);

        serviceConnectedTimestamp = System.currentTimeMillis();
        lastEventTimestamp        = 0L;

        Log.i(TAG, "Guardian service connected ✅");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Always stamp the heartbeat
        lastEventTimestamp = System.currentTimeMillis();

        if (event == null) return;

        int        eventType = event.getEventType();
        CharSequence pkgSeq  = event.getPackageName();

        // Track foreground package
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgSeq != null) {
            lastForegroundPackage = pkgSeq.toString();
        }

        // Only process the two event types we care about
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();

        // Never interfere with our own UI
        if (pkg.equals(getApplicationContext().getPackageName())) return;

        // Get root ONCE — shared between handlers
        AccessibilityNodeInfo root = getRootInActiveWindow();

        // Shorts blocking
        shortsBlockHandler.handle(root, pkg);

        // App blocking (excluding settings packages — handled by WatchdogService now)
        appBlockHandler.handle(root, pkg, eventType, getApplicationContext());
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
}