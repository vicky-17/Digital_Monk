package com.curbme.app.service.monitor

import android.content.Context
import android.util.Log
import com.curbme.app.core.utils.PermissionHelper
import com.curbme.app.core.utils.ShizukuManager

/**
 * Automates background protection and self-healing of GuardianAccessibilityService
 * using Shizuku permissions when accessibility service dies or is disabled.
 */
object AccessibilitySelfHealer {
    private const val TAG = "AccessibilitySelfHealer"

    /** Returns true if Shizuku self-healing is possible. */
    fun canSelfHeal(): Boolean = ShizukuManager.hasShizukuPermission()

    /**
     * Checks service state and attempts silent self-healing via Shizuku if disabled.
     * Also reinforces background execution whitelisting.
     */
    fun healIfNeeded(context: Context): Boolean {
        val isServiceRunning = PermissionHelper.isAccessibilityEnabled(context)
        if (!canSelfHeal()) {
            Log.d(TAG, "Cannot self-heal: Shizuku permission not granted")
            return isServiceRunning
        }

        try {
            // Always reinforce background battery idle whitelist
            ShizukuManager.reinforceBackgroundExecution(context)

            if (!isServiceRunning) {
                Log.w(TAG, "GuardianAccessibilityService is disabled. Triggering Shizuku self-heal...")
                val healed = ShizukuManager.healAccessibilityService(context)
                Log.i(TAG, "Shizuku self-healing result: $healed")
                return healed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing accessibility self-healing", e)
        }

        return isServiceRunning
    }
}
