package com.example.digitalmonk.service.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.accessibility.detectors.UninstallerDetector;
import com.example.digitalmonk.service.accessibility.handlers.AppBlockHandler;
import com.example.digitalmonk.service.accessibility.handlers.ShortsBlockHandler;
// import com.example.digitalmonk.service.accessibility.handlers.ScreenTimeHandler; // Ready for Phase 3
import com.example.digitalmonk.service.accessibility.GuardianAccessibilityService;
import com.example.digitalmonk.service.overlay.SettingsBlockOverlayService;

/**
 * Why we made this file:
 * This is the beating heart of Digital Monk's foreground monitoring system.
 * An Android AccessibilityService is a highly privileged background engine that
 * can read the screen and inject global actions (like pressing 'Home' or 'Back').
 *
 * * Architectural Note (Dispatcher Pattern):
 * We intentionally keep this file small. Instead of writing massive if/else blocks
 * here to handle blocking apps, tracking time, and finding keywords, this service
 * acts as a "Dispatcher." It simply receives the raw events from Android and passes
 * them to the specialized Handlers we just built.
 *
 * What the file name defines:
 * "Guardian" reflects its role as the active protector of the device.
 * "AccessibilityService" is the Android framework class it extends.
 */
public class GuardianAccessibilityService extends AccessibilityService {

    private static final String TAG = "GuardianService";

    public static volatile long lastEventTimestamp = 0L;
    public static volatile long serviceConnectedTimestamp = 0L;
    public static volatile String lastForegroundPackage = null;

    // Dependencies
    private PrefsManager prefs;

    // Feature Handlers
    private ShortsBlockHandler shortsBlockHandler;
    private AppBlockHandler appBlockHandler;
    // private ScreenTimeHandler screenTimeHandler;

    /**
     * Handlers are initialized lazily here to guarantee that the Android 'Context'
     * is fully available.
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        prefs = new PrefsManager(this);

        // We pass 'this::performGlobalAction' as a method reference to satisfy
        // the ActionPerformer functional interface we built in the Handlers.
        shortsBlockHandler = new ShortsBlockHandler(prefs, this::performGlobalAction);
        appBlockHandler = new AppBlockHandler(prefs, this::performGlobalAction);
        // screenTimeHandler = new ScreenTimeHandler(prefs, this::performGlobalAction);

        serviceConnectedTimestamp = System.currentTimeMillis();
        lastEventTimestamp = 0L; // reset so health checker starts fresh

        Log.i(TAG, "Guardian service connected ✅");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        GuardianAccessibilityService.lastEventTimestamp = System.currentTimeMillis();

        // Safe check
        if (event == null) return;

        // We only care about events where the window state or content changes
        int eventType = event.getEventType();
        CharSequence pkgSequence = event.getPackageName();

        // 4. Now use them to update the last foreground package
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgSequence != null) {
            GuardianAccessibilityService.lastForegroundPackage = pkgSequence.toString();
        }


        // Optimization: Only process major UI changes to save CPU and prevent flickering
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        // Extract the package name safely
        if (pkgSequence == null) return;
        String pkg = pkgSequence.toString();

        // Security / UX Check: NEVER interfere with Digital Monk's own UI
        if (pkg.equals(getApplicationContext().getPackageName())) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();

        shortsBlockHandler.handle(root, pkg);
        appBlockHandler.handle(root, pkg, getApplicationContext());

    }

    @Override
    public void onInterrupt() {
        lastEventTimestamp = 0L; // mark as potentially broken

        Log.w(TAG, "Service interrupted");
    }

}