package com.example.digitalmonk.service.overlay;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.digitalmonk.R;
import com.example.digitalmonk.core.utils.Constants;

/**
 * SettingsBlockOverlayService — Restructured for UsageStats-driven detection
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * NEW ARCHITECTURE (no accessibility dependency):
 * ─────────────────────────────────────────────────────────────────────────────
 * STATE MACHINE:
 *   HIDDEN   → settings package detected by WatchdogService   → BOTTOM_ONLY
 *   BOTTOM_ONLY → uninstaller page confirmed by page reader   → FULL_SCREEN
 *   FULL_SCREEN → safe page detected (not uninstaller)        → BOTTOM_ONLY
 *   BOTTOM_ONLY / FULL_SCREEN → settings app closed           → HIDDEN (via WatchdogService)
 *
 * KEY CHANGES vs old version:
 *   1. NO auto-timeout. Overlay is NEVER automatically removed.
 *      Only WatchdogService can hide it when settings is confirmed closed.
 *   2. Two-layer overlay:
 *      - Bottom blocker: 200dp tall, covers action buttons area (Force stop / Uninstall / Deactivate)
 *      - Full overlay: expands to fill entire screen when uninstaller page confirmed
 *   3. Height animation from bottom layer to full screen (smooth expansion)
 *   4. Expansion direction: grows UPWARD from bottom — natural since bottom bar is already there
 *   5. Separate MIUI path: detects com.miui.securitycenter and applies same logic
 *
 * THREAD SAFETY:
 *   All WindowManager operations are dispatched to the Main thread via Handler.
 *   State flags (isRunning, isFullOverlay) are volatile for cross-thread reads.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class SettingsBlockOverlayService extends Service {

    private static final String TAG = "SettingsBlockOverlay";

    // ── Intent actions ────────────────────────────────────────────────────────
    /** Show the bottom-only blocker (settings opened) */
    public static final String ACTION_SHOW_BOTTOM  = "ACTION_SETTINGS_BLOCK_BOTTOM";
    /** Expand to full screen (uninstaller page confirmed) */
    public static final String ACTION_SHOW_FULL    = "ACTION_SETTINGS_BLOCK_FULL";
    /** Shrink back to bottom-only (non-dangerous page) */
    public static final String ACTION_SHRINK_BOTTOM = "ACTION_SETTINGS_BLOCK_SHRINK";
    /** Hide everything (settings closed) */
    public static final String ACTION_HIDE         = "ACTION_SETTINGS_BLOCK_HIDE";

    // ── Public state — read by WatchdogService & AppBlockHandler ─────────────
    public static volatile boolean isRunning     = false;
    public static volatile boolean isFullOverlay = false;

    // ── Internal state ────────────────────────────────────────────────────────
    private WindowManager windowManager;
    private View          overlayView;        // single view; we animate its height
    private WindowManager.LayoutParams overlayParams;

    // ── NEW: Transparent full-screen touch blocker ────────────────────────────
    // Sits behind the animated overlay. Consumes ALL taps during the shrink
    // animation so the uninstall button can never be reached mid-frame.
    private View touchBlockerView;
    private WindowManager.LayoutParams touchBlockerParams;

    private Handler mainHandler;

    private int screenWidth;
    private int screenHeight;

    private static final int INITIAL_BLOCKER_DP  = 650;  // covers action buttons area
    private static final int EXPLORING_SHRINK_DP = 80;  // Other details/non-critical pages


    private static final int DELAY_EXPLORING_MS = 2000; // Your requested 5-second delay
    private static final int ANIMATE_DURATION_MS = 350;

    // ── Static helpers ────────────────────────────────────────────────────────

    /** Called by WatchdogService when it detects settings is open */
    public static void showBottom(Context context) {
        Intent i = new Intent(context, SettingsBlockOverlayService.class);
        i.setAction(ACTION_SHOW_BOTTOM);
        context.startForegroundService(i);
    }

    /** Called by page reader when uninstaller page confirmed */
    public static void expandFull(Context context) {
        Intent i = new Intent(context, SettingsBlockOverlayService.class);
        i.setAction(ACTION_SHOW_FULL);
        context.startForegroundService(i);
    }

    /** Called by page reader when safe page detected (not uninstaller) */
    public static void shrinkToBottom(Context context) {
        Intent i = new Intent(context, SettingsBlockOverlayService.class);
        i.setAction(ACTION_SHRINK_BOTTOM);
        context.startForegroundService(i);
    }

    /** Called by WatchdogService when settings is closed — ONLY way to remove overlay */
    public static void hide(Context context) {
        Intent i = new Intent(context, SettingsBlockOverlayService.class);
        i.setAction(ACTION_HIDE);
        context.startService(i); // not foreground — we're about to stop
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mainHandler   = new Handler(Looper.getMainLooper());

        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        screenWidth  = dm.widthPixels;
        screenHeight = dm.heightPixels;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Called after system restart — just stop cleanly
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        // ── CRITICAL FIX: Call startForeground() immediately ─────────────────
        // Android demands this within 5s of startForegroundService().
        // We must call it even if we're about to stop, then call stopForeground().
        try {
            startForeground(Constants.NOTIFICATION_ID_SETTINGS_BLOCK, buildNotification());
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }

        // If we are in Full Overlay, we stay there until "Go Home" is clicked
        if (isFullOverlay && !ACTION_HIDE.equals(action)) {
            return START_NOT_STICKY;
        }

        if (action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        switch (action) {

            case ACTION_SHOW_BOTTOM:
                if (!isRunning) { // Only start if not already running
                    isRunning = true;
                    // Transition to the 100dp standard bottom overlay
                    mainHandler.post(() -> animateToHeight(INITIAL_BLOCKER_DP, false));
                }
                break;

            case ACTION_SHOW_FULL:
                isFullOverlay = true;
                mainHandler.post(this::animateToFull);
                break;

            case ACTION_SHRINK_BOTTOM:
                // Add the 5 second delay before shrinking to 50dp
                mainHandler.postDelayed(() -> {
                    // Only shrink if we haven't moved to Full Screen in the meantime
                    if (!isFullOverlay && isRunning) {
                        animateToHeight(EXPLORING_SHRINK_DP, false);
                    }
                }, DELAY_EXPLORING_MS);
                break;

            case ACTION_HIDE:
                Log.d("MONK_DEBUG", "OverlayService: Executing ACTION_HIDE. Removing view now.");
                mainHandler.post(() -> {
                    removeOverlay();
                    stopForeground(true);
                    stopSelf();
                    isRunning = false;
                    isFullOverlay = false;
                });
                break;
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        removeTouchBlocker();
        removeOverlay();
        isRunning     = false;
        isFullOverlay = false;
    }

    // ── Overlay Management ────────────────────────────────────────────────────

    /**
     * Creates (or resets to) the bottom blocker state.
     * The overlay view is created once and reused — we only change its height.
     */
    private void applyBottomOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission — cannot show blocker");
            return;
        }

        float density    = getResources().getDisplayMetrics().density;
        int   bottomH    = (int)(INITIAL_BLOCKER_DP * density);

        if (overlayView == null) {
            // First time — create the view and add it
            overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    bottomH,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
            );
            overlayParams.gravity = Gravity.BOTTOM | Gravity.START;
            overlayParams.x = 0;
            overlayParams.y = 0;

            overlayView = buildOverlayView(false);

            try {
                windowManager.addView(overlayView, overlayParams);
                Log.i(TAG, "⚡ Bottom blocker shown");
            } catch (Exception e) {
                Log.e(TAG, "Failed to add overlay", e);
                overlayView = null;
            }

        } else {
            // Already exists — just resize to bottom height
            overlayParams.height  = bottomH;
            overlayParams.gravity = Gravity.BOTTOM | Gravity.START;
            overlayParams.flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            try {
                // Rebuild content for bottom state
                if (overlayView instanceof FrameLayout) {
                    ((FrameLayout) overlayView).removeAllViews();
                    ((FrameLayout) overlayView).addView(buildOverlayView(false));
                }
                windowManager.updateViewLayout(overlayView, overlayParams);
            } catch (Exception e) {
                Log.w(TAG, "updateViewLayout failed: " + e.getMessage());
            }
        }
    }

    /**
     * Animates the overlay from bottom height to full screen.
     * Direction: grows upward (gravity = BOTTOM).
     */
    private void animateToFull() {
        if (overlayView == null) {
            // Overlay not yet created — create it at full size directly
            applyFullOverlayDirect();
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int   startH  = (int)(INITIAL_BLOCKER_DP * density);
        int   endH    = screenHeight;

        android.animation.ValueAnimator anim =
                android.animation.ValueAnimator.ofInt(startH, endH);
        anim.setDuration(ANIMATE_DURATION_MS);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            if (overlayView == null) return;
            overlayParams.height = (int) a.getAnimatedValue();
            // Enable touch (so "Go Home" button works) halfway through
            if (overlayParams.height > screenHeight / 2) {
                overlayParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            }
            try { windowManager.updateViewLayout(overlayView, overlayParams); }
            catch (Exception ignored) {}
        });

        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // Swap content to full-screen view when animation completes
                if (overlayView instanceof FrameLayout) {
                    ((FrameLayout) overlayView).removeAllViews();
                    ((FrameLayout) overlayView).addView(buildOverlayView(true));
                }
                Log.i(TAG, "✅ Full overlay expanded");
            }
        });

        anim.start();
    }

    /**
     * Generic height animator for Bottom/Exploring states.
     */
    private void animateToHeight(int targetDp, boolean enableTouch) {
        // Ensure touch blocker is active before we start shrinking
        addTouchBlocker();

        if (overlayView == null) {
            applyBottomOverlay(); // Fallback to create view
            removeTouchBlocker(); // nothing to animate, remove immediately
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int startH = overlayParams.height;
        int endH = (int) (targetDp * density);

        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(startH, endH);
        anim.setDuration(ANIMATE_DURATION_MS);
        anim.setInterpolator(new DecelerateInterpolator());

        anim.addUpdateListener(a -> {
            if (overlayView == null) return;
            overlayParams.height = (int) a.getAnimatedValue();
            overlayParams.gravity = Gravity.BOTTOM | Gravity.START;
            overlayParams.x       = 0;
            overlayParams.y       = 0; // ← LOCK: bottom edge never moves
            overlayParams.flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS; // ← prevents system shift
            try { windowManager.updateViewLayout(overlayView, overlayParams); }
            catch (Exception ignored) {}
        });

        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // ← KEY FIX: release the touch blocker now that animation is settled
                removeTouchBlocker();
                Log.i(TAG, "🔓 Touch blocker released after animation settled");
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                // Also release if animation is interrupted (e.g. ACTION_HIDE mid-flight)
                removeTouchBlocker();
            }
        });

        anim.start();
    }

    /**
     * Directly shows full-screen overlay (no animation — called when overlay didn't exist yet).
     */
    private void applyFullOverlayDirect() {
        if (!Settings.canDrawOverlays(this)) return;

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                screenHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.BOTTOM | Gravity.START;

        overlayView = buildOverlayView(true);
        try {
            windowManager.addView(overlayView, overlayParams);
            Log.i(TAG, "✅ Full overlay shown directly");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add full overlay", e);
            overlayView = null;
        }
    }

    private void removeOverlay() {
        removeTouchBlocker(); // ← remove blocker first
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
        }
    }

    // ── View Builders ─────────────────────────────────────────────────────────

    /**
     * Builds the overlay content.
     *
     * @param isFull  true = full-screen with "Go Home" button
     *                false = bottom strip with shield label only
     */
    private View buildOverlayView(boolean isFull) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor(isFull ? "#F0080E1A" : "#E6080E1A"));

        if (isFull) {
            root.addView(buildFullContent());
        } else {
            root.addView(buildBottomContent());
        }

        return root;
    }

    private View buildBottomContent() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.TRANSPARENT);

        TextView label = new TextView(this);
        label.setText("🛡️  Protected by Digital Monk");
        label.setTextSize(13f);
        label.setTextColor(Color.parseColor("#94A3B8"));
        label.setGravity(Gravity.CENTER);
        label.setTypeface(null, Typeface.BOLD);
        label.setLetterSpacing(0.05f);

        layout.addView(label);
        return layout;
    }

    private View buildFullContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.TRANSPARENT);

        // Shield icon
        TextView shield = new TextView(this);
        shield.setText("🛡️");
        shield.setTextSize(56f);
        shield.setGravity(Gravity.CENTER);

        // Title
        TextView title = new TextView(this);
        title.setText("Protected by Digital Monk");
        title.setTextSize(22f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(48, 24, 48, 12);

        // Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("This page is restricted.\nA parent PIN is required to make changes here.");
        subtitle.setTextSize(15f);
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(48, 0, 48, 48);
        subtitle.setLineSpacing(6f, 1f);

        // "Go to Home Screen" button
        android.widget.Button homeBtn = new android.widget.Button(this);
        homeBtn.setText("← Go to Home Screen");
        homeBtn.setTextColor(Color.WHITE);
        homeBtn.setTextSize(16f);
        homeBtn.setTypeface(null, Typeface.BOLD);
        homeBtn.setBackgroundColor(Color.parseColor("#3B82F6"));
        homeBtn.setPadding(64, 28, 64, 28);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnLp.gravity = Gravity.CENTER_HORIZONTAL;
        btnLp.topMargin = 8;

        homeBtn.setOnClickListener(v -> {
            // 1. Send the user home
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);

            // 2. EXPLICITLY reset and hide because the user clicked the button
            isFullOverlay = false;
            hide(this);
        });

        root.addView(shield);
        root.addView(title);
        root.addView(subtitle);
        root.addView(homeBtn, btnLp);

        return root;
    }




    /**
     * Adds an invisible full-screen view that eats all touch events.
     * This is the KEY FIX: during height animation the visible overlay shrinks,
     * but this blocker stays full-screen so the uninstall button is NEVER reachable.
     */
    private void addTouchBlocker() {
        if (touchBlockerView != null) return; // already added
        if (!Settings.canDrawOverlays(this)) return;

        touchBlockerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // NOT_TOUCH_MODAL is intentionally absent — we WANT to intercept all touches
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        touchBlockerParams.gravity = Gravity.BOTTOM | Gravity.START;
        touchBlockerParams.x = 0;
        touchBlockerParams.y = 0;

        touchBlockerView = new View(this);
        touchBlockerView.setBackgroundColor(Color.TRANSPARENT);
        // Consume every touch silently
        touchBlockerView.setOnTouchListener((v, event) -> true);

        try {
            windowManager.addView(touchBlockerView, touchBlockerParams);
            Log.i(TAG, "🛡️ Touch blocker added");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add touch blocker", e);
            touchBlockerView = null;
        }
    }

    private void removeTouchBlocker() {
        if (touchBlockerView != null && windowManager != null) {
            try { windowManager.removeView(touchBlockerView); } catch (Exception ignored) {}
            touchBlockerView = null;
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, Constants.CHANNEL_ALERTS)
                .setContentTitle("Digital Monk — Settings Protected")
                .setContentText("Restricted page blocked")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}