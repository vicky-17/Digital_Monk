package com.digitalmonk.app.service.monitor

import android.app.usage.UsageEvents
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.digitalmonk.app.data.local.db.AppDatabase
import com.digitalmonk.app.data.local.db.dao.AppUsageDao
import com.digitalmonk.app.data.local.db.entity.AppUsageEntity
import com.digitalmonk.app.core.utils.TimeUtils
import com.digitalmonk.app.service.accessibility.GuardianAccessibilityService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Highly accurate App Usage Tracker that leverages Accessibility Events
 * to monitor app switches in real-time.
 */
class AppUsageTracker {

    companion object {
        private const val HEARTBEAT_MS = 20_000L

        // Truly ignore: never represents a real foreground app, and never
        // implies the user left whatever app was actually open (empty
        // package / OS-internal placeholder events).
        private val IGNORED_PACKAGES = setOf("android")

        // Transient system surfaces: notification shade, Quick Settings,
        // Recents/Overview, MIUI control-center variants. These DO mean the
        // user has left the previously-tracked app, so we must end that
        // session — but they are not "apps" in their own right, so we don't
        // start a new session for them either.
        //
        // Root cause this fixes: previously these packages were lumped into
        // IGNORED_PACKAGES and the event was dropped entirely, which left
        // the previous app's session open. The 20s heartbeat then kept
        // crediting elapsed time to that app for as long as the user sat in
        // Recents/the shade — this is what produced the 2x-14x inflation
        // seen vs raw dumpsys totals (worst on apps people frequently swipe
        // away from: Instagram, Telegram, Curbox).
        private val TRANSIENT_SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.miui.systemui.plugin"
        )
    }

    private lateinit var service: GuardianAccessibilityService
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

    fun setup(service: GuardianAccessibilityService) {
        this.service = service
        this.ownPackage = service.packageName
        this.dao = AppDatabase.getDatabase(service).appUsageDao()

        val powerManager = service.getSystemService(Context.POWER_SERVICE) as PowerManager
        screenOn = powerManager.isInteractive

        registerScreenReceiver()

        // Always enabled for now in Digital Monk
        trackingEnabled = true

        loadInitialTotals()
        startHeartbeat()
    }

    private fun loadInitialTotals() {
        scope.launch {
            val today = TimeUtils.todayKey()
            val allStats = dao.getForDate(today)
            totalUsageTodayMs = allStats.sumOf { it.totalTime }
            totalLaunchesToday = allStats.sumOf { it.launchCount }
            updateNotification()
        }
    }

    fun onEvent(event: AccessibilityEvent?) {
        if (!trackingEnabled) return
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!screenOn) return

        val foreground = event.packageName?.toString() ?: return

        if (foreground.isEmpty() || foreground in IGNORED_PACKAGES) return

        if (foreground in TRANSIENT_SYSTEM_PACKAGES) {
            // User pulled the shade / opened Recents / opened Quick
            // Settings. End the current session so this idle time isn't
            // silently attributed to whatever app was open before. We do
            // NOT start a new session for the transient surface itself —
            // the next real app's WINDOW_STATE_CHANGED event will do that.
            endCurrentSession()
            return
        }

        if (foreground == currentPackage) return

        switchTo(foreground)
    }

    private fun switchTo(packageName: String) {
        if (!trackingEnabled) return
        endCurrentSession()

        val nowElapsed = SystemClock.elapsedRealtime()
        currentPackage = packageName
        sessionStartElapsed = nowElapsed
        sessionStartWall = System.currentTimeMillis()
        lastCommitElapsed = nowElapsed

        scope.launch {
            val today = TimeUtils.todayKey()
            val entity = dao.get(today, packageName)
            // Use the maximum of what we have in DB vs what the system reports
            // but for real-time we start from the current known total.
            val systemStats = com.digitalmonk.app.core.utils.UsageStatsHelper(service).getTodayUsageStats()
            val systemAppTotal = systemStats.find { it.packageName == packageName }?.usageTimeMs ?: 0L

            currentAppTotalMs = maxOf(entity?.totalTime ?: 0L, systemAppTotal)
            totalUsageTodayMs = maxOf(totalUsageTodayMs, systemStats.sumOf { it.usageTimeMs })
            currentAppLaunchCount = (entity?.launchCount ?: 0) + 1
            currentAppName = try {
                val pm = service.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) { packageName }

            recordLaunch(packageName, sessionStartWall)
            updateNotification()
        }
        startHeartbeat()
    }

    private fun endCurrentSession() {
        if (currentPackage == null) return
        commit(SystemClock.elapsedRealtime())
        currentPackage = null
        currentAppName = null
        currentAppTotalMs = 0
        currentAppLaunchCount = 0
        stopHeartbeat()
        updateNotification()
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
        updateNotification()
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

    private fun updateNotification() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val sessionDuration = if (currentPackage != null) nowElapsed - sessionStartElapsed else 0L

        val displayTotal = totalUsageTodayMs + sessionDuration
        val displayApp = currentAppTotalMs + sessionDuration
        val displayLaunches = if (currentPackage != null) currentAppLaunchCount else totalLaunchesToday

        val notification = com.digitalmonk.app.service.notification.NotificationHelper.buildUsageNotification(
            service,
            currentAppName,
            displayApp,
            displayTotal,
            displayLaunches
        )

        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(com.digitalmonk.app.core.utils.Constants.NOTIFICATION_ID_GUARDIAN, notification)
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
        service.registerReceiver(screenReceiver, filter)
    }

    fun onDestroy() {
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
            service.unregisterReceiver(screenReceiver)
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
}