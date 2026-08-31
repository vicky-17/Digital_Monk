// UiDumper.java — place in com.example.digitalmonk.core.utils
package com.curbme.app.core.utils

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object UiDumper {
    private const val TAG = "MONK_UI_DUMP"

    /**
     * Call this from anywhere you have a root node.
     * Filter logcat with tag: MONK_UI_DUMP
     */
    @JvmStatic
    fun dumpAll(root: AccessibilityNodeInfo?, context: String?) {
        if (root == null) {
            Log.w(TAG, "[DUMP:" + context + "] root is null — no UI to dump")
            return
        }
        Log.d(TAG, "════════ UI DUMP [" + context + "] ════════")
        dumpNode(root, "", 0)
        Log.d(TAG, "════════ END DUMP ════════")
    }

    private fun dumpNode(node: AccessibilityNodeInfo?, indent: String?, depth: Int) {
        if (node == null || depth > 30) return  // guard against infinite recursion


        val viewId = node.getViewIdResourceName() // ← THE KEY: resource id
        val cls = if (node.getClassName() != null) node.getClassName().toString() else "?"
        val text = if (node.getText() != null) node.getText().toString() else ""
        val desc = if (node.getContentDescription() != null) node.getContentDescription()
            .toString() else ""
        val clickable = node.isClickable()
        val enabled = node.isEnabled()
        val visible = node.isVisibleToUser()

        // Only log nodes that have SOMETHING useful (id, text, or desc)
        if (viewId != null || !text.isEmpty() || !desc.isEmpty()) {
            Log.d(
                TAG, (indent
                        + "[id=" + (if (viewId != null) viewId else "—") + "]"
                        + " cls=" + cls
                        + " txt=\"" + text + "\""
                        + " desc=\"" + desc + "\""
                        + (if (clickable) " CLICKABLE" else "")
                        + (if (enabled) "" else " DISABLED")
                        + (if (visible) "" else " HIDDEN"))
            )
        }

        for (i in 0..<node.getChildCount()) {
            dumpNode(node.getChild(i), indent + "  ", depth + 1)
        }
    }
}