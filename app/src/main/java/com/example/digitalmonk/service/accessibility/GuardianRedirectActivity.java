package com.example.digitalmonk.service.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

import com.example.digitalmonk.service.accessibility.GuardianAccessibilityService;
import com.example.digitalmonk.service.overlay.SettingsBlockOverlayService;

/**
 * GuardianRedirectActivity
 * ─────────────────────────────────────────────────────────────────────────────
 * Launched by SettingsPageReader when a dangerous settings page is detected.
 *
 * WHY AN ACTIVITY?
 *   - Starting an Activity does NOT require SYSTEM_ALERT_WINDOW permission
 *   - Once our Activity is in the foreground, we own the window —
 *     the dangerous settings page is already behind us
 *   - From here we can safely fire RECENTS + CLEAR without any root node
 *     reliability issues (accessibility now sees OUR window, not settings)
 *
 * FLOW:
 *   onCreate → immediately show a blank dark screen (child sees nothing useful)
 *           → fire HOME (0ms)
 *           → fire RECENTS (150ms)
 *           → scan + click Clear all Recents (500ms)
 *           → fire HOME again (700ms)
 *           → finish() this activity (900ms)
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class GuardianRedirectActivity extends Activity {

    private static final String TAG = "GuardianRedirect";

    private static final long DELAY_RECENTS_MS   = 150L;
    private static final long DELAY_CLEAR_MS     = 500L;
    private static final long DELAY_FINAL_HOME   = 700L;
    private static final long DELAY_FINISH       = 900L;

    private static final String CLEAR_RECENTS_DESC = "Clear all Recents";

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full black screen — no layout needed, child sees nothing
        getWindow().getDecorView().setBackgroundColor(android.graphics.Color.BLACK);
        // Remove title bar
        if (getActionBar() != null) getActionBar().hide();

        Log.w(TAG, "GuardianRedirectActivity launched — starting escape");

        runEscapeSequence();
    }

    private void runEscapeSequence() {
        GuardianAccessibilityService svc = GuardianAccessibilityService.getInstance();

        if (svc == null) {
            Log.e(TAG, "Accessibility service null — finishing with home intent");
            fallbackAndFinish();
            return;
        }


        // ── Step 2: RECENTS (150ms) ───────────────────────────────────────────
        handler.postDelayed(() -> {
            Log.d(TAG, "Step 2: RECENTS");
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
        }, DELAY_RECENTS_MS);

        // ── Step 3: CLEAR ALL RECENTS (500ms) ────────────────────────────────
        handler.postDelayed(() -> {
            Log.d(TAG, "Step 3: scanning for Clear all Recents");
            boolean cleared = attemptClearRecents(svc);
            Log.d(TAG, "Clear result: " + cleared);
        }, DELAY_CLEAR_MS);

        // ── Step 4: FINAL HOME (700ms) ────────────────────────────────────────
        handler.postDelayed(() -> {
            Log.d(TAG, "Step 4: final HOME");
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            SettingsBlockOverlayService.hide(getApplicationContext());
        }, DELAY_FINAL_HOME);

        // ── Step 5: FINISH this activity (900ms) ──────────────────────────────
        handler.postDelayed(() -> {
            Log.i(TAG, "✅ Escape complete — finishing activity");
            finish();
        }, DELAY_FINISH);
    }

    // ── Clear Recents ─────────────────────────────────────────────────────────

    private boolean attemptClearRecents(GuardianAccessibilityService svc) {
        try {
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            if (root == null) {
                Log.w(TAG, "Root null when scanning for Clear Recents");
                return false;
            }

            // Primary: search by content description
            // Confirmed from your MIUI dump:
            //   cls=android.widget.Button desc="Clear all Recents" CLICKABLE
            List<AccessibilityNodeInfo> candidates =
                    root.findAccessibilityNodeInfosByText(CLEAR_RECENTS_DESC);

            if (candidates != null && !candidates.isEmpty()) {
                for (AccessibilityNodeInfo node : candidates) {
                    if (clickNodeOrParent(node)) return true;
                }
            }

            // Fallback: DFS traversal
            AccessibilityNodeInfo found = findNodeByDesc(root, CLEAR_RECENTS_DESC);
            if (found != null) return clickNodeOrParent(found);

        } catch (Exception e) {
            Log.e(TAG, "Error scanning for Clear Recents", e);
        }
        return false;
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        if (node == null) return false;

        if (node.isClickable() && node.isEnabled()) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.i(TAG, "✅ Clicked node: " + node.getClassName());
            return true;
        }

        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null && parent.isClickable() && parent.isEnabled()) {
            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.i(TAG, "✅ Clicked parent: " + parent.getClassName());
            return true;
        }

        return false;
    }

    private AccessibilityNodeInfo findNodeByDesc(AccessibilityNodeInfo node, String desc) {
        if (node == null) return null;
        CharSequence d = node.getContentDescription();
        if (d != null && desc.equals(d.toString())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findNodeByDesc(node.getChild(i), desc);
            if (result != null) return result;
        }
        return null;
    }

    private void fallbackAndFinish() {
        android.content.Intent home = new android.content.Intent(
                android.content.Intent.ACTION_MAIN);
        home.addCategory(android.content.Intent.CATEGORY_HOME);
        home.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        SettingsBlockOverlayService.hide(getApplicationContext());
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}