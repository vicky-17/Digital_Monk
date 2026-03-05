package com.example.digitalmonk.services;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.digitalmonk.data.PrefsManager;
import com.example.digitalmonk.logic.BlockedAppsDetector;
import com.example.digitalmonk.logic.ShortsDetector;
import com.example.digitalmonk.logic.WebUrlDetector;

public class LiveGuardianService extends AccessibilityService {

    // The Logic Modules
    private PrefsManager prefs;
    private BlockedAppsDetector appBlocker;
    private ShortsDetector shortsBlocker;
    private WebUrlDetector webUrlDetector;

    // UI Components
    private WindowManager windowManager;
    private TextView overlayView; // For individual app blocking
    private boolean isOverlayShowing = false;

    // --- 🔒 NEW LOCK SCREEN VARIABLES ---
    private LinearLayout systemLockView; // The full screen lock
    private boolean isSystemLocked = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    // Global flag (Controlled by SyncService)
    public static boolean isRestrictedMode = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        prefs = new PrefsManager(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Initialize the Logic Modules
        appBlocker = new BlockedAppsDetector(prefs);
        shortsBlocker = new ShortsDetector();
        webUrlDetector = new WebUrlDetector();

        // Start the background enforcement loop
        startEnforcementLoop();
    }

    // --- 🔄 BACKGROUND LOOP (Checks every 0.5s) ---
    private void startEnforcementLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkRestrictedState();
                handler.postDelayed(this, 500); // Repeat every 500ms
            }
        }, 500);
    }

    private void checkRestrictedState() {
        if (isRestrictedMode) {
            if (!isSystemLocked) {
                showLockScreen(); // 🛑 LOCK IT
            }
        } else {
            if (isSystemLocked) {
                hideLockScreen(); // ✅ UNLOCK IT
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String packageName = event.getPackageName().toString();

        // Note: The "Internet Lock" is now handled by the background loop above.
        // We don't need to check isRestrictedMode here anymore.

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();

        try {
            // 1. Live App Blocker
            if (appBlocker.shouldBlockApp(packageName)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                performGlobalAction(GLOBAL_ACTION_HOME);
                showBlockedOverlay();
                return;
            } else {
                removeOverlay();
            }

            // 2. Live Shorts Blocker
            if (prefs != null && prefs.isBlockShortsEnabled()) {
                if (rootNode != null && shortsBlocker.shouldBlockView(rootNode, packageName)) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    return;
                }
            }

            // 3. Web Tracker
            if (rootNode != null && webUrlDetector.isBrowser(packageName)) {
                webUrlDetector.processBrowserEvent(rootNode);
            }
        } finally {
            if (rootNode != null) rootNode.recycle();
        }
    }

    @Override
    public void onInterrupt() { }

    // ==========================================
    // 🛑 NEW FULL SCREEN LOCK (The "Smooth" UI)
    // ==========================================
    private void showLockScreen() {
        if (isSystemLocked || windowManager == null) return;

        try {
            // Create the container
            systemLockView = new LinearLayout(this);
            systemLockView.setOrientation(LinearLayout.VERTICAL);
            systemLockView.setBackgroundColor(Color.BLACK);
            systemLockView.setGravity(Gravity.CENTER);

            // Title
            TextView title = new TextView(this);
            title.setText("⚠️ DEVICE LOCKED ⚠️");
            title.setTextColor(Color.RED);
            title.setTextSize(24);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 20);

            // Message
            TextView msg = new TextView(this);
            msg.setText("Internet & Location Required.\n\nPull down the status bar and\nenable them to continue.");
            msg.setTextColor(Color.WHITE);
            msg.setTextSize(16);
            msg.setGravity(Gravity.CENTER);

            systemLockView.addView(title);
            systemLockView.addView(msg);

            // Layout Params: Covers screen but allows Status Bar interaction
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, // High priority
                    // UPDATED: Removed FLAG_LAYOUT_IN_SCREEN so it doesn't cover the status bar.
                    // Added FLAG_NOT_FOCUSABLE so the status bar can receive touch events.
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            windowManager.addView(systemLockView, params);
            isSystemLocked = true;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideLockScreen() {
        if (isSystemLocked && systemLockView != null) {
            try {
                windowManager.removeView(systemLockView);
            } catch (Exception e) { e.printStackTrace(); }
            systemLockView = null;
            isSystemLocked = false;
        }
    }

    // --- Old App Block Overlay (Kept for specific apps) ---
    private void showBlockedOverlay() {
        if (isOverlayShowing) return;
        handler.post(() -> {
            try {
                if (overlayView == null) {
                    overlayView = new TextView(this);
                    overlayView.setText("🚫 APP BLOCKED");
                    overlayView.setTextSize(20);
                    overlayView.setTextColor(Color.WHITE);
                    overlayView.setBackgroundColor(Color.parseColor("#E6FF0000"));
                    overlayView.setGravity(Gravity.CENTER);
                }
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT, 300,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP;

                if (overlayView.getParent() == null) {
                    windowManager.addView(overlayView, params);
                    isOverlayShowing = true;
                    handler.postDelayed(this::removeOverlay, 3000);
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void removeOverlay() {
        if (isOverlayShowing && overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception e) {}
            isOverlayShowing = false;
        }
    }
}