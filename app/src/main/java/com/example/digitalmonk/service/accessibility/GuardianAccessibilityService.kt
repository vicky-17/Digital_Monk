package com.example.digitalmonk.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.digitalmonk.core.utils.Logger
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.accessibility.handlers.AppBlockHandler
import com.example.digitalmonk.service.accessibility.handlers.ShortsBlockHandler

/**
 * Digital Monk's single AccessibilityService.
 *
 * Designed as a dispatcher — it receives events and delegates to
 * feature-specific handlers. Adding a new enforcement feature = add a
 * new Handler class; this file stays untouched.
 *
 * Handlers are initialised lazily in [onServiceConnected] to guarantee
 * that [Context] is available.
 *
 * ┌─ onAccessibilityEvent ─────────────────────────────────────────┐
 * │   ShortsBlockHandler.handle()   ← Phase 1 (done)              │
 * │   AppBlockHandler.handle()      ← Phase 2                      │
 * │   ScreenTimeHandler.handle()    ← Phase 3                      │
 * │   KeywordBlockHandler.handle()  ← Future                       │
 * └────────────────────────────────────────────────────────────────┘
 */
class GuardianAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PrefsManager
    private lateinit var shortsBlockHandler: ShortsBlockHandler
    private lateinit var appBlockHandler: AppBlockHandler

    override fun onServiceConnected() {
        prefs = PrefsManager(this)
        shortsBlockHandler = ShortsBlockHandler(prefs, ::performGlobalAction)
        appBlockHandler    = AppBlockHandler(prefs, ::performGlobalAction)
        Logger.i(TAG, "Guardian service connected ✅")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == applicationContext.packageName) return   // never touch our own UI

        val root = rootInActiveWindow

        // Dispatch to all handlers — each decides internally whether to act
        shortsBlockHandler.handle(root, pkg)
        appBlockHandler.handle(root, pkg)
        // Add more handlers here as features are built
    }

    override fun onInterrupt() {
        Logger.w(TAG, "Service interrupted")
    }

    companion object {
        private const val TAG = "GuardianService"
    }
}