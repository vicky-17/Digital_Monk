package com.example.digitalmonk.ui.activities

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.digitalmonk.utils.Constants
import com.example.digitalmonk.receivers.MyAppAdminReceiver
import com.example.digitalmonk.data.PrefsManager
import com.example.digitalmonk.services.LiveGuardianService
import com.example.digitalmonk.services.SyncService
import com.example.digitalmonk.utils.UsageStatsHelper
import com.example.digitalmonk.ui.theme.G4ParentalMonitorTheme
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // No need to setContent here, the LifecycleObserver will handle the refresh
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent.getBooleanExtra("SYNC_NOW", false)) {
            Toast.makeText(this, "Syncing App Data...", Toast.LENGTH_LONG).show()
            uploadDeviceData()
        }

        setContent { AppUI() }
    }

    @Composable
    fun AppUI() {
        val context = LocalContext.current
        val scrollState = rememberScrollState()
        val usageScrollState = rememberScrollState()
        val rawScrollState = rememberScrollState()
        val lifecycleOwner = LocalLifecycleOwner.current

        // --- AUTOMATIC REFRESH LOGIC ---
        var refreshTrigger by remember { mutableLongStateOf(System.currentTimeMillis()) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshTrigger = System.currentTimeMillis()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        var usageList by remember { mutableStateOf<List<AppUsageItem>>(emptyList()) }
        var rawUsageList by remember { mutableStateOf<List<RawUsageItem>>(emptyList()) }
        var lastUsageUpdatedAt by remember { mutableStateOf<Long?>(null) }
        var showRaw by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(refreshTrigger) {
            isLoading = true // Start loading

            // Move the heavy work to the background thread (IO) so the UI doesn't freeze
            withContext(Dispatchers.IO) {
                // 1. Get Usage Map
                val realUsageMap = UsageStatsHelper.getEventBasedDailyUsage(context)
                val pm = context.packageManager

                // 2. Get All Installed Apps
                val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

                // 3. Apply Filtering Logic
                val parsed = allApps.filter { app ->
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    !isSystem || pm.getLaunchIntentForPackage(app.packageName) != null
                }.map { app ->
                    val totalMs = realUsageMap[app.packageName] ?: 0L
                    val minutes = totalMs / (1000 * 60)

                    AppUsageItem(
                        name = pm.getApplicationLabel(app).toString(),
                        packageName = app.packageName,
                        minutes = minutes
                    )
                }.sortedByDescending { it.minutes }

                val rawDaily = UsageStatsHelper.getRawDailyUsageStats(context)
                val rawParsed = rawDaily.mapNotNull { item ->
                    val pkg = item["packageName"] as? String ?: return@mapNotNull null
                    val name = item["appName"] as? String ?: pkg
                    val minutes = (item["minutes"] as? Number)?.toLong() ?: 0L
                    val totalMs = (item["totalMs"] as? Number)?.toLong() ?: 0L
                    RawUsageItem(name = name, packageName = pkg, minutes = minutes, totalMs = totalMs)
                }.sortedByDescending { it.minutes }

                // Switch back to Main Thread to update the UI variables safely
                withContext(Dispatchers.Main) {
                    usageList = parsed
                    rawUsageList = rawParsed
                    lastUsageUpdatedAt = System.currentTimeMillis()
                    isLoading = false // Stop loading
                }
            }
        }

        G4ParentalMonitorTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(20.dp)
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Text("G4 Monitor Setup", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Grant these permissions to activate monitoring.", color = Color.Gray)

                    Spacer(modifier = Modifier.height(40.dp))
                    HorizontalDivider(thickness = 1.dp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Troubleshooting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(context, PairingActivity::class.java)
                            intent.putExtra("FORCE_PAIR", true)
                            startActivity(intent)
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Connect Again / Re-pair Device")
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // --- BASIC PERMISSIONS ---
                    Text("Basic Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    val isUsageGranted = hasUsageStatsPermission()
                    PermissionItem(
                        name = "1. Usage Access",
                        description = "Required to see app usage time.",
                        isGranted = isUsageGranted,
                        onClick = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        PermissionItem(
                            name = "2. All Files Access",
                            description = "Required for monitoring logs.",
                            isGranted = Environment.isExternalStorageManager(),
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                    intent.data = Uri.parse("package:$packageName")
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                }
                            }
                        )
                    }

                    val runtimePermissionsGranted = areRuntimePermissionsGranted()
                    PermissionItem(
                        name = "3. Location & Camera",
                        description = "Standard Android permissions.",
                        isGranted = runtimePermissionsGranted,
                        onClick = { if (!runtimePermissionsGranted) requestRuntimePermissions() }
                    )

                    // --- STABILITY & DISPLAY (NEW SECTION) ---
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(thickness = 1.dp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Stability & Display (Critical)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Required to prevent app from stopping.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    val isOverlayGranted = hasOverlayPermission()
                    PermissionItem(
                        name = "4. Display Over Other Apps",
                        description = "Required to show the blocked screen.",
                        isGranted = isOverlayGranted,
                        onClick = {
                            if (!isOverlayGranted) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                    startActivity(intent)
                                }
                            } else {
                                Toast.makeText(context, "Overlay permission already granted!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    val isBatteryIgnored = isBatteryOptimizationIgnored()
                    PermissionItem(
                        name = "5. Ignore Battery Opt",
                        description = "Prevents system from killing the app.",
                        isGranted = isBatteryIgnored,
                        onClick = {
                            if (!isBatteryIgnored) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Manually go to Settings > Battery > Optimization", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Battery optimization already ignored!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    // Update Check: Include new permissions in the "All Good" check
                    val allGood = isUsageGranted &&
                            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) &&
                            runtimePermissionsGranted &&
                            isOverlayGranted &&
                            isBatteryIgnored

                    Button(
                        onClick = {
                            if (allGood) {
                                startMonitoringService()
                                Toast.makeText(context, "Service Started!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please grant all permissions first.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (allGood) Color(0xFF4CAF50) else Color.Gray)
                    ) {
                        Text(if (allGood) "START MONITORING SERVICE" else "SETUP INCOMPLETE")
                    }

                    // --- SECURITY SETUP SECTION ---
                    Spacer(modifier = Modifier.height(30.dp))

                    HorizontalDivider(thickness = 1.dp, color = Color.LightGray)

                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Security Features", style = MaterialTheme.typography.titleLarge)
                    Text("Enable these to prevent uninstallation.", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Anti-Uninstall
                    val isAdmin = isDeviceAdminActive(context)
                    PermissionItem(
                        name = "1. Anti-Uninstall",
                        description = "Prevents the app from being deleted.",
                        isGranted = isAdmin,
                        onClick = {
                            if (!isAdmin) {
                                val compName = ComponentName(context, MyAppAdminReceiver::class.java)
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents uninstallation.")
                                startActivity(intent)
                            } else {
                                Toast.makeText(context, "Anti-Uninstall is already active!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // 2. Settings Lock
                    val isAccessibilityEnabled = isAccessibilityServiceEnabled(context, LiveGuardianService::class.java)
                    PermissionItem(
                        name = "2. Lock Settings",
                        description = "Blocks access to Settings without PIN.",
                        isGranted = isAccessibilityEnabled,
                        onClick = {
                            if (!isAccessibilityEnabled) {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                startActivity(intent)
                                Toast.makeText(context, "Find 'G4 Parental Monitor' and turn ON", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Settings Lock is active!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // --- LATEST APP USAGE (SCROLLABLE LIST) ---
                    Spacer(modifier = Modifier.height(30.dp))
                    HorizontalDivider(thickness = 1.dp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(20.dp))

                    // Added a Row to put the Refresh Button next to the Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Latest App Usage", style = MaterialTheme.typography.titleLarge)
                            Text("Sorted by highest usage today (All Apps).", color = Color.Gray, fontSize = 14.sp)
                        }
                        // The Refresh Button
                        IconButton(
                            onClick = { refreshTrigger = System.currentTimeMillis() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text("🔄", fontSize = 24.sp)
                        }
                    }

                    val lastUpdatedText = lastUsageUpdatedAt?.let {
                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
                    }
                    if (lastUpdatedText != null) {
                        Text("Updated $lastUpdatedText", color = Color.Gray, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Crossfade creates a smooth fading animation between loading and the list
                    androidx.compose.animation.Crossfade(
                        targetState = isLoading,
                        label = "LoadingTransition"
                    ) { loading ->
                        if (loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF2563EB))
                            }
                        } else {
                            Column {
                                if (!isUsageGranted) {
                                    Text(
                                        "Usage Access permission is required to show app usage.",
                                        color = Color.Red,
                                        fontSize = 13.sp
                                    )
                                } else if (usageList.isEmpty()) {
                                    Text("No usage data available yet.", color = Color.Gray, fontSize = 13.sp)
                                } else {
                                    Text(
                                        "Showing ${usageList.size} apps. Scroll to see all.",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 520.dp)
                                            .verticalScroll(usageScrollState)
                                    ) {
                                        usageList.forEachIndexed { index, item ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${index + 1}.",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        modifier = Modifier.width(28.dp)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = item.name,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 14.sp
                                                        )
                                                        Text(
                                                            text = item.packageName,
                                                            color = Color.Gray,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = "${item.minutes}m",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = Color(0xFF2563EB)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "Hide Raw Usage Data" else "Show Raw Usage Data")
                    }

                    if (showRaw) {
                        Text(
                            "Raw UsageStatsManager daily data (${rawUsageList.size} items).",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rawScrollState)
                        ) {
                            rawUsageList.forEach { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = item.packageName,
                                                color = Color.Gray,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${item.minutes}m",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = "${item.totalMs}ms",
                                                color = Color.Gray,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    @Composable
    fun PermissionItem(name: String, description: String, isGranted: Boolean, onClick: () -> Unit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isGranted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = description, fontSize = 12.sp, color = Color.Gray)
                }
                Text(
                    text = if (isGranted) "✅" else "❌",
                    fontSize = 20.sp
                )
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    private fun areRuntimePermissionsGranted(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRuntimePermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun startMonitoringService() {
        val serviceIntent = Intent(this, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(context, MyAppAdminReceiver::class.java)
        return dpm.isAdminActive(compName)
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName)
                return true
        }
        return false
    }

    private fun uploadDeviceData() {
        val prefs = PrefsManager(this)
        val deviceId = prefs.deviceId ?: return

        val pm = packageManager
        val appsList = pm.getInstalledPackages(0)
        val jsonArray = JSONArray()

        for (pkg in appsList) {
            val appInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdated = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            if (!isSystem || isUpdated) {
                val appObj = JSONObject()
                appObj.put("appName", appInfo.loadLabel(pm).toString())
                appObj.put("packageName", pkg.packageName)
                appObj.put("seconds", 0)
                appObj.put("initialSync", true)
                jsonArray.put(appObj)
            }
        }

        val jsonBody = JSONObject()
        jsonBody.put("deviceId", deviceId)
        jsonBody.put("apps", jsonArray)

        val client = OkHttpClient()
        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(Constants.APPS_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Sync", "Failed to upload apps", e)
                runOnUiThread {
                    Toast.makeText(applicationContext, "Sync Failed: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Apps Uploaded Successfully!", Toast.LENGTH_SHORT).show()
                    }
                    startMonitoringService()
                } else {
                    Log.e("Sync", "Server Error: " + response.code)
                }
            }
        })
    }
}

data class AppUsageItem(
    val name: String,
    val packageName: String,
    val minutes: Long
)

data class RawUsageItem(
    val name: String,
    val packageName: String,
    val minutes: Long,
    val totalMs: Long
)