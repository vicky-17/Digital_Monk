package com.curbme.app.service.monitor

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active temporary unblock passes / cooldowns for apps or app groups.
 */
object CooldownManager {

    private val cooldowns = ConcurrentHashMap<String, Long>()

    /**
     * Checks if a temporary pass is currently active for targetKey (package or group ID).
     */
    fun isCoolingDown(targetKey: String): Boolean {
        val endTime = cooldowns[targetKey] ?: return false
        val now = SystemClock.uptimeMillis()
        if (now < endTime) {
            return true
        } else {
            cooldowns.remove(targetKey)
            return false
        }
    }

    /**
     * Grants a temporary unblock pass for durationMs milliseconds.
     */
    fun grantCooldown(targetKey: String, durationMs: Long) {
        if (durationMs <= 0) return
        val now = SystemClock.uptimeMillis()
        val endTime = if (durationMs > Long.MAX_VALUE - now) Long.MAX_VALUE else now + durationMs
        cooldowns[targetKey] = endTime
    }

    /**
     * Removes an active unblock pass immediately.
     */
    fun removeCooldown(targetKey: String) {
        cooldowns.remove(targetKey)
    }

    /**
     * Returns remaining cooldown milliseconds for targetKey, or 0 if expired/not set.
     */
    fun getRemainingMillis(targetKey: String): Long {
        val endTime = cooldowns[targetKey] ?: return 0L
        val now = SystemClock.uptimeMillis()
        return (endTime - now).coerceAtLeast(0L)
    }
}
