package com.curbme.app.core.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar

/**
 * 🚨 CRITICAL & HIGH-PRECISION USAGE RECONSTRUCTION ENGINE 🚨
 * DO NOT MODIFY OR REFACTOR THIS RECONSTRUCTION ALGORITHM.
 * 
 * Reconstructs per-package foreground screen time for a calendar day (or any window)
 * from the raw UsageEvents stream, achieving Digital Wellbeing precision.
 * 
 * Key Pillars of Precision:
 * 1. Instance-ID Tracking: Uses UsageEvents.Event.getInstanceId() to prevent multi-activity stale event clobbering.
 * 2. Day-Boundary Clipping: Clips future timestamps to System.currentTimeMillis() for today.
 * 3. Lockscreen Exclusions: Treats SCREEN_NON_INTERACTIVE and SCREEN_INTERACTIVE correctly so screen-off or lockscreen glances do not count.
 */
object UsageReconstructor {

    data class PackageUsage(
        val packageName: String,
        val foregroundMs: Long,
        val isLauncher: Boolean
    )

    /** One anomaly found while cross-checking instance-level bookkeeping. */
    data class Anomaly(val description: String, val timestamp: Long)

    data class Result(
        val usages: List<PackageUsage>,
        val totalScreenTimeMs: Long,
        val anomalies: List<Anomaly>
    )

    /** package -> the instanceId currently considered "the open one" for it */
    private typealias CurrentInstanceMap = MutableMap<String, Int>

    fun todayMidnightMillis(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun calculate(
        context: Context,
        startTime: Long = todayMidnightMillis(),
        endTime: Long = System.currentTimeMillis()
    ): Result {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(startTime, endTime)

        // package -> when its current (possibly multi-activity) session opened
        val openSince = mutableMapOf<String, Long>()
        // package -> accumulated ms so far
        val usage = mutableMapOf<String, Long>()
        // packages we've seen at least one event for (used for the
        // "already open at startTime" bootstrap case)
        val seenPackage = mutableSetOf<String>()
        // package -> the instanceId currently considered "the open one" for
        // it. This is the crux of the fix: only a PAUSED/STOPPED matching
        // this instance is allowed to close the package's session.
        val currentInstance: CurrentInstanceMap = mutableMapOf()
        val anomalies = mutableListOf<Anomaly>()

        fun closeSession(pkg: String, at: Long) {
            val since = openSince.remove(pkg) ?: return
            if (at > since) usage[pkg] = (usage[pkg] ?: 0L) + (at - since)
            currentInstance.remove(pkg)
        }

        fun closeAllSessions(at: Long) {
            openSince.keys.toList().forEach { closeSession(it, at) }
        }

        // Project minSdk is 31, so instanceId is always available
        val e = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val t = e.timeStamp
            val instanceId = getInstanceId(e)

            when (e.eventType) {

                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    seenPackage.add(pkg)
                    if (!openSince.containsKey(pkg)) {
                        openSince[pkg] = t
                    }
                    // This instance becomes "the current one" for the
                    // package - any later PAUSED/STOPPED for a DIFFERENT,
                    // superseded instance will be recognized as stale.
                    currentInstance[pkg] = instanceId
                }

                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (!seenPackage.contains(pkg)) {
                        // First event we've seen for this package is a
                        // close: it was already foregrounded before
                        // startTime. Treat this event's instance as current
                        // so the close below actually fires.
                        seenPackage.add(pkg)
                        openSince[pkg] = startTime
                        currentInstance[pkg] = instanceId
                    }
                    if (currentInstance[pkg] == instanceId) {
                        closeSession(pkg, t)
                    } else {
                        // Stale/out-of-order event for an instance that's
                        // already been superseded by a later RESUMED of a
                        // different instance in the same package - ignore.
                        anomalies += Anomaly(
                            "stale ${eventName(e.eventType)} for $pkg instance $instanceId " +
                                "(current instance is ${currentInstance[pkg]}) - ignored", t
                        )
                    }
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    closeAllSessions(t)
                }

                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    // No-op by design: only a real ACTIVITY_RESUMED reopens
                    // a session, so we don't attribute lock-screen glances
                    // to whatever was last in the foreground.
                }

                else -> { /* CONFIGURATION_CHANGE, USER_INTERACTION, etc. - ignored */ }
            }
        }

        // Anything still open at endTime (screen on, app currently in hand).
        closeAllSessions(endTime)

        val launcherPkg = defaultLauncherPackage(context)
        val usages = usage
            .map { (pkg, ms) -> PackageUsage(pkg, ms, isLauncher = pkg == launcherPkg) }
            .sortedByDescending { it.foregroundMs }

        val total = usages.filter { !it.isLauncher }.sumOf { it.foregroundMs }

        return Result(usages, total, anomalies)
    }

    /** Apps with no launcher icon (services/overlay managers) that DWB hides from its list. */
    fun hasLaunchIntent(context: Context, pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun getInstanceId(event: UsageEvents.Event): Int {
        return try {
            val method = event.javaClass.getMethod("getInstanceId")
            method.invoke(event) as Int
        } catch (_: Exception) {
            0
        }
    }

    private fun eventName(type: Int): String = when (type) {
        UsageEvents.Event.ACTIVITY_PAUSED -> "PAUSED"
        UsageEvents.Event.ACTIVITY_STOPPED -> "STOPPED"
        else -> "type=$type"
    }

    private fun defaultLauncherPackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }
}
