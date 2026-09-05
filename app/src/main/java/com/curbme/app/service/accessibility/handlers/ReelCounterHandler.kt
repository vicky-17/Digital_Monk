package com.curbme.app.service.accessibility.handlers

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.core.utils.TimeUtils
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.ReelStatsEntity
import com.curbme.app.service.accessibility.detectors.ReelProgressionAnalyzer
import com.curbme.app.service.accessibility.detectors.ReelTextExtractor
import com.curbme.app.ui.overlay.ReelCounterOverlayManager
import kotlinx.coroutines.*

/**
 * Handles short video / reel scroll detection, database persistence,
 * and live on-screen overlay updating.
 */
class ReelCounterHandler(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val reelStatsDao = db.reelStatsDao()
    private val reelUsageStatsDao = db.reelUsageStatsDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val analyzer = ReelProgressionAnalyzer()
    private val overlayManager = ReelCounterOverlayManager(context)

    private var todayCount = 0
    private var lastDateStr = TimeUtils.todayKey()
    private var activePackage: String? = null
    private var lastTickElapsedMs = 0L

    init {
        scope.launch {
            loadTodayCount()
        }
    }

    fun handleEvent(rootNode: AccessibilityNodeInfo?, packageName: String?) {
        if (packageName == null) {
            overlayManager.hide()
            return
        }

        val comparator = ReelTextExtractor.extractComparator(rootNode, packageName)
        if (comparator == null) {
            if (activePackage == packageName) {
                activePackage = null
                flushActiveTime(packageName)
            }
            overlayManager.hide()
            return
        }

        // Active reel screen
        if (activePackage != packageName) {
            if (activePackage != null) {
                flushActiveTime(activePackage!!)
            }
            activePackage = packageName
            lastTickElapsedMs = SystemClock.elapsedRealtime()
        } else {
            flushActiveTime(packageName)
        }

        val isNewReel = analyzer.checkReelProgression(packageName, comparator)
        if (isNewReel) {
            onReelCounted(packageName)
        } else {
            overlayManager.showCount(todayCount)
        }
    }

    fun onDestroy() {
        activePackage?.let { flushActiveTime(it) }
        overlayManager.destroy()
        scope.cancel()
    }

    private fun onReelCounted(packageName: String) {
        val today = TimeUtils.todayKey()
        if (today != lastDateStr) {
            todayCount = 0
            lastDateStr = today
        }
        todayCount++

        overlayManager.showCount(todayCount)

        val wallNow = System.currentTimeMillis()
        scope.launch {
            try {
                reelStatsDao.upsert(ReelStatsEntity(date = today, count = todayCount, lastUpdated = wallNow))
                reelUsageStatsDao.incrementCount(today, packageName, wallNow)
            } catch (_: Exception) {}
        }
    }

    private fun flushActiveTime(packageName: String) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val deltaMs = (nowElapsed - lastTickElapsedMs).coerceIn(0L, 10_000L)
        lastTickElapsedMs = nowElapsed

        if (deltaMs < 250L) return

        val today = TimeUtils.todayKey()
        val wallNow = System.currentTimeMillis()
        scope.launch {
            try {
                reelUsageStatsDao.addTime(today, packageName, deltaMs, wallNow)
            } catch (_: Exception) {}
        }
    }

    private suspend fun loadTodayCount() {
        try {
            lastDateStr = TimeUtils.todayKey()
            todayCount = reelStatsDao.getCount(lastDateStr) ?: 0
        } catch (_: Exception) {
            todayCount = 0
        }
    }
}
