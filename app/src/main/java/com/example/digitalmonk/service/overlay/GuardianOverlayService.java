package com.example.digitalmonk.service.overlay;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import android.animation.ValueAnimator;
import androidx.core.app.NotificationCompat;

import com.example.digitalmonk.core.utils.AccessibilityHealthChecker;
import com.example.digitalmonk.core.utils.Constants;
import com.example.digitalmonk.ui.dashboard.MainActivity;
import com.example.digitalmonk.service.accessibility.GuardianAccessibilityService;





public class GuardianOverlayService extends Service {

    private static final String TAG = "GuardianOverlayService";
    public static final String ACTION_START = "ACTION_GUARDIAN_START";
    public static final String ACTION_STOP  = "ACTION_GUARDIAN_STOP";

    // Extra: pass the failure reason so UI can customize the message
    public static final String EXTRA_IS_FROZEN = "extra_is_frozen";

    public static volatile boolean isRunning = false;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isMinimized = false;
    private boolean isFrozen = false;

    // Screen dimensions cached on start
    private int screenWidth, screenHeight;

    // ── Static helpers ────────────────────────────────────────────────────────

    public static void start(Context context, boolean isFrozen) {
        Intent intent = new Intent(context, GuardianOverlayService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_IS_FROZEN, isFrozen);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, GuardianOverlayService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        screenWidth  = dm.widthPixels;
        screenHeight = dm.heightPixels;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_STOP.equals(intent.getAction())) {
            removeOverlay();
            stopForeground(true);
            stopSelf();
            isRunning = false;
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(intent.getAction())) {
            isFrozen = intent.getBooleanExtra(EXTRA_IS_FROZEN, false);
            startForeground(Constants.NOTIFICATION_ID_OVERLAY, buildNotification());
            if (overlayView == null) {
                showOverlay();
            }
            isRunning = true;
            startMonitorLoop();
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        isRunning = false;
    }

    // ── Overlay Construction ──────────────────────────────────────────────────

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) return;

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                screenWidth,
                screenHeight,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        overlayView = buildFullscreenView();
        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to add overlay", e);
        }
    }

    /**
     * Builds the full-screen block UI.
     */
    private View buildFullscreenView() {
        // Root — dark semi-transparent background
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#F0080E1A")); // 94% opaque dark

        // ── Icon / emoji ──
        TextView icon = new TextView(this);
        icon.setText("🛡️");
        icon.setTextSize(56f);
        icon.setGravity(Gravity.CENTER);

        // ── Title ──
        TextView title = new TextView(this);
        title.setText(isFrozen ? "Guardian Service Needs Attention" : "Accessibility Permission Required");
        title.setTextSize(22f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(48, 24, 48, 8);

        // ── Subtitle ──
        TextView subtitle = new TextView(this);
        subtitle.setText(isFrozen
                ? "The Guardian service is enabled but not responding. Please toggle it OFF and ON again."
                : "Digital Monk needs Accessibility permission to block harmful content and short-form videos.");
        subtitle.setTextSize(15f);
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(48, 0, 48, 32);
        subtitle.setLineSpacing(6f, 1f);

        // ── Guide card ──
        LinearLayout guideCard = buildGuideCard();

        // ── Primary button ──
        Button primaryBtn = new Button(this);
        primaryBtn.setText(isFrozen ? "Open Accessibility Settings" : "Grant Permission");
        primaryBtn.setTextColor(Color.WHITE);
        primaryBtn.setTextSize(16f);
        primaryBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        styleButton(primaryBtn, Color.parseColor("#3B82F6"));
        primaryBtn.setOnClickListener(v -> openAccessibilitySettings());

        // ── Secondary button (only for frozen state) ──
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                (int)(screenWidth * 0.82f),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.gravity = Gravity.CENTER_HORIZONTAL;
        btnParams.topMargin = 16;
        btnParams.bottomMargin = 32;

        root.addView(icon);
        root.addView(title);
        root.addView(subtitle);
        root.addView(guideCard);
        root.addView(primaryBtn, btnParams);

        return root;
    }

    /**
     * Step-by-step guide card shown in the overlay.
     */
    private LinearLayout buildGuideCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1E293B"));

        // Rounded corners via padding
        card.setPadding(40, 32, 40, 32);
        int margin = (int)(screenWidth * 0.09f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(margin, 0, margin, 24);
        card.setLayoutParams(lp);

        TextView cardTitle = new TextView(this);
        cardTitle.setText(isFrozen ? "How to fix:" : "How to enable:");
        cardTitle.setTextSize(13f);
        cardTitle.setTextColor(Color.parseColor("#64748B"));
        cardTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        cardTitle.setAllCaps(true);
        cardTitle.setLetterSpacing(0.1f);
        card.addView(cardTitle);

        String[] steps = isFrozen
                ? new String[]{
                "1️⃣  Tap 'Open Accessibility Settings' below",
                "2️⃣  Tap on 'Downloaded Apps' below",
                "3️⃣  Find 'Digital Monk' in the list",
                "4️⃣  Tap it and toggle the switch OFF",
                "5️⃣  Wait 2 seconds, then toggle it ON again",
                "6️⃣  Return here — the block will auto-lift"
        }
                : new String[]{
                "1️⃣  Tap 'Grant Permission' below",
                "2️⃣  Tap on 'Downloaded Apps' below",
                "3️⃣  Find 'Digital Monk' in the list",
                "4️⃣  Tap 'Use Digital Monk'",
                "5️⃣  Tap 'Allow' on the confirmation dialog",
                "6️⃣  Return here — protection activates instantly"
        };

        for (String step : steps) {
            TextView stepView = new TextView(this);
            stepView.setText(step);
            stepView.setTextSize(14f);
            stepView.setTextColor(Color.parseColor("#CBD5E1"));
            stepView.setPadding(0, 10, 0, 0);
            stepView.setLineSpacing(4f, 1f);
            card.addView(stepView);
        }

        return card;
    }

    private void styleButton(Button btn, int bgColor) {
        btn.setBackgroundColor(bgColor);
        btn.setPadding(48, 24, 48, 24);
    }

    // ── Mini overlay (corner bubble) ──────────────────────────────────────────

    /**
     * Shrinks the overlay to a small corner guide when the user is on the
     * Accessibility settings page.
     */
    private View buildMiniView() {
        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.VERTICAL);
        mini.setGravity(Gravity.CENTER);
        mini.setBackgroundColor(Color.parseColor("#DD1E293B")); // dark card
        mini.setPadding(24, 20, 24, 20);

        TextView emoji = new TextView(this);
        emoji.setText("🛡️");
        emoji.setTextSize(24f);
        emoji.setGravity(Gravity.CENTER);

        TextView tip = new TextView(this);
        tip.setText(isFrozen ? "Toggle OFF → ON" : "Enable Digital Monk");
        tip.setTextSize(12f);
        tip.setTextColor(Color.parseColor("#94A3B8"));
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, 6, 0, 0);

        mini.addView(emoji);
        mini.addView(tip);
        return mini;
    }

    // ── Animate between full-screen and mini ──────────────────────────────────

    private static final int MINI_WIDTH  = 180; // dp converted below
    private static final int MINI_HEIGHT = 120;

    private void animateToMini() {
        if (isMinimized || overlayView == null) return;
        isMinimized = true;

        float density = getResources().getDisplayMetrics().density;
        int targetW = (int)(MINI_WIDTH  * density);
        int targetH = (int)(MINI_HEIGHT * density);

        ValueAnimator widthAnim  = ValueAnimator.ofInt(params.width,  targetW);
        ValueAnimator heightAnim = ValueAnimator.ofInt(params.height, targetH);

        widthAnim.setDuration(350);
        widthAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        widthAnim.addUpdateListener(a -> {
            params.width = (int) a.getAnimatedValue();
            // Reposition to bottom-start corner
            params.x = 24;
            params.y = screenHeight - params.height - 200;
            // Change flags to allow touches on the settings page behind
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            try { windowManager.updateViewLayout(overlayView, params); } catch (Exception ignored) {}
        });

        heightAnim.setDuration(350);
        heightAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        heightAnim.addUpdateListener(a -> {
            params.height = (int) a.getAnimatedValue();
            try { windowManager.updateViewLayout(overlayView, params); } catch (Exception ignored) {}
        });

        // Swap the content after animation starts
        handler.postDelayed(() -> {
            if (overlayView instanceof LinearLayout) {
                ((LinearLayout) overlayView).removeAllViews();
                View mini = buildMiniView();
                ((LinearLayout) overlayView).addView(mini);
            }
        }, 200);

        widthAnim.start();
        heightAnim.start();
    }

    private void animateToFull() {
        if (!isMinimized || overlayView == null) return;
        isMinimized = false;

        // Swap content first
        if (overlayView instanceof LinearLayout) {
            ((LinearLayout) overlayView).removeAllViews();
            // Re-build full content and add children
            View full = buildFullscreenView();
            if (full instanceof LinearLayout) {
                LinearLayout fullLL = (LinearLayout) full;
                LinearLayout target = (LinearLayout) overlayView;
                target.setBackgroundColor(Color.parseColor("#F0080E1A"));
                // Move all children
                while (fullLL.getChildCount() > 0) {
                    View child = fullLL.getChildAt(0);
                    fullLL.removeViewAt(0);
                    target.addView(child);
                }
            }
        }

        ValueAnimator widthAnim  = ValueAnimator.ofInt(params.width,  screenWidth);
        ValueAnimator heightAnim = ValueAnimator.ofInt(params.height, screenHeight);

        widthAnim.setDuration(350);
        widthAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        widthAnim.addUpdateListener(a -> {
            params.width = (int) a.getAnimatedValue();
            params.x = 0;
            params.y = 0;
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            try { windowManager.updateViewLayout(overlayView, params); } catch (Exception ignored) {}
        });

        heightAnim.setDuration(350);
        heightAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        heightAnim.addUpdateListener(a -> {
            params.height = (int) a.getAnimatedValue();
            try { windowManager.updateViewLayout(overlayView, params); } catch (Exception ignored) {}
        });

        widthAnim.start();
        heightAnim.start();
    }

    // ── Monitor loop: detect when user opens/leaves accessibility settings ────

    private static final long MONITOR_INTERVAL_MS = 800L;

    private final Runnable monitorRunnable = new Runnable() {
        @Override
        public void run() {
            checkAndAdapt();
            handler.postDelayed(this, MONITOR_INTERVAL_MS);
        }
    };

    private void startMonitorLoop() {
        handler.removeCallbacks(monitorRunnable);
        handler.post(monitorRunnable);
    }

    private void checkAndAdapt() {
        // 1. If accessibility is now healthy — dismiss the whole overlay
        if (!AccessibilityHealthChecker.needsLockdown(this)) {
            android.util.Log.i(TAG, "Accessibility healthy — removing lockdown");
            stop(this);
            return;
        }

        // 2. Detect if the current foreground window is the Accessibility settings
        boolean onSettingsPage = isAccessibilitySettingsVisible();

        if (onSettingsPage && !isMinimized) {
            animateToMini();
        } else if (!onSettingsPage && isMinimized) {
            animateToFull();
        }
    }

    /**
     * Checks if the Accessibility Settings page is currently visible.
     * We use GuardianAccessibilityService's last-seen package to detect this.
     * Since our service may be broken, we fall back to UsageStatsManager.
     */
    private boolean isAccessibilitySettingsVisible() {
        // Primary: use the last package seen by our accessibility service
        // (if it's working at all)
        String lastPkg = GuardianAccessibilityService.lastForegroundPackage;
        if (lastPkg != null && isSettingsPackage(lastPkg)) return true;

        // Fallback: UsageStatsManager (needs PACKAGE_USAGE_STATS permission)
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            if (usm != null) {
                long now = System.currentTimeMillis();
                java.util.List<android.app.usage.UsageStats> stats =
                        usm.queryUsageStats(
                                android.app.usage.UsageStatsManager.INTERVAL_BEST,
                                now - 3000, now);
                if (stats != null) {
                    android.app.usage.UsageStats mostRecent = null;
                    for (android.app.usage.UsageStats s : stats) {
                        if (mostRecent == null || s.getLastTimeUsed() > mostRecent.getLastTimeUsed()) {
                            mostRecent = s;
                        }
                    }
                    if (mostRecent != null && isSettingsPackage(mostRecent.getPackageName())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    private boolean isSettingsPackage(String pkg) {
        return "com.android.settings".equals(pkg)
                || "com.miui.securitycenter".equals(pkg)
                || (pkg != null && pkg.contains("settings"));
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Cannot open accessibility settings", e);
        }
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, Constants.CHANNEL_ALERTS)
                .setContentTitle("Action Required — Digital Monk")
                .setContentText("Accessibility permission needed for protection")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }
}