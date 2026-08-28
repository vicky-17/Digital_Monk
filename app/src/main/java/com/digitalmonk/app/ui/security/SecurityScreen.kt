package com.digitalmonk.app.ui.security

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.ui.components.cards.ToggleCard
import com.digitalmonk.app.ui.components.dialogs.ConfirmDialog
import com.digitalmonk.app.ui.components.dialogs.PinGateDialog
import com.digitalmonk.app.ui.components.dialogs.PreventVpnOverrideDialog
import com.digitalmonk.app.ui.components.dialogs.VpnKeepAliveDialog
import com.digitalmonk.app.ui.sidebar.formatRemainingTime
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import com.digitalmonk.app.ui.components.dialogs.LockSettingsDialog
import android.os.SystemClock
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.ui.draw.clip
import com.digitalmonk.app.ui.components.cards.ActionCard

import com.digitalmonk.app.ui.components.cards.DnsProtectionCard

import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.draw.scale
import androidx.core.graphics.createBitmap

// ── Color palette ─────────────────────────────────────────────────────────────
private val ScreenBg   = Color(0xFF080E1A)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond  = Color(0xFF64748B)
private val DividerCol  = Color(0xFF1E293B)

// ─────────────────────────────────────────────────────────────────────────────
//  SecurityScreen  —  public entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SecurityScreen(prefs: PrefsManager) {
    val context = LocalContext.current
    val viewModel = remember { SecurityViewModel(prefs, context) }

    val isPermissionBlockEnabled by viewModel.isPermissionBlockEnabled.collectAsState()
    val showConfirmDialog by viewModel.showConfirmDialog.collectAsState()

    // ── Private DNS States (Moved to top so they are available globally in this scope) ──
    val isDeviceOwner = viewModel.isDeviceOwner
    val isPrivateDnsEnabled by viewModel.isPrivateDnsEnabled.collectAsState()
    val selectedHostname by viewModel.selectedHostname.collectAsState()
    val hostnameList by viewModel.hostnameList.collectAsState()
    val showAddDialog by viewModel.showAddHostnameDialog.collectAsState()
    val newHostnameInput by viewModel.newHostnameInput.collectAsState()
    val showEnableHostnameDialog by viewModel.showEnableHostnameDialog.collectAsState()
    val isApplyingPrivateDns by viewModel.isApplyingPrivateDns.collectAsState()
    val privateDnsError by viewModel.privateDnsError.collectAsState()
    val isPrivateDnsLocked by viewModel.isPrivateDnsLocked.collectAsState()
    val showAppConfirmDialog by viewModel.showAppConfirmDialog.collectAsState()

    // ── Banking Mode States ──────────────────────────────────────────────────
    val isBankingBypassEnabled by viewModel.isBankingBypassEnabled.collectAsState()
    val bankingBypassPackage by viewModel.bankingBypassPackage.collectAsState()
    val showBankingAppPicker by viewModel.showBankingAppPicker.collectAsState()
    val bankingApps by viewModel.bankingApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()

    // ── State ─────────────────────────────────────────────────────────────────
    var keepVpnAlive            by remember { mutableStateOf(prefs.isKeepVpnAlive) }
    var preventVpnOverride      by remember { mutableStateOf(prefs.isPreventVpnOverride) }
    var antiUninstallEnabled    by remember { mutableStateOf(prefs.isAntiUninstallEnabled) }

    // ── Dialog visibility ─────────────────────────────────────────────────────
    var showKeepAliveInfoDialog     by remember { mutableStateOf(false) }
    var showPreventVpnDialog        by remember { mutableStateOf(false) }
    var showDisableVpnPinDialog     by remember { mutableStateOf(false) }
    var showAntiUninstallPinDialog  by remember { mutableStateOf(false) }

    var showDnsLockDialog by remember { mutableStateOf(false) }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
    ) {
        SecurityScreenHeader()

        Spacer(Modifier.height(8.dp))

        // ── VPN Protection section ────────────────────────────────────────────
        SectionLabel("VPN PROTECTION")

        ToggleCard(
            emoji    = "♻️",
            title    = "Keep VPN Alive",
            subtitle = "Some phones kill VPN unexpectedly. We'll attempt to keep it on for as long as possible.",
            isEnabled = keepVpnAlive,
            onToggle = { newValue ->
                val prefsCheck = PrefsManager(context)
                if (prefsCheck.isSettingsLocked) {
                    Toast.makeText(
                        context,
                        "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@ToggleCard
                }
                if (newValue) {
                    showKeepAliveInfoDialog = true
                } else {
                    keepVpnAlive = false
                    prefs.isKeepVpnAlive = false
                }
            }
        )

        HorizontalDivider(
            color     = DividerCol,
            thickness = 0.5.dp,
            modifier  = Modifier.padding(horizontal = 20.dp)
        )

        ToggleCard(
            emoji    = "🔒",
            title    = "Prevent VPN Override",
            subtitle = "Prevents another VPN app from overriding Digital Monk's filter.",
            isEnabled = preventVpnOverride,
            onToggle = { newValue ->
                val prefsCheck = PrefsManager(context)
                if (prefsCheck.isSettingsLocked) {
                    Toast.makeText(
                        context,
                        "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@ToggleCard
                }
                if (newValue) {
                    showPreventVpnDialog = true
                } else {
                    // Disabling is the dangerous direction — require PIN
                    showDisableVpnPinDialog = true
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        // ── Anti-Uninstall section ────────────────────────────────────────────
        SectionLabel("ANTI-UNINSTALL")

        ToggleCard(
            emoji    = "🛡️",
            title    = "Prevent Settings Tampering",
            subtitle = "Blocks access to Force Stop, Uninstall, and Device Admin pages for Digital Monk.",
            isEnabled = antiUninstallEnabled,
            onToggle = { newValue ->
                val prefsCheck = PrefsManager(context)
                if (prefsCheck.isSettingsLocked) {
                    Toast.makeText(
                        context,
                        "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@ToggleCard
                }
                if (newValue) {
                    // Turning ON is always safe — no PIN required
                    antiUninstallEnabled = true
                    prefs.isAntiUninstallEnabled = true
                } else {
                    // Turning OFF is dangerous — require PIN
                    showAntiUninstallPinDialog = true
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        ActionCard(
            title = "App Uninstall Protection",
            description = "Choose apps that cannot be uninstalled from this device.",
            icon = androidx.compose.material.icons.Icons.Rounded.Security,
            onClick = { viewModel.onOpenAppListRequested() }
        )

        SectionLabel("STRICT PERMISSION ENFORCEMENT")

        ToggleCard(
            emoji     = "⚠️",
            title     = "Strict Permission Blocking",
            subtitle  = "Block entire screen if required permissions are missing.",
            isEnabled = isPermissionBlockEnabled,
            onToggle  = { isChecked ->
                viewModel.onToggleClicked(isChecked)
            }
        )

        // ── Banking Mode Section (Conditional) ────────────────────────────────
        if (isPermissionBlockEnabled) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("BANKING COMPATIBILITY")

            ActionCard(
                title = if (isBankingBypassEnabled) "Finish Banking Mode" else "Banking Mode (Temp Bypass)",
                description = if (isBankingBypassEnabled) 
                    "Tap to finish and re-enable Accessibility permission." 
                    else "One-tap turn OFF accessibility to use one banking app.",
                icon = androidx.compose.material.icons.Icons.Rounded.Security,
                onClick = { viewModel.onBankingBypassToggleRequested(!isBankingBypassEnabled) }
            )
            
            if (isBankingBypassEnabled) {
                Text(
                    "Currently allowing: $bankingBypassPackage\nDigital Monk Accessibility is OFF. All non-banking apps are blocked.",
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444), // Red to indicate vulnerability/special state
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel("PRIVATE DNS PROTECTION")

        DnsProtectionCard(
            isEnabled = isPrivateDnsEnabled && isDeviceOwner,
            selectedHostname = selectedHostname,
            isSettingsLocked = isPrivateDnsLocked && isDeviceOwner,
            isTimedLockActive = prefs.isSettingsLocked,
            lockUntil = prefs.lockUntil,
            isDeviceOwner = isDeviceOwner,
            onDnsToggle = { newValue ->
                if (!isDeviceOwner) {
                    Toast.makeText(context, "Requires Device Owner.", Toast.LENGTH_LONG).show()
                } else if (!prefs.isSettingsLocked) {
                    viewModel.onPrivateDnsToggleRequested(newValue)
                }
            },
            onHostClick = {
                viewModel.onPrivateDnsToggleRequested(true) // Re-opens host picker
            },
            onSettingsLockToggle = { newValue ->
                if (!isDeviceOwner) {
                    Toast.makeText(context, "Requires Device Owner.", Toast.LENGTH_LONG).show()
                } else if (prefs.isSettingsLocked && !newValue) {
                    // Strict check: Cannot turn OFF shield while locked
                    Toast.makeText(context, "Cannot disable shield while locked.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.onPrivateDnsLockToggleRequested(newValue)
                }
            },
            onLockClick = {
                if (isPrivateDnsEnabled) {
                    showDnsLockDialog = true
                } else {
                    Toast.makeText(context, "Turn on DNS filtering first to lock it.", Toast.LENGTH_SHORT).show()
                }
            }
        )

        Spacer(Modifier.height(40.dp))


    }


    val showAppListDialog by viewModel.showAppListDialog.collectAsState()
    if (showAppListDialog) {
        AppUninstallProtectionDialog(viewModel, prefs)
    }

    if (showBankingAppPicker) {
        BankingAppPickerDialog(
            apps = bankingApps,
            isLoading = isLoadingApps,
            onAppSelected = { viewModel.confirmBankingBypass(it) },
            onDismiss = { viewModel.dismissBankingAppPicker() }
        )
    }

    if (showDnsLockDialog) {
        LockSettingsDialog(
            onConfirm = { durationMs ->
                val now = System.currentTimeMillis()
                prefs.lockDurationMs = durationMs
                prefs.lockAnchorElapsed = SystemClock.elapsedRealtime()
                prefs.lockUntil = now + durationMs
                prefs.lastKnownDeviceTime = now
                prefs.lockNtpOffset = Long.MIN_VALUE
                showDnsLockDialog = false

                // Optional: Fetch NTP time for tamper-proofing (same as Dashboard)
                Thread {
                    val ntpTime = com.digitalmonk.app.core.utils.NtpFetcher.fetchNtpTime()
                    if (ntpTime > 0) {
                        val offset = ntpTime - System.currentTimeMillis()
                        prefs.lockNtpOffset = offset
                        prefs.lockUntil = ntpTime + durationMs
                    }
                }.start()
            },
            onDismiss = { showDnsLockDialog = false }
        )
    }

    if (showEnableHostnameDialog) {
        var tempSelection by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissEnableHostnameDialog() },
            title = { Text("Choose a Private DNS host") },
            text = {
                Column {
                    Text(
                        "Select which secure DNS provider to use. This is asked every time " +
                                "you turn Private DNS on.",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    if (isApplyingPrivateDns) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Checking host…", fontSize = 13.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            hostnameList.forEach { hostname ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { tempSelection = hostname }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (tempSelection == hostname),
                                        onClick = { tempSelection = hostname }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(hostname, fontSize = 14.sp, modifier = Modifier.weight(1f))

                                    if (!viewModel.isDefaultHostname(hostname)) {
                                        IconButton(
                                            onClick = {
                                                if (tempSelection == hostname) tempSelection = null
                                                viewModel.deleteHostname(hostname)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Rounded.Delete,
                                                contentDescription = "Remove $hostname",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.onAddHostnameClicked() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Add Custom Hostname")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = tempSelection != null && !isApplyingPrivateDns,
                    onClick = { tempSelection?.let { viewModel.confirmEnablePrivateDns(it) } }
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isApplyingPrivateDns,
                    onClick = { viewModel.dismissEnableHostnameDialog() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddHostnameDialog() },
            title = { Text("Add Custom Hostname") },
            text = {
                Column {
                    Text("Enter a valid DNS hostname (e.g., security.cloudflare-dns.com):", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newHostnameInput,
                        onValueChange = { viewModel.updateNewHostnameInput(it) },
                        singleLine = true,
                        placeholder = { Text("hostname.com") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveNewHostname() }) {
                    Text("Add & Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAddHostnameDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showConfirmDialog) {
        ConfirmDialog(
            title = "Change Security Setting",
            message = "Are you sure you want to change the permission blocking behavior? Disabling this might prevent the app from enforcing rules effectively.",
            onConfirm = { viewModel.confirmToggle() },
            onDismiss = { viewModel.dismissDialog() }
        )
    }

    if (showKeepAliveInfoDialog) {
        VpnKeepAliveDialog(
            onConfirm = {
                showKeepAliveInfoDialog = false
                keepVpnAlive = true
                prefs.isKeepVpnAlive = true
            },
            onDismiss = { showKeepAliveInfoDialog = false }
        )
    }

    if (showPreventVpnDialog) {
        PreventVpnOverrideDialog(
            onConfirm = {
                showPreventVpnDialog = false
                preventVpnOverride = true
                prefs.isPreventVpnOverride = true
            },
            onDismiss = { showPreventVpnDialog = false }
        )
    }

    if (showDisableVpnPinDialog) {
        PinGateDialog(
            prefs   = prefs,
            title   = "Disable VPN Override Protection",
            message = "Enter your parent PIN to allow other VPN apps to override Digital Monk.",
            onSuccess = {
                showDisableVpnPinDialog = false
                preventVpnOverride = false
                prefs.isPreventVpnOverride = false
            },
            onDismiss = { showDisableVpnPinDialog = false }
        )
    }

    if (showAntiUninstallPinDialog) {
        PinGateDialog(
            prefs   = prefs,
            title   = "Disable Anti-Uninstall Protection",
            message = "Turning this off allows access to Force Stop, Uninstall, and Device Admin pages for Digital Monk. Enter your PIN to confirm.",
            onSuccess = {
                showAntiUninstallPinDialog = false
                antiUninstallEnabled = false
                prefs.isAntiUninstallEnabled = false
            },
            onDismiss = { showAntiUninstallPinDialog = false }
        )
    }
    showAppConfirmDialog?.let { data ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissAppConfirmDialog() },
            title = { Text("Enable Protection", color = Color.White) },
            text = { Text("Enable protection for ${data.appName}? This prevents tampering until the lock expires.", color = Color(0xFF94A3B8)) },
            containerColor = Color(0xFF0F172A),
            confirmButton = {
                TextButton(onClick = { viewModel.confirmAppToggle() }) { Text("Confirm", color = Color(0xFF3B82F6)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAppConfirmDialog() }) { Text("Cancel", color = Color.White) }
            }
        )
    }
}



@Composable
private fun BankingAppPickerDialog(
    apps: List<SecurityViewModel.AppInfo>,
    isLoading: Boolean,
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text("Select Banking App", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Only one app can be allowed for temporary bypass. Social media apps are hidden for security.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color(0xFF3B82F6)
                        )
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(apps.size) { index ->
                                val app = apps[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAppSelected(app.packageName) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = rememberAppIconPainter(app.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(app.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(app.packageName, fontSize = 11.sp, color = Color(0xFF64748B))
                                    }
                                }
                                if (index < apps.size - 1) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
        }
    )
}

@Composable
private fun AppUninstallProtectionDialog(viewModel: SecurityViewModel, prefs: PrefsManager) {
    val apps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    var showLockDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { viewModel.dismissAppListDialog() },
        containerColor = Color(0xFF0F172A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Uninstall Protection", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showLockDialog = true }) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Lock,
                        contentDescription = "Lock Settings",
                        tint = if (prefs.isSettingsLocked) Color(0xFF3B82F6) else Color.White
                    )
                }
            }
        },
        text = {
            Column {
                if (prefs.isSettingsLocked) {
                    Text(
                        "Locked for ${formatRemainingTime(prefs.lockUntil - System.currentTimeMillis())}",
                        color = Color(0xFF3B82F6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    "Selected apps cannot be uninstalled from this device settings.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Box(modifier = Modifier.heightIn(max = 450.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color(0xFF3B82F6)
                        )
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(apps.size) { index ->
                                val app = apps[index]
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // --- Real App Icon ---
                                        androidx.compose.foundation.Image(
                                            painter = rememberAppIconPainter(app.icon),
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                        )

                                        Spacer(Modifier.width(14.dp))

                                        Column(Modifier.weight(1f)) {
                                            Text(app.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(app.packageName, fontSize = 11.sp, color = Color(0xFF64748B))
                                        }
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    // --- Dual Toggles ---
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        // Uninstall Protection
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Anti-Uninstall", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                            Switch(
                                                checked = app.isUninstallBlocked,
                                                onCheckedChange = { viewModel.toggleUninstallProtection(app.packageName, it) },
                                                modifier = Modifier.scale(0.8f) // Correct way to scale
                                            )
                                        }

                                        // Force Stop Protection
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Block Force Stop", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                            Switch(
                                                checked = app.isForceStopBlocked,
                                                onCheckedChange = { viewModel.toggleForceStopProtection(app.packageName, it) },
                                                modifier = Modifier.scale(0.8f)
                                            )
                                        }
                                    }
                                }
                                if (index < apps.size - 1) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.dismissAppListDialog() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text("Done", color = Color.White)
            }
        }
    )
    if (showLockDialog) {
        LockSettingsDialog(
            onConfirm = { durationMs ->
                // Copy the logic from SecurityScreen.kt (line 300) here:
                val now = System.currentTimeMillis()
                prefs.lockDurationMs = durationMs
                prefs.lockAnchorElapsed = SystemClock.elapsedRealtime()
                prefs.lockUntil = now + durationMs
                prefs.lastKnownDeviceTime = now
                prefs.lockNtpOffset = Long.MIN_VALUE
                showLockDialog = false

                // Optional: Fetch NTP time for tamper-proofing
                Thread {
                    val ntpTime = com.digitalmonk.app.core.utils.NtpFetcher.fetchNtpTime()
                    if (ntpTime > 0) {
                        val offset = ntpTime - System.currentTimeMillis()
                        prefs.lockNtpOffset = offset
                        prefs.lockUntil = ntpTime + durationMs
                    }
                }.start()
            },
            onDismiss = { showLockDialog = false }
        )
    }
}

@Composable
fun rememberAppIconPainter(drawable: android.graphics.drawable.Drawable?): androidx.compose.ui.graphics.painter.Painter {
    return remember(drawable) {
        val bitmap = try {
            drawable?.toBitmap() ?: createBitmap(1, 1)
        } catch (e: Exception) {
            createBitmap(1, 1)
        }
        androidx.compose.ui.graphics.painter.BitmapPainter(bitmap.asImageBitmap())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SecurityScreenHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2A4A), ScreenBg)))
            .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 24.dp)
    ) {
        Column {
            Text("🛡️ Security", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Manage protection and VPN settings", fontSize = 13.sp, color = TextSecond)
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text          = label,
        fontSize      = 10.sp,
        fontWeight    = FontWeight.Bold,
        color         = Color(0xFF334155),
        letterSpacing = 1.5.sp,
        modifier      = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews — stateless, no PrefsManager/Context needed
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SecurityScreenContent(
    keepVpnAlive:         Boolean,
    preventVpnOverride:   Boolean,
    antiUninstallEnabled: Boolean,
    onKeepVpnToggle:      (Boolean) -> Unit,
    onPreventToggle:      (Boolean) -> Unit,
    onAntiUninstallToggle:(Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        SecurityScreenHeader()
        Spacer(Modifier.height(8.dp))

        SectionLabel("VPN PROTECTION")
        ToggleCard(
            emoji    = "♻️",
            title    = "Keep VPN Alive",
            subtitle = "Some phones kill VPN unexpectedly. We'll attempt to keep it on for as long as possible.",
            isEnabled = keepVpnAlive,
            onToggle  = onKeepVpnToggle
        )
        HorizontalDivider(
            color     = DividerCol,
            thickness = 0.5.dp,
            modifier  = Modifier.padding(horizontal = 20.dp)
        )
        ToggleCard(
            emoji    = "🔒",
            title    = "Prevent VPN Override",
            subtitle = "Prevents another VPN app from overriding Digital Monk's filter.",
            isEnabled = preventVpnOverride,
            onToggle  = onPreventToggle
        )

        Spacer(Modifier.height(24.dp))

        SectionLabel("ANTI-UNINSTALL")
        ToggleCard(
            emoji    = "🛡️",
            title    = "Prevent Settings Tampering",
            subtitle = "Blocks access to Force Stop, Uninstall, and Device Admin pages for Digital Monk.",
            isEnabled = antiUninstallEnabled,
            onToggle  = onAntiUninstallToggle
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Preview(name = "Security — All Off", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
private fun SecurityScreenPreviewAllOff() {
    MaterialTheme {
        SecurityScreenContent(
            keepVpnAlive          = false,
            preventVpnOverride    = false,
            antiUninstallEnabled  = false,
            onKeepVpnToggle       = {},
            onPreventToggle       = {},
            onAntiUninstallToggle = {}
        )
    }
}

@Preview(name = "Security — All On", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
private fun SecurityScreenPreviewAllOn() {
    MaterialTheme {
        SecurityScreenContent(
            keepVpnAlive          = true,
            preventVpnOverride    = true,
            antiUninstallEnabled  = true,
            onKeepVpnToggle       = {},
            onPreventToggle       = {},
            onAntiUninstallToggle = {}
        )
    }
}