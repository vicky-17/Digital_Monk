package com.curbme.app.core.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Resolves OEM autostart / battery saver whitelist screens that aggressive Android skins
 * (MIUI, ColorOS, Funtouch, EMUI, OneUI, OxygenOS, etc.) hide in device settings.
 */
object OemAutostartHelper {

    private val OEM_AUTOSTART_COMPONENTS = listOf(
        // Xiaomi / MIUI / Redmi / Poco
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        // Oppo / ColorOS / Realme
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        // Vivo / Funtouch / iQOO
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        // Huawei / Honor / EMUI
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        // Samsung / OneUI
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        // OnePlus / OxygenOS
        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        // Letv, Asus
        ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")
    )

    /**
     * Checks if the device manufacturer matches aggressive OEM background killers.
     */
    fun isAggressiveOemDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return listOf("xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo", "huawei", "honor", "oneplus", "samsung", "letv", "asus")
            .any { manufacturer.contains(it) || brand.contains(it) }
    }

    /**
     * Returns an explicit Intent to open the device's OEM autostart management screen, or null if none resolved.
     */
    fun getAutostartIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (component in OEM_AUTOSTART_COMPONENTS) {
            val intent = Intent().setComponent(component)
            if (pm.resolveActivity(intent, 0) != null) {
                return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return null
    }

    /**
     * Launches the OEM autostart screen if available. Returns true if an activity was launched successfully.
     */
    fun launchAutostartSettings(context: Context): Boolean {
        val intent = getAutostartIntent(context) ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
