package com.curbme.app.service.accessibility.handlers

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.core.utils.TimeUtils
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity
import com.curbme.app.service.accessibility.detectors.BrowserUrlReader
import kotlinx.coroutines.*

/**
 * Handles domain-level website usage tracking across supported mobile browsers.
 */
class WebsiteUsageHandler(private val context: Context) {

    private val websiteStatsDao = AppDatabase.getDatabase(context).websiteStatsDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeDomain: String? = null
    private var activePackage: String? = null
    private var domainStartTimeMs: Long = 0L

    fun handleEvent(rootNode: AccessibilityNodeInfo?, packageName: String?, isTrackingEnabled: Boolean = true) {
        if (!isTrackingEnabled || packageName == null) {
            flushSession()
            return
        }

        val domain = BrowserUrlReader.readDomain(rootNode, packageName)
        if (domain == null) {
            if (activePackage == packageName) {
                flushSession()
            }
            return
        }

        if (domain != activeDomain || packageName != activePackage) {
            flushSession()
            activeDomain = domain
            activePackage = packageName
            domainStartTimeMs = SystemClock.elapsedRealtime()
        } else {
            // Periodic flush every 15 seconds
            val now = SystemClock.elapsedRealtime()
            if (now - domainStartTimeMs >= 15_000L) {
                flushSession()
                activeDomain = domain
                activePackage = packageName
                domainStartTimeMs = now
            }
        }
    }

    fun onDestroy() {
        flushSession()
        scope.cancel()
    }

    private fun flushSession() {
        val domain = activeDomain ?: return
        val pkg = activePackage ?: return
        val startTime = domainStartTimeMs

        activeDomain = null
        activePackage = null
        domainStartTimeMs = 0L

        if (startTime <= 0L) return

        val nowElapsed = SystemClock.elapsedRealtime()
        val deltaMs = (nowElapsed - startTime).coerceIn(0L, 30_000L)
        if (deltaMs < 250L) return

        val today = TimeUtils.todayKey()
        val wallNow = System.currentTimeMillis()

        scope.launch {
            try {
                websiteStatsDao.upsert(
                    WebsiteStatsEntity(
                        date = today,
                        packageName = pkg,
                        domain = domain,
                        totalTime = 0L,
                        lastVisited = wallNow
                    )
                )
                websiteStatsDao.addTime(today, pkg, domain, deltaMs, wallNow)
            } catch (_: Exception) {}
        }
    }
}
