package com.example.digitalmonk.service.monitor;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.example.digitalmonk.core.utils.PermissionHelper;
import com.example.digitalmonk.core.utils.PersistenceManager;
import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.vpn.DnsVpnService;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * ProtectionStateMonitor
 * ─────────────────────────────────────────────────────────────────────────────
 * Single-responsibility class that checks every protection dimension and
 * returns the current set of active issues.
 *
 * Design rules:
 *   • Pure detection — no side effects, no UI, no service starts.
 *   • All checks are cheap (no I/O, no network calls).
 *   • Thread-safe: can be called from any background thread.
 *   • Caller (WatchdogService) decides what to do with the results.
 *
 * Usage:
 *   ProtectionStateMonitor monitor = new ProtectionStateMonitor(context);
 *   Set<ProtectionIssue> issues = monitor.check();
 *   if (!issues.isEmpty()) { ... react ... }
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class ProtectionStateMonitor {

    private static final String TAG = "ProtectionStateMonitor";

    private final Context      context;
    private final PrefsManager prefs;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ProtectionStateMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.prefs   = new PrefsManager(this.context);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs all checks and returns a set of active protection issues.
     * Returns an empty set when everything is healthy.
     * Never returns null.
     */
    public Set<ProtectionIssue> check() {
        Set<ProtectionIssue> issues = EnumSet.noneOf(ProtectionIssue.class);

        checkPermissions(issues);
        checkVpnState(issues);

        if (!issues.isEmpty()) {
            Log.w(TAG, "Protection issues detected: " + issues);
        }

        return Collections.unmodifiableSet(issues);
    }

    /**
     * Convenience method: returns true if ANY issue is currently active.
     */
    public boolean hasAnyIssue() {
        return !check().isEmpty();
    }

    /**
     * Convenience method: returns true if a specific issue is active.
     */
    public boolean hasIssue(ProtectionIssue issue) {
        return check().contains(issue);
    }

    // ── Permission checks ─────────────────────────────────────────────────────

    private void checkPermissions(Set<ProtectionIssue> issues) {

        // 1. Accessibility service
        if (!PermissionHelper.isAccessibilityEnabled(context)) {
            issues.add(ProtectionIssue.ACCESSIBILITY_DISABLED);
        }

        // 2. Display over other apps
        if (!PermissionHelper.canDrawOverlays(context)) {
            issues.add(ProtectionIssue.OVERLAY_PERMISSION_MISSING);
        }

        // 3. Usage stats
        if (!PermissionHelper.hasUsageStatsPermission(context)) {
            issues.add(ProtectionIssue.USAGE_STATS_MISSING);
        }

        // 4. Battery optimization
        //    We use PersistenceManager here (same as WatchdogService and PermissionHelper)
        // Battery optimization
        if (!PersistenceManager.isBatteryOptimizationDisabled(context)) {

            // MIUI bug:
            // When VPN is active some Xiaomi devices falsely report
            // battery optimization as enabled.
            if (!DnsVpnService.isServiceRunning) {
                issues.add(ProtectionIssue.BATTERY_OPTIMIZATION_ACTIVE);
            }
        }
    }

    // ── VPN / content filter checks ───────────────────────────────────────────

    private void checkVpnState(Set<ProtectionIssue> issues) {
        // VPN checks only matter when the parent has enabled the filter
        if (!prefs.isSafeSearchEnabled()) {
            return;
        }

        // ── Check 1: VPN permission revoked ───────────────────────────────────
        // VpnService.prepare() returns null when permission is already granted.
        // A non-null result means we need to re-ask.
        try {
            Intent vpnPrepareIntent = VpnService.prepare(context);
            if (vpnPrepareIntent != null) {
                // Permission was revoked or never granted in this boot cycle
                issues.add(ProtectionIssue.VPN_PERMISSION_REVOKED);
                // If permission is gone, the service can't run — no point checking further
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "VpnService.prepare() threw: " + e.getMessage());
        }

        // ── Check 2: Our VPN service is dead ──────────────────────────────────
        if (!DnsVpnService.isServiceRunning) {
            issues.add(ProtectionIssue.VPN_SERVICE_DEAD);
        }

        // ── Check 3: Always-On VPN not set to us ─────────────────────────────
        // We check the system setting directly (same approach as PermissionHelper)
        if (!isAlwaysOnVpnSetToUs()) {
            issues.add(ProtectionIssue.ALWAYS_ON_VPN_NOT_SET);
        }

        // ── Check 4: Another VPN is active ────────────────────────────────────
        // If our service IS running but another VPN is also active via
        // NetworkCapabilities, it means someone found a way to layer a second VPN
        // on top (some rooted approaches or specific OEM behaviors).
        // If our service is NOT running, check if a foreign VPN took our slot.
        if (isAnotherVpnActive()) {
            issues.add(ProtectionIssue.ANOTHER_VPN_ACTIVE);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true if the system's Always-On VPN is set to our package.
     *
     * Reads the undocumented but stable Settings.Secure key "always_on_vpn_app"
     * which is the same approach used in PermissionHelper.isAlwaysOnVpnActive().
     */
    private boolean isAlwaysOnVpnSetToUs() {
        try {
            String alwaysOnApp = Settings.Secure.getString(
                    context.getContentResolver(),
                    "always_on_vpn_app"
            );
            return context.getPackageName().equals(alwaysOnApp);
        } catch (Exception e) {
            // If we can't read this setting, assume it's not set (conservative)
            Log.w(TAG, "Could not read always_on_vpn_app setting: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns true if any VPN is active on the device that is NOT ours.
     *
     * Strategy: use ConnectivityManager.NetworkCapabilities to detect an active
     * VPN transport. If a VPN transport is present AND our service is not running,
     * a foreign VPN is active. If our service IS running alongside another VPN
     * transport, it's likely the same situation.
     *
     * This is API 21+ but our minSdk is 26 so no guard needed.
     */
    private boolean isAnotherVpnActive() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return false;

            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
            if (caps == null) return false;

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                // A VPN transport is active. Is it ours?
                if (!DnsVpnService.isServiceRunning) {
                    // Our service is off but a VPN is active → foreign VPN
                    return true;
                }
                // Our service is running → this VPN IS ours, expected
                return false;
            }

        } catch (Exception e) {
            Log.w(TAG, "VPN transport check failed: " + e.getMessage());
        }
        return false;
    }

    private boolean isAnyVpnActive() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm == null) return false;

            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return false;

            NetworkCapabilities caps =
                    cm.getNetworkCapabilities(activeNetwork);

            return caps != null &&
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);

        } catch (Exception e) {
            Log.w(TAG, "isAnyVpnActive failed: " + e.getMessage());
            return false;
        }
    }
}