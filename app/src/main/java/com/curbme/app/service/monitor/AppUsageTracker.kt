package com.curbme.app.service.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.dao.AppUsageDao
import com.curbme.app.data.local.db.entity.AppUsageEntity
import com.curbme.app.core.utils.TimeUtils
import java.time.Instant
import java.time.ZoneId

/**
 * Highly accurate App Usage Tracker that leverages Accessibility Events
 * to monitor app switches in real-time.
 */
class AppUsageTracker {

    companion object {
        private const val HEARTBEAT_MS = 20_000L

        @Volatile
        var instance: AppUsageTracker? = null
            private set
    }

    private lateinit var context: Context
    private lateinit var dao: AppUsageDao

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val mainHandler = Handler(Looper.getMainLooper())

    private var ownPackage = ""
    private var currentPackage: String? = null
    private var currentAppName: String? = null
    private var sessionStartElapsed = 0L
    private var sessionStartWall = 0L
    private var lastCommitElapsed = 0L
    private var screenOn = true
    @Volatile private var trackingEnabled = true

    // Real-time notification stats
    private var totalUsageTodayMs = 0L
    private var totalLaunchesToday = 0
    private var currentAppTotalMs = 0L
    private var currentAppLaunchCount = 0

    fun setup(context: Context) {
        instance = this
        this.context = context.applicationContext
        this.ownPackage = context.packageName
        this.dao = AppDatabase.getDatabase(context).appUsageDao()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        screenOn = powerManager.isInteractive

        registerScreenReceiver()

        // Always enabled for now in Digital Monk
        trackingEnabled = true

        loadInitialTotals()
        
        // Hub & Spoke: Subscribe to central state changes
        scope.launch {
            MonitorState.foregroundApp.collect { info ->
                if (info.packageName != null) {
                    switchTo(info.packageName)
                } else {
                    stopTracking()
                }
                updateNotification()
            }
        }
        
        // Real-time ticking: Update notification every second for the clock
        scope.launch {
            while (true) {
                delay(1000)
                if (screenOn && currentPackage != null) {
                    updateNotification()
                }
            }
        }
    }

    private fun updateNotification() {
        val stats = getStatsSnapshot()
        // We need to get settings here, which is tricky since they are in DataStore
        // For now, we'll assume no bypass or get it from a shared place if needed.
        // Better yet, let's keep the notification build simple for now.
        
        val notification = com.curbme.app.service.notification.NotificationHelper.buildUsageNotification(
            context,
            stats.currentAppName,
            stats.currentAppUsageMs,
            stats.totalUsageTodayMs,
            stats.totalLaunchesToday,
            0L, // TODO: Wire up bypass
            stats.sessionDurationMs
        )
        
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(com.curbme.app.core.utils.Constants.NOTIFICATION_ID_GUARDIAN, notification)
    }

    private fun loadInitialTotals() {
        scope.launch {
            val today = TimeUtils.todayKey()
            val allStats = dao.getForDate(today)
            totalUsageTodayMs = allStats.sumOf { it.totalTime }
            totalLaunchesToday = allStats.sumOf { it.launchCount }
        }
    }

    fun switchTo(packageName: String) {
        if (!trackingEnabled || packageName == currentPackage) return
        endCurrentSession()

        val nowElapsed = SystemClock.elapsedRealtime()
        currentPackage = packageName
        
        // Don't set currentAppName = packageName to avoid flickering.
        // We look it up synchronously for the initial switch to be instant.
        currentAppName = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) { null }

        sessionStartElapsed = nowElapsed
        sessionStartWall = System.currentTimeMillis()
        lastCommitElapsed = nowElapsed

        scope.launch {
            val today = TimeUtils.todayKey()
            val entity = dao.get(today, packageName)
            // Use the maximum of what we have in DB vs what the system reports
            val systemStats = com.curbme.app.core.utils.UsageStatsHelper(context).getTodayUsageStats()
            val systemAppTotal = systemStats.find { it.packageName == packageName }?.usageTimeMs ?: 0L

            currentAppTotalMs = maxOf(entity?.totalTime ?: 0L, systemAppTotal)
            totalUsageTodayMs = maxOf(totalUsageTodayMs, systemStats.sumOf { it.usageTimeMs })
            currentAppLaunchCount = (entity?.launchCount ?: 0) + 1

            recordLaunch(packageName, sessionStartWall)
        }
        startHeartbeat()
    }

    fun stopTracking() {
        endCurrentSession()
    }

    private fun endCurrentSession() {
        if (currentPackage == null) return
        commit(SystemClock.elapsedRealtime())
        currentPackage = null
        currentAppName = null
        currentAppTotalMs = 0
        currentAppLaunchCount = 0
        stopHeartbeat()
    }

    private fun commit(nowElapsed: Long) {
        val packageName = currentPackage ?: return
        if (nowElapsed <= lastCommitElapsed) return

        val duration = nowElapsed - lastCommitElapsed
        totalUsageTodayMs += duration

        val startWall = sessionStartWall + (lastCommitElapsed - sessionStartElapsed)
        val endWall = sessionStartWall + (nowElapsed - sessionStartElapsed)
        lastCommitElapsed = nowElapsed

        val segments = splitIntoHourlySegments(startWall, endWall)
        scope.launch {
            if (!trackingEnabled) return@launch
            segments.forEach { addUsage(it.date, packageName, it.hour, it.durationMs, it.endWall) }
        }
    }

    private fun recordLaunch(packageName: String, wall: Long) {
        val date = TimeUtils.todayKey()
        totalLaunchesToday++
        scope.launch {
            if (!trackingEnabled) return@launch
            val existing = dao.get(date, packageName)
            dao.upsert(
                existing?.copy(
                    launchCount = existing.launchCount + 1,
                    lastUsed = maxOf(existing.lastUsed, wall)
                ) ?: AppUsageEntity(
                    date = date,
                    packageName = packageName,
                    launchCount = 1,
                    lastUsed = wall
                )
            )
        }
    }

    private suspend fun addUsage(date: String, packageName: String, hour: Int, durationMs: Long, wall: Long) {
        if (durationMs <= 0 || !trackingEnabled) return
        val existing = dao.get(date, packageName)
        val hourly = parseHourly(existing?.hourlyUsage)
        hourly[hour] += durationMs
        dao.upsert(
            AppUsageEntity(
                date = date,
                packageName = packageName,
                totalTime = (existing?.totalTime ?: 0L) + durationMs,
                hourlyUsage = serializeHourly(hourly),
                launchCount = existing?.launchCount ?: 0,
                lastUsed = maxOf(existing?.lastUsed ?: 0L, wall)
            )
        )
    }

    private data class Segment(val date: String, val hour: Int, val durationMs: Long, val endWall: Long)

    private fun splitIntoHourlySegments(startWall: Long, endWall: Long): List<Segment> {
        if (endWall <= startWall) return emptyList()
        val zone = ZoneId.systemDefault()
        val segments = ArrayList<Segment>()
        var cursor = startWall
        while (cursor < endWall) {
            val zdt = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = zdt.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                .toInstant().toEpochMilli()
            val segmentEnd = minOf(endWall, nextHour)
            segments.add(
                Segment(
                    date = TimeUtils.dayKey(zdt.toLocalDate()),
                    hour = zdt.hour,
                    durationMs = segmentEnd - cursor,
                    endWall = segmentEnd
                )
            )
            cursor = segmentEnd
        }
        return segments
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            if (!trackingEnabled) return
            commit(SystemClock.elapsedRealtime())
            mainHandler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private fun startHeartbeat() {
        mainHandler.removeCallbacks(heartbeat)
        mainHandler.postDelayed(heartbeat, HEARTBEAT_MS)
    }

    private fun stopHeartbeat() {
        mainHandler.removeCallbacks(heartbeat)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    endCurrentSession()
                }
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_USER_PRESENT -> {
                    screenOn = true
                    // Resume tracking if screen is back on
                    resumeForegroundApp()
                }
            }
        }
    }

    private fun resumeForegroundApp() {
        if (!trackingEnabled || !screenOn || currentPackage != null) return
        // We can't easily get the rootInActiveWindow from here without service instance
        // but the next window state change event will trigger switchTo()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(screenReceiver, filter)
    }

    fun onDestroy() {
        instance = null
        stopHeartbeat()
        val packageName = currentPackage.takeIf { trackingEnabled }
        if (packageName != null) {
            val startWall = sessionStartWall + (lastCommitElapsed - sessionStartElapsed)
            val endWall = sessionStartWall + (SystemClock.elapsedRealtime() - sessionStartElapsed)
            val segments = splitIntoHourlySegments(startWall, endWall)
            try {
                runBlocking {
                    segments.forEach { addUsage(it.date, packageName, it.hour, it.durationMs, it.endWall) }
                }
            } catch (_: Exception) {
            }
        }
        currentPackage = null
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
    }

    private fun parseHourly(serialized: String?): LongArray {
        val result = LongArray(24)
        if (serialized.isNullOrEmpty()) return result
        val parts = serialized.split(',')
        for (i in 0 until minOf(24, parts.size)) {
            result[i] = parts[i].toLongOrNull() ?: 0L
        }
        return result
    }

    private fun serializeHourly(hourly: LongArray): String = hourly.joinToString(",")

    data class UsageStatsSnapshot(
        val currentPackage: String?,
        val currentAppName: String?,
        val currentAppUsageMs: Long,
        val totalUsageTodayMs: Long,
        val totalLaunchesToday: Int,
        val sessionDurationMs: Long
    )

    fun getStatsSnapshot(): UsageStatsSnapshot {
        val nowElapsed = SystemClock.elapsedRealtime()
        val sessionDuration = if (currentPackage != null) nowElapsed - sessionStartElapsed else 0L
        val lastCommitDuration = if (currentPackage != null) nowElapsed - lastCommitElapsed else 0L
        
        return UsageStatsSnapshot(
            currentPackage = currentPackage,
            currentAppName = currentAppName,
            currentAppUsageMs = currentAppTotalMs + lastCommitDuration,
            totalUsageTodayMs = totalUsageTodayMs + lastCommitDuration,
            totalLaunchesToday = totalLaunchesToday,
            sessionDurationMs = sessionDuration
        )
    }
}