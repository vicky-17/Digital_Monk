package com.example.digitalmonk.service.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.accessibility.handlers.AppBlockHandler;
import com.example.digitalmonk.service.accessibility.handlers.ShortsBlockHandler;

/**
 * Guardian Accessibility Service — Dispatcher pattern.
 *
 * Keeps this class small: receives raw AT events, routes to specialized handlers.
 * Critical performance rule: getRootInActiveWindow() is expensive — call it only
 * when eventType is STATE_CHANGED or CONTENT_CHANGED, never for other types.
 */
public class GuardianAccessibilityService extends AccessibilityService {

    private static final String TAG = "GuardianService";

    public static volatile long   lastEventTimestamp        = 0L;
    public static volatile long   serviceConnectedTimestamp = 0L;
    public static volatile String lastForegroundPackage     = null;

    private PrefsManager      prefs;
    private ShortsBlockHandler shortsBlockHandler;
    private AppBlockHandler    appBlockHandler;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        prefs = new PrefsManager(this);
        shortsBlockHandler = new ShortsBlockHandler(prefs, this::performGlobalAction);
        appBlockHandler    = new AppBlockHandler(prefs, this::performGlobalAction);

        serviceConnectedTimestamp = System.currentTimeMillis();
        lastEventTimestamp        = 0L;

        Log.i(TAG, "Guardian service connected ✅");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        // Always stamp the heartbeat so AccessibilityHealthChecker knows we're alive.
        lastEventTimestamp = System.currentTimeMillis();

        if (event == null) return;

        int    eventType    = event.getEventType();
        CharSequence pkgSeq = event.getPackageName();

        // Track foreground package on every STATE_CHANGED.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgSeq != null) {
            lastForegroundPackage = pkgSeq.toString();
        }

        // ── Only run handlers for the two event types we care about ───────────
        // Ignoring all other event types (TYPE_VIEW_CLICKED, TYPE_ANNOUNCEMENT,
        // etc.) avoids unnecessary getRootInActiveWindow() calls, which are the
        // #1 cause of "Digital Monk not responding" ANR dialogs.
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();

        // Never interfere with our own UI.
        if (pkg.equals(getApplicationContext().getPackageName())) return;

        // getRootInActiveWindow() — called ONCE per event, shared between handlers.
        AccessibilityNodeInfo root = getRootInActiveWindow();

        // Shorts handler runs on both event types (needs content changes to detect scroll).
        shortsBlockHandler.handle(root, pkg);

        // App-block handler receives eventType so it can debounce the expensive
        // UninstallerDetector to STATE_CHANGED only (not every CONTENT_CHANGED).
        appBlockHandler.handle(root, pkg, eventType, getApplicationContext());
    }

    @Override
    public void onInterrupt() {
        // Reset so AccessibilityHealthChecker can detect a frozen service.
        lastEventTimestamp = 0L;
        Log.w(TAG, "Service interrupted");
    }
}