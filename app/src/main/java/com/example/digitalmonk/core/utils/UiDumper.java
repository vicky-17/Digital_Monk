// UiDumper.java — place in com.example.digitalmonk.core.utils
package com.example.digitalmonk.core.utils;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

public class UiDumper {

    private static final String TAG = "MONK_UI_DUMP";

    /**
     * Call this from anywhere you have a root node.
     * Filter logcat with tag: MONK_UI_DUMP
     */
    public static void dumpAll(AccessibilityNodeInfo root, String context) {
        if (root == null) {
            Log.w(TAG, "[DUMP:" + context + "] root is null — no UI to dump");
            return;
        }
        Log.d(TAG, "════════ UI DUMP [" + context + "] ════════");
        dumpNode(root, "", 0);
        Log.d(TAG, "════════ END DUMP ════════");
    }

    private static void dumpNode(AccessibilityNodeInfo node, String indent, int depth) {
        if (node == null || depth > 30) return; // guard against infinite recursion

        String viewId   = node.getViewIdResourceName(); // ← THE KEY: resource id
        String cls      = node.getClassName() != null ? node.getClassName().toString() : "?";
        String text     = node.getText()      != null ? node.getText().toString()      : "";
        String desc     = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        boolean clickable  = node.isClickable();
        boolean enabled    = node.isEnabled();
        boolean visible    = node.isVisibleToUser();

        // Only log nodes that have SOMETHING useful (id, text, or desc)
        if (viewId != null || !text.isEmpty() || !desc.isEmpty()) {
            Log.d(TAG, indent
                    + "[id="    + (viewId != null ? viewId : "—") + "]"
                    + " cls="   + cls
                    + " txt=\"" + text + "\""
                    + " desc=\""+ desc + "\""
                    + (clickable ? " CLICKABLE" : "")
                    + (enabled   ? "" : " DISABLED")
                    + (visible   ? "" : " HIDDEN")
            );
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            dumpNode(node.getChild(i), indent + "  ", depth + 1);
        }
    }
}