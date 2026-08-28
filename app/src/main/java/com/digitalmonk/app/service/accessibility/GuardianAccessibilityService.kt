package com.digitalmonk.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.digitalmonk.app.core.utils.UiDumper.dumpAll
import com.digitalmonk.app.data.local.prefs.DataStoreManager
import com.digitalmonk.app.data.local.prefs.Settings
import com.digitalmonk.app.service.accessibility.handlers.AppBlockHandler
import com.digitalmonk.app.service.accessibility.handlers.ShortsBlockHandler
import com.digitalmonk.app.service.monitor.AppUsageTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.concurrent.Volatile

class GuardianAccessibilityService : AccessibilityService() {
    private var dataStoreManager: DataStoreManager? = null
    private var settings = Settings()
    private val settingsFlow = MutableStateFlow(Settings())
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val eventChannel = Channel<AccessibilityEvent>(Channel.CONFLATED)
    
    private var shortsBlockHandler: ShortsBlockHandler? = null
    private var appBlockHandler: AppBlockHandler? = null
    private var appUsageTracker: AppUsageTracker? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        dataStoreManager = DataStoreManager(this)
        
        serviceScope.launch {
            dataStoreManager?.settings?.collect { newSettings ->
                settings = newSettings
                settingsFlow.value = newSettings
            }
        }

        serviceScope.launch(Dispatchers.Default) {
            eventChannel.receiveAsFlow().collect { event ->
                try {
                    processEvent(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing event", e)
                } finally {
                    event.recycle()
                }
            }
        }

        shortsBlockHandler = ShortsBlockHandler(
            ShortsBlockHandler.ActionPerformer { action: Int -> this.performGlobalAction(action) })
        appBlockHandler = AppBlockHandler(
            AppBlockHandler.ActionPerformer { action: Int -> this.performGlobalAction(action) })
        appUsageTracker = AppUsageTracker()
        appUsageTracker?.setup(this)
        serviceConnectedTimestamp = System.currentTimeMillis()
        lastEventTimestamp = 0L
        Log.i(TAG, "Guardian accessibility service connected")
    }

    override fun onInterrupt() {
        lastEventTimestamp = 0L
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        appUsageTracker?.onDestroy()
        appUsageTracker = null
        instance = null
        Log.w(TAG, "Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        lastEventTimestamp = System.currentTimeMillis()
        if (event == null) return
        appUsageTracker?.onEvent(event)
        
        val eventType = event.eventType
        val pkgSeq = event.packageName
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgSeq != null) {
            lastForegroundPackage = pkgSeq.toString()
        }

        // Conflate events: only process the latest one to avoid interaction timeouts
        val eventCopy = AccessibilityEvent.obtain(event)
        if (!eventChannel.trySend(eventCopy).isSuccess) {
            eventCopy.recycle()
        }
    }

    private fun processEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        val pkgSeq = event.packageName
        
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val root = rootInActiveWindow // Slow call
            if (root != null) {
                try {
                    if (findAndPerformBack(root)) return
                } finally {
                    root.recycle() // CRITICAL: Recycle to avoid leaks and hangs
                }
            }
        }

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        if (pkgSeq == null) return
        val pkg = pkgSeq.toString()
        if (pkg == packageName) return

        val root = rootInActiveWindow
        if (root != null) {
            try {
                if (DEBUG_DUMP_UI && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    dumpAll(root, pkg)
                }

                shortsBlockHandler?.handle(root, pkg, settings)
                appBlockHandler?.handle(root, pkg, eventType, settings)
            } finally {
                root.recycle() // CRITICAL: Recycle to avoid leaks and hangs
            }
        }
    }

    private fun findAndPerformBack(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (!settings.isAntiUninstallEnabled) return false

        if (!hasText(root, "Digital Monk")) return false

        val dangerous =
            hasText(root, "Force stop")
                    || hasText(root, "Erase all data (factory reset)")
                    || hasText(root, "Deactivate this device admin app")
                    || hasText(root, "Use Digital Monk")
                    || hasText(root, "Battery details")
                    || hasText(root, "VPN")



        if (dangerous) {
            Log.w(TAG, "🔒 Dangerous page for Digital Monk → firing BACK")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return true
        }

        return false
    }

    private fun hasText(root: AccessibilityNodeInfo, text: String?): Boolean {
        try {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            return nodes != null && !nodes.isEmpty()
        } catch (e: Exception) {
            return false
        }
    } //    private void logViewHierarchy(AccessibilityNodeInfo nodeInfo, int depth) {
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


    companion object {
        private const val TAG = "GuardianService"

        @JvmStatic
        fun disableService() {
            instance?.disableSelf()
        }

        @JvmField
        @Volatile
        var lastEventTimestamp: Long = 0L

        @JvmField
        @Volatile
        var serviceConnectedTimestamp: Long = 0L

        @Volatile
        var lastForegroundPackage: String? = null

        @Volatile
        var DEBUG_DUMP_UI: Boolean = false

        @Volatile
        var instance: GuardianAccessibilityService? = null
            private set
        @JvmStatic
        val currentRootNode: AccessibilityNodeInfo?
            get() {
                val svc: GuardianAccessibilityService? =
                    instance
                if (svc == null) return null
                try {
                    return svc.getRootInActiveWindow()
                } catch (e: Exception) {
                    return null
                }
            }
    }
}