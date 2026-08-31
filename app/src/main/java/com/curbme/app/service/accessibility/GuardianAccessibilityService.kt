package com.curbme.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.core.utils.UiDumper.dumpAll
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.service.accessibility.handlers.AppBlockHandler
import com.curbme.app.service.accessibility.handlers.ShortsBlockHandler
import com.curbme.app.service.monitor.AppUsageTracker
import com.curbme.app.ui.block.AppBlockOverlayManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.concurrent.Volatile

class GuardianAccessibilityService : AccessibilityService() {
    private var dataStoreManager: DataStoreManager? = null
    private var settings = Settings()
    private val settingsFlow = MutableStateFlow(Settings())
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val eventChannel = Channel<AccessibilityEventInfo>(Channel.CONFLATED)
    
    private data class AccessibilityEventInfo(
        val eventType: Int,
        val packageName: String?
    )
    
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
            eventChannel.receiveAsFlow().collect { eventInfo ->
                try {
                    processEvent(eventInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing event", e)
                }
            }
        }

        shortsBlockHandler = ShortsBlockHandler(
            ShortsBlockHandler.ActionPerformer { action: Int -> this.performGlobalAction(action) })
        appBlockHandler = AppBlockHandler(
            AppBlockHandler.ActionPerformer { action: Int -> this.performGlobalAction(action) })
        
        // Use existing tracker if available, otherwise setup a new one
        appUsageTracker = AppUsageTracker.instance ?: AppUsageTracker().apply { setup(this@GuardianAccessibilityService) }
        
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

        val eventType = event.eventType
        val pkgSeq = event.packageName
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgSeq != null) {
            val pkgName = pkgSeq.toString()
            lastForegroundPackage = pkgName
            // Hub & Spoke: Update central state immediately for fast notifications
            com.curbme.app.service.monitor.MonitorState.updateForegroundApp(pkgName)
            // System A: Notify block engine for immediate enforcement
            com.curbme.app.service.monitor.AppBlockEngineService.onAppSwitched(pkgName)
        }

        // Conflate events: only process the latest one to avoid interaction timeouts
        val eventInfo = AccessibilityEventInfo(event.eventType, event.packageName?.toString())
        eventChannel.trySend(eventInfo)
    }

    private fun processEvent(event: AccessibilityEventInfo) {
        val eventType = event.eventType
        val pkgSeq = event.packageName
        
        // ── Block Escape Suppression ──────────────────────────────────────────
        if (AppBlockOverlayManager.isOverlayShowing) {
            val pkg = pkgSeq ?: ""
            // If user tries to open Recents or System settings while overlay is up
            if (pkg == "com.android.systemui" || pkg == "com.android.settings") {
                Log.w(TAG, "Suppression: Forced HOME while overlay is showing")
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val root = rootInActiveWindow // Slow call
            if (root != null) {
                if (findAndPerformBack(root)) return
            }
        }

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        if (pkgSeq == null) return
        val pkg = pkgSeq
        if (pkg == packageName) return

        val root = rootInActiveWindow
        if (root != null) {
            if (DEBUG_DUMP_UI && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                dumpAll(root, pkg)
            }

            shortsBlockHandler?.handle(root, pkg, settings)
            appBlockHandler?.handle(root, pkg, eventType, settings)
        }
    }

    private fun findAndPerformBack(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (!settings.isAntiUninstallEnabled) return false

        if (!hasText(root, "CurbMe")) return false

        val dangerous =
            hasText(root, "Force stop")
                    || hasText(root, "Erase all data (factory reset)")
                    || hasText(root, "Deactivate this device admin app")
                    || hasText(root, "Use CurbMe")
                    || hasText(root, "Battery details")
                    || hasText(root, "VPN")



        if (dangerous) {
            Log.w(TAG, "🔒 Dangerous page for CurbMe → firing BACK")
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
        fun disableService(context: Context? = null) {
            val svc = instance
            if (svc != null) {
                try {
                    svc.disableSelf()
                    Log.i(TAG, "disableSelf() invoked on GuardianAccessibilityService instance")
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking disableSelf()", e)
                }
            }

            if (context != null) {
                try {
                    val hasWriteSecure = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.WRITE_SECURE_SETTINGS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (hasWriteSecure) {
                        val cr = context.contentResolver
                        val enabledServices = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                        val expected = android.content.ComponentName(context, GuardianAccessibilityService::class.java).flattenToString()
                        if (enabledServices.contains(expected)) {
                            val updated = enabledServices.split(":")
                                .filter { it.isNotBlank() && it != expected }
                                .joinToString(":")
                            android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
                            Log.i(TAG, "Accessibility service revoked via WRITE_SECURE_SETTINGS")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error disabling accessibility via Settings.Secure", e)
                }
            }
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