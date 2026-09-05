package com.curbme.app.service.accessibility.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.core.utils.TimeUtils
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.data.models.ReelPlanConfig
import com.curbme.app.data.models.ShortsBlockMode
import com.curbme.app.service.accessibility.detectors.ShortsDetector.shouldBlock
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class ShortsBlockHandler(
    private val actionPerformer: ActionPerformer
) {
    // State variable to prevent spamming the system logs
    private var lastBlockedPackage: String? = null

    /**
     * Functional interface for the callback.
     */
    fun interface ActionPerformer {
        fun performAction(action: Int): Boolean
    }

    /**
     * Evaluates the current screen state and fires a back press if short-form
     * video is detected.
     * Returns true if a block action was triggered.
     */
    fun handle(
        rootNode: AccessibilityNodeInfo?,
        packageName: String?,
        settings: Settings,
        context: Context? = null
    ): Boolean {
        val isBlockShorts = settings.isBlockShorts || (context != null && PrefsManager(context).isBlockShorts)
        if (packageName == null || !isBlockShorts) {
            return false
        }

        val plan = settings.reelPlanConfig

        // COMPLETE_BLOCK blocks all short-video apps unconditionally.
        // Other modes check whether the current package is in user's selected enabledTargetApps list.
        if (plan.mode != ShortsBlockMode.COMPLETE_BLOCK && plan.enabledTargetApps.isNotEmpty()) {
            if (!plan.enabledTargetApps.contains(packageName)) {
                return false
            }
        }

        val isShortsView = shouldBlock(rootNode, packageName)
        if (!isShortsView) {
            if (packageName == lastBlockedPackage) {
                lastBlockedPackage = null
            }
            return false
        }

        val shouldEnforceBlock = when (plan.mode) {
            ShortsBlockMode.COMPLETE_BLOCK,
            ShortsBlockMode.SELECTIVE_APPS -> true

            ShortsBlockMode.DAILY_TIME_LIMIT -> {
                context != null && isReelTimeLimitExceeded(context, plan.dailyTimeLimitMinutes)
            }

            ShortsBlockMode.REEL_COUNT_LIMIT -> {
                context != null && isReelCountLimitExceeded(context, plan.dailyReelCountLimit)
            }

            ShortsBlockMode.SCHEDULED_WINDOWS -> {
                isOutsideAllowedWindow(plan)
            }
        }

        if (shouldEnforceBlock) {
            if (packageName != lastBlockedPackage) {
                Log.d(TAG, "🚫 Plan Enforced: Blocking Shorts in $packageName (Mode=${plan.mode})")
                lastBlockedPackage = packageName
            }
            actionPerformer.performAction(GLOBAL_ACTION_BACK)
            return true
        }

        return false
    }

    private fun isReelTimeLimitExceeded(context: Context, limitMinutes: Int): Boolean {
        if (limitMinutes <= 0) return false
        return try {
            val db = AppDatabase.getDatabase(context)
            val today = TimeUtils.todayKey()
            val stats = runBlocking { db.reelUsageStatsDao().getForDate(today) }
            val totalMs = stats.sumOf { it.totalTime }
            totalMs >= limitMinutes * 60_000L
        } catch (_: Exception) {
            false
        }
    }

    private fun isReelCountLimitExceeded(context: Context, countLimit: Int): Boolean {
        if (countLimit <= 0) return false
        return try {
            val db = AppDatabase.getDatabase(context)
            val today = TimeUtils.todayKey()
            val count = runBlocking { db.reelStatsDao().getCount(today) } ?: 0
            count >= countLimit
        } catch (_: Exception) {
            false
        }
    }

    private fun isOutsideAllowedWindow(plan: ReelPlanConfig): Boolean {
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startMinutes = plan.startHour * 60 + plan.startMinute
        val endMinutes = plan.endHour * 60 + plan.endMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes !in startMinutes..endMinutes
        } else {
            currentMinutes in endMinutes until startMinutes
        }
    }

    companion object {
        private const val TAG = "ShortsBlockHandler"

        // We use BACK (1) instead of HOME (2) so we don't close the entire app
        private val GLOBAL_ACTION_BACK = AccessibilityService.GLOBAL_ACTION_BACK
    }
}