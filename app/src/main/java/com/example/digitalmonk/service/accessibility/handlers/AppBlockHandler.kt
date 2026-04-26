package com.example.digitalmonk.service.accessibility.handlers

import android.view.accessibility.AccessibilityNodeInfo
import com.example.digitalmonk.core.utils.Logger
import com.example.digitalmonk.data.local.prefs.PrefsManager

/**
 * Phase 2 — App Blocking handler.
 *
 * When a blocked app is opened, this handler fires [GLOBAL_ACTION_HOME]
 * to immediately return the user to the home screen.
 *
 * TODO Phase 2:
 *  - Wire overlay service to show a "This app is blocked" message before going home.
 *  - Read blocked packages from Room DB (AppRuleDao) instead of SharedPrefs
 *    for real-time updates without service restart.
 */
class AppBlockHandler(
    private val prefs: PrefsManager,
    private val performAction: (Int) -> Boolean
) {

    fun handle(rootNode: AccessibilityNodeInfo?, packageName: String) {
        if (!prefs.isAppBlocked(packageName)) return

        Logger.d(TAG, "🚫 Blocked app launched: $packageName — sending HOME")
        performAction(GLOBAL_ACTION_HOME)

        // Future: trigger overlay service to show "App is blocked" UI
    }

    companion object {
        private const val TAG = "AppBlockHandler"
        private const val GLOBAL_ACTION_HOME = 2  // AccessibilityService.GLOBAL_ACTION_HOME
    }
}