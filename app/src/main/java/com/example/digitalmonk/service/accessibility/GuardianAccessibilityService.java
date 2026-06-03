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
    public static volatile long   lastEventTimestamp        = 0L;
    public static volatile long   serviceConnectedTimestamp = 0L;
    public static volatile String lastForegroundPackage     = null;
    public static volatile boolean DEBUG_DUMP_UI = false;
    private static volatile GuardianAccessibilityService instance = null;
    private PrefsManager       prefs;
    private ShortsBlockHandler shortsBlockHandler;
    private AppBlockHandler    appBlockHandler;

    public static AccessibilityNodeInfo getCurrentRootNode() {
        GuardianAccessibilityService svc = instance;
        if (svc == null) return null;
        try {
            return svc.getRootInActiveWindow();
        } catch (Exception e) {
            return null;
        }
    }

    public static GuardianAccessibilityService getInstance() {
        return instance;
    }
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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        lastEventTimestamp = System.currentTimeMillis();
        if (event == null) return;
        int eventType = event.getEventType();
        CharSequence pkgSeq = event.getPackageName();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgSeq != null) {
            lastForegroundPackage = pkgSeq.toString();
        }
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
//                logViewHierarchy(root, 0);
                if (findAndPerformBack(root)) return;
            }
        }
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();
        if (pkg.equals(getApplicationContext().getPackageName())) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (DEBUG_DUMP_UI && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            UiDumper.dumpAll(root, pkg);
        }

        shortsBlockHandler.handle(root, pkg);
        appBlockHandler.handle(root, pkg, eventType, getApplicationContext());
    }

    private boolean findAndPerformBack(AccessibilityNodeInfo root) {
        if (root == null) return false;

        if (!hasText(root, "Digital Monk"))
            return false;

        boolean dangerous =
                hasText(root, "Force stop")
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



//    private void logViewHierarchy(AccessibilityNodeInfo nodeInfo, int depth) {
//        if (nodeInfo == null) return;
//        StringBuilder prefix = new StringBuilder();
//        for (int i = 0; i < depth; i++) {
//            prefix.append("  ");
//        }
//        Log.d(TAG, prefix.toString() + nodeInfo.toString());
//        for (int i = 0; i < nodeInfo.getChildCount(); i++) {
//            AccessibilityNodeInfo child = nodeInfo.getChild(i);
//            if (child != null) {
//                logViewHierarchy(child, depth + 1);
//            }
//        }
//    }


}