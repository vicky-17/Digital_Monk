package com.example.digitalmonk.service.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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

import com.example.digitalmonk.core.utils.Constants;






public class SettingsBlockOverlayService extends Service
        implements androidx.lifecycle.LifecycleOwner {

    @NonNull
    @Override
    public androidx.lifecycle.Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    private static final String TAG = "SettingsBlockOverlay";

    // ─────────────────────────────────────────────────────────────────────────
    // Intent Actions  (callers use the static helpers below, not raw intents)
    // ─────────────────────────────────────────────────────────────────────────

    /** WatchdogService → settings app opened: show bottom strip */
    public static final String ACTION_SHOW_BOTTOM   = "ACTION_SETTINGS_BLOCK_BOTTOM";

    /** SettingsPageReader → uninstaller page confirmed: expand to full screen */
    public static final String ACTION_SHOW_FULL     = "ACTION_SETTINGS_BLOCK_FULL";

    /** SettingsPageReader → safe page detected: shrink back to bottom strip */
    public static final String ACTION_SHRINK_BOTTOM = "ACTION_SETTINGS_BLOCK_SHRINK";

    /** WatchdogService → settings app closed: remove overlay entirely */
    public static final String ACTION_HIDE          = "ACTION_SETTINGS_BLOCK_HIDE";

    // ─────────────────────────────────────────────────────────────────────────
    // Public State  (read by WatchdogService / AppBlockHandler)
    // ─────────────────────────────────────────────────────────────────────────

    /** True while any overlay layer is visible */
    public static volatile boolean isRunning     = false;

    /** True when the full-screen layer is active */
    public static volatile boolean isFullOverlay = false;

    /**
     * DEBUG — set true to suppress all overlay drawing.
     * Lets you navigate Settings freely while UiDumper logs the screen tree.
     * Remember to set back to false before release.
     */
    public static volatile boolean DEBUG_SUPPRESS_OVERLAY = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Layout / Animation Constants
    // ─────────────────────────────────────────────────────────────────────────

    /** Height of the bottom strip in dp — sized to cover Force Stop / Uninstall buttons */
    private static final int INITIAL_BLOCKER_DP  = 650;

    /** Height in dp after shrinking when a non-dangerous settings page is detected */
    private static final int EXPLORING_SHRINK_DP = 80;

    /** How long to wait (ms) before shrinking when a safe page is detected */
    private static final int DELAY_EXPLORING_MS  = 2_000;

    /** Duration of all height animations */
    private static final int ANIMATE_DURATION_MS = 350;

    // ─────────────────────────────────────────────────────────────────────────
    // Internal State
    // ─────────────────────────────────────────────────────────────────────────

    private WindowManager              windowManager;
    private Handler                    mainHandler;

    /** The single animated overlay view (reused across state changes) */
    private View                       overlayView;
    private WindowManager.LayoutParams overlayParams;

    /**
     * Transparent full-screen view added during shrink animations.
     * Consumes all touches so the uninstall button is unreachable mid-frame.
     */
    private View                       touchBlockerView;
    private WindowManager.LayoutParams touchBlockerParams;

    private int screenWidth;
    private int screenHeight;


    // ── Compose fields ────────────────────────────────────────────────────────
    private androidx.lifecycle.LifecycleRegistry lifecycleRegistry;
    private androidx.compose.runtime.MutableState<OverlayState> overlayState;

    // =========================================================================
    // Static Helpers  (called by WatchdogService / SettingsPageReader)
    // =========================================================================

    /** Settings app opened — show the bottom strip */
    public static void showBottom(Context ctx) {
        ctx.startForegroundService(intentFor(ctx, ACTION_SHOW_BOTTOM));
    }

    /** Uninstaller page confirmed — expand to full screen */
    public static void expandFull(Context ctx) {
        ctx.startForegroundService(intentFor(ctx, ACTION_SHOW_FULL));
    }

    /** Safe page detected — shrink back to bottom strip */
    public static void shrinkToBottom(Context ctx) {
        ctx.startForegroundService(intentFor(ctx, ACTION_SHRINK_BOTTOM));
    }

    /** Settings app closed — remove overlay (the ONLY removal path) */
    public static void hide(Context ctx) {
        // Plain startService: we are about to stop, no foreground needed
        ctx.startService(intentFor(ctx, ACTION_HIDE));
    }

    private static Intent intentFor(Context ctx, String action) {
        return new Intent(ctx, SettingsBlockOverlayService.class).setAction(action);
    }

    // =========================================================================
    // Service Lifecycle
    // =========================================================================

    @Override
    public void onCreate() {

        lifecycleRegistry = new androidx.lifecycle.LifecycleRegistry(this);
        lifecycleRegistry.setCurrentState(androidx.lifecycle.Lifecycle.State.CREATED);

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
        // Android requires startForeground() within 5 s of startForegroundService().
        // Call it immediately, even when we might stop right away.
        try {
            startForeground(Constants.NOTIFICATION_ID_SETTINGS_BLOCK, buildNotification());
            lifecycleRegistry.setCurrentState(androidx.lifecycle.Lifecycle.State.STARTED);
            lifecycleRegistry.setCurrentState(androidx.lifecycle.Lifecycle.State.RESUMED);
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Full-screen lock: once we are full, ignore everything except HIDE
        if (isFullOverlay && !ACTION_HIDE.equals(action)) {
            return START_NOT_STICKY;
        }

        // Debug mode: suppress all overlay drawing (but still allow HIDE to clean up)
        if (DEBUG_SUPPRESS_OVERLAY && !ACTION_HIDE.equals(action)) {
            Log.d(TAG, "[DEBUG] Overlay suppressed — DEBUG_SUPPRESS_OVERLAY=true");
            return START_NOT_STICKY;
        }

        switch (action) {

            case ACTION_SHOW_BOTTOM:
                if (!isRunning) {
                    isRunning = true;
                    mainHandler.post(() -> animateToHeight(INITIAL_BLOCKER_DP, false));
                }
                break;

            case ACTION_SHOW_FULL:
                isFullOverlay = true;
                mainHandler.post(this::animateToFull);
                break;

            case ACTION_SHRINK_BOTTOM:
                // Delay gives the page reader time to confirm the navigation is final
                mainHandler.postDelayed(() -> {
                    if (!isFullOverlay && isRunning) {
                        animateToHeight(EXPLORING_SHRINK_DP, false);
                    }
                }, DELAY_EXPLORING_MS);
                break;

            case ACTION_HIDE:
                Log.d(TAG, "ACTION_HIDE received — removing overlay");
                mainHandler.post(() -> {
                    removeOverlay();
                    stopForeground(true);
                    stopSelf();
                    isRunning     = false;
                    isFullOverlay = false;
                });
                break;
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        lifecycleRegistry.setCurrentState(androidx.lifecycle.Lifecycle.State.DESTROYED);
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        removeOverlay(); // also removes touch blocker inside
        isRunning     = false;
        isFullOverlay = false;
    }

    // =========================================================================
    // Overlay Management
    // =========================================================================

    /**
     * Creates the overlay view if it doesn't exist yet, anchored to the bottom
     * at INITIAL_BLOCKER_DP height. If the view exists, resets it to that height.
     */
    private void applyBottomOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Missing SYSTEM_ALERT_WINDOW permission — overlay skipped");

            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int   bottomH = (int) (INITIAL_BLOCKER_DP * density);

        if (overlayView == null) {
            // ── First time: create and add the view ──────────────────────────
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

            overlayView = buildOverlayView(false);
            try {
                windowManager.addView(overlayView, overlayParams);
                Log.i(TAG, "Bottom blocker added to window");
            } catch (Exception e) {
                Log.e(TAG, "Failed to add overlay view", e);
                overlayView = null;
            }

        } else {
            // ── View exists: resize and refresh content ───────────────────────
            overlayParams.height  = bottomH;
            overlayParams.gravity = Gravity.BOTTOM | Gravity.START;
            overlayParams.flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

            if (overlayView instanceof FrameLayout) {
                FrameLayout fl = (FrameLayout) overlayView;
                fl.removeAllViews();
                fl.addView(buildOverlayView(false));
            }
            try {
                windowManager.updateViewLayout(overlayView, overlayParams);
            } catch (Exception e) {
                Log.w(TAG, "updateViewLayout failed: " + e.getMessage());
            }
        }
    }

    /**
     * Animates the overlay height from its current value to {@code targetDp}.
     * A transparent touch-blocker covers the whole screen during the animation
     * so no buttons beneath the shrinking overlay can be tapped.
     */
    private void animateToHeight(int targetDp, boolean enableTouch) {
        addTouchBlocker(); // must go on before animation starts

        if (overlayView == null) {
            applyBottomOverlay();
            removeTouchBlocker(); // nothing animated, release immediately
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int   startH  = overlayParams.height;
        int   endH    = (int) (targetDp * density);

        ValueAnimator anim = ValueAnimator.ofInt(startH, endH);
        anim.setDuration(ANIMATE_DURATION_MS);
        anim.setInterpolator(new DecelerateInterpolator());

        anim.addUpdateListener(a -> {
            if (overlayView == null) return;
            overlayParams.height  = (int) a.getAnimatedValue();
            overlayParams.gravity = Gravity.BOTTOM | Gravity.START;
            overlayParams.x       = 0;
            overlayParams.y       = 0; // keeps bottom edge fixed
            overlayParams.flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            try { windowManager.updateViewLayout(overlayView, overlayParams); }
            catch (Exception ignored) {}
        });

        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation)    { removeTouchBlocker(); }
            @Override public void onAnimationCancel(Animator animation) { removeTouchBlocker(); }
        });

        anim.start();
    }

    /**
     * Animates the overlay from INITIAL_BLOCKER_DP up to full screen height.
     * Touch is enabled halfway through so the "Go Home" button becomes tappable
     * only once the overlay is large enough to cover the whole screen.
     */
    private void animateToFull() {
        if (overlayView == null) {
            applyFullOverlayDirect();
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int   startH  = (int) (INITIAL_BLOCKER_DP * density);
        int   endH    = screenHeight;

        ValueAnimator anim = ValueAnimator.ofInt(startH, endH);
        anim.setDuration(ANIMATE_DURATION_MS);
        anim.setInterpolator(new DecelerateInterpolator());

        anim.addUpdateListener(a -> {
            if (overlayView == null) return;
            overlayParams.height = (int) a.getAnimatedValue();

            // Enable touch input once overlay covers the bottom half
            if (overlayParams.height > screenHeight / 2) {
                overlayParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            }
            try { windowManager.updateViewLayout(overlayView, overlayParams); }
            catch (Exception ignored) {}
        });

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Swap in the full-screen content (adds "Go Home" button)
                if (overlayView instanceof FrameLayout) {
                    FrameLayout fl = (FrameLayout) overlayView;
                    if (overlayState != null) overlayState.setValue(OverlayState.FULL);
                }
                Log.i(TAG, "Full overlay expanded");
            }
        });

        anim.start();
    }

    /**
     * Skips animation and immediately shows the full-screen overlay.
     * Used when ACTION_SHOW_FULL arrives before the view has been created.
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
            Log.i(TAG, "Full overlay added directly (no animation)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add full overlay", e);
            overlayView = null;
        }
    }

    /** Removes the animated overlay from the window (calls removeTouchBlocker first) */
    private void removeOverlay() {
        removeTouchBlocker();
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
        }
    }

    // =========================================================================
    // Touch Blocker
    // =========================================================================

    /**
     * Adds an invisible full-screen view that consumes every touch event.
     *
     * WHY: During a shrink animation the visible overlay gets smaller, briefly
     * exposing the area where the uninstall / force-stop button lives.
     * This blocker stays full-screen for the entire animation duration so those
     * buttons can never be reached mid-frame.
     *
     * It is always removed in onAnimationEnd / onAnimationCancel.
     */
    private void addTouchBlocker() {
        if (touchBlockerView != null) return; // already active
        if (!Settings.canDrawOverlays(this)) return;

        touchBlockerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Intentionally omit FLAG_NOT_TOUCH_MODAL — we want ALL touches
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        touchBlockerParams.gravity = Gravity.BOTTOM | Gravity.START;

        touchBlockerView = new View(this);
        touchBlockerView.setBackgroundColor(Color.TRANSPARENT);
        touchBlockerView.setOnTouchListener((v, e) -> true); // swallow everything

        try {
            windowManager.addView(touchBlockerView, touchBlockerParams);
            Log.d(TAG, "Touch blocker added");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add touch blocker", e);
            touchBlockerView = null;
        }
    }

    private void removeTouchBlocker() {
        if (touchBlockerView != null && windowManager != null) {
            try { windowManager.removeView(touchBlockerView); } catch (Exception ignored) {}
            touchBlockerView = null;
            Log.d(TAG, "Touch blocker removed");
        }
    }

    // =========================================================================
    // View Builders
    // =========================================================================

    /**
     * Returns the root FrameLayout for the overlay.
     *
     * @param isFull true  → full-screen layout with "Go Home" button
     *               false → compact bottom strip with shield label only
     */
    private OverlayLifecycleOwner overlayLifecycleOwner;

    private View buildOverlayView(boolean isFull) {
        OverlayState initialState = isFull ? OverlayState.FULL : OverlayState.BOTTOM;

        overlayLifecycleOwner = new OverlayLifecycleOwner();
        overlayLifecycleOwner.onCreate();
        overlayLifecycleOwner.onStart();
        overlayLifecycleOwner.onResume();

        androidx.compose.ui.platform.ComposeView composeView =
                new androidx.compose.ui.platform.ComposeView(this);

        ViewTreeLifecycleOwner.set(composeView, overlayLifecycleOwner);
        ViewTreeSavedStateRegistryOwner.set(composeView, overlayLifecycleOwner);

        // Keep a handle to update state later
        overlayState = androidx.compose.runtime.SnapshotStateKt
                .mutableStateOf(initialState, androidx.compose.runtime.SnapshotMutationPolicyKt.structuralEqualityPolicy());

        final androidx.compose.runtime.State<OverlayState> stateSnapshot = overlayState;

        composeView.setContent(() ->
                OverlayComposeContentKt.OverlayComposeContent(
                        stateSnapshot.getValue(),
                        () -> {
                            Intent home = new Intent(Intent.ACTION_MAIN);
                            home.addCategory(Intent.CATEGORY_HOME);
                            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(home);
                            hide(SettingsBlockOverlayService.this);
                            return null;
                        }
                )
        );

        FrameLayout root = new FrameLayout(this);
        root.addView(composeView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        return root;
    }

    /** Compact bottom strip — just the shield label */
    private View buildBottomContent() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);

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

    /** Full-screen layout — shield icon, title, subtitle, and "Go Home" button */
    private View buildFullContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);

        // ── Shield emoji ──────────────────────────────────────────────────────
        TextView shield = new TextView(this);
        shield.setText("🛡️");
        shield.setTextSize(56f);
        shield.setGravity(Gravity.CENTER);

        // ── Title ─────────────────────────────────────────────────────────────
        TextView title = new TextView(this);
        title.setText("Protected by Digital Monk");
        title.setTextSize(22f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(48, 24, 48, 12);

        // ── Subtitle ──────────────────────────────────────────────────────────
        TextView subtitle = new TextView(this);
        subtitle.setText("This page is restricted.\nA parent PIN is required to make changes here.");
        subtitle.setTextSize(15f);
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(48, 0, 48, 48);
        subtitle.setLineSpacing(6f, 1f);

        // ── "Go Home" button ──────────────────────────────────────────────────
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
        btnLp.gravity   = Gravity.CENTER_HORIZONTAL;
        btnLp.topMargin = 8;

        homeBtn.setOnClickListener(v -> {
            // Navigate home
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);

            // Reset state and tear down overlay
            isFullOverlay = false;
            hide(this);
        });

        root.addView(shield);
        root.addView(title);
        root.addView(subtitle);
        root.addView(homeBtn, btnLp);

        return root;
    }

    // =========================================================================
    // Foreground Notification  (required to keep the service alive)
    // =========================================================================

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, Constants.CHANNEL_SILENT)
                .setContentTitle("")
                .setContentText("")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSilent(true)
                .setShowWhen(false)
                .setOngoing(false)
                .build();
    }
}