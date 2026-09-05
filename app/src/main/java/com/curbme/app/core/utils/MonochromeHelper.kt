package com.curbme.app.core.utils

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Automates system monochrome / grayscale display filter via Shizuku or WRITE_SECURE_SETTINGS.
 */
object MonochromeHelper {
    private const val TAG = "MonochromeHelper"

    /** Enables system monochrome/grayscale filter. */
    fun enableMonochrome(context: Context) {
        setGrayscaleState(context, enabled = true)
    }

    /** Disables system monochrome/grayscale filter. */
    fun disableMonochrome(context: Context) {
        setGrayscaleState(context, enabled = false)
    }

    private fun setGrayscaleState(context: Context, enabled: Boolean) {
        val enabledVal = if (enabled) "1" else "0"
        val cmd = "settings put secure accessibility_display_daltonizer_enabled $enabledVal; " +
                  "settings put secure accessibility_display_daltonizer 0"

        if (ShizukuManager.hasShizukuPermission()) {
            ShizukuRunner.executeCommand(cmd)
        } else {
            try {
                val cr = context.contentResolver
                Settings.Secure.putInt(cr, "accessibility_display_daltonizer_enabled", if (enabled) 1 else 0)
                Settings.Secure.putInt(cr, "accessibility_display_daltonizer", 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle grayscale via secure settings", e)
            }
        }
    }
}
