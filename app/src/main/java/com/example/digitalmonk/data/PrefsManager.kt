package com.example.digitalmonk.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
            context.getSharedPreferences("digital_monk_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ─── PIN ──────────────────────────────────────────────────────────────────

    fun savePin(pin: String) = prefs.edit().putString(KEY_PIN, pin).apply()

    fun getPin(): String = prefs.getString(KEY_PIN, "") ?: ""

    fun hasPin(): Boolean = prefs.getString(KEY_PIN, "").isNullOrEmpty().not()

    // ─── SHORTS BLOCKER ───────────────────────────────────────────────────────

    var blockShorts: Boolean
    get() = prefs.getBoolean(KEY_BLOCK_SHORTS, true)
    set(value) = prefs.edit().putBoolean(KEY_BLOCK_SHORTS, value).apply()

    // ─── BLOCKED APPS ─────────────────────────────────────────────────────────

    fun getBlockedPackages(): Set<String> =
            prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()

    fun saveBlockedPackages(packages: Set<String>) =
            prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, packages).apply()

    fun isAppBlocked(packageName: String): Boolean =
    getBlockedPackages().contains(packageName)

    // ─── SETUP STATE ──────────────────────────────────────────────────────────

    var isSetupComplete: Boolean
    get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
    set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    companion object {
        private const val KEY_PIN = "parent_pin"
        private const val KEY_BLOCK_SHORTS = "block_shorts"
        private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
    }
}