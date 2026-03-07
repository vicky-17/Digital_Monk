package com.example.digitalmonk.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.digitalmonk.data.PrefsManager
import com.example.digitalmonk.service.GuardianAccessibilityService
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PrefsManager(this)

        // Route to PIN setup if first launch
        if (!prefs.hasPin()) {
            startActivity(Intent(this, PinSetupActivity::class.java))
            finish()
            return
        }

        setContent {
            DigitalMonkTheme {
                AppContent(prefs)
            }
        }
    }

    @Composable
    fun AppContent(prefs: PrefsManager) {
        var isUnlocked by remember { mutableStateOf(false) }

        if (isUnlocked) {
            Dashboard(prefs, onLock = { isUnlocked = false })
        } else {
            PinGateScreen(prefs, onSuccess = { isUnlocked = true })
        }
    }

    // ─── PIN GATE ─────────────────────────────────────────────────────────────

    @Composable
    fun PinGateScreen(prefs: PrefsManager, onSuccess: () -> Unit) {
        var enteredPin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔒", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Parent Dashboard",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Enter your PIN to continue",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = enteredPin,
                onValueChange = {
                    if (it.length <= 6) {
                        enteredPin = it
                        error = false
                    }
                },
                label = { Text("Parent PIN", color = Color(0xFF94A3B8)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error,
                supportingText = {
                    if (error) Text("Incorrect PIN", color = Color(0xFFEF4444))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (enteredPin == prefs.getPin()) {
                        onSuccess()
                    } else {
                        error = true
                        enteredPin = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text("Unlock", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // ─── DASHBOARD ────────────────────────────────────────────────────────────

    @Composable
    fun Dashboard(prefs: PrefsManager, onLock: () -> Unit) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var refreshKey by remember { mutableLongStateOf(0L) }

        // Refresh permission states when returning from Settings
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshKey = System.currentTimeMillis()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val isAccessibilityOn by remember(refreshKey) {
            mutableStateOf(isAccessibilityEnabled(context))
        }
        var blockShorts by remember { mutableStateOf(prefs.blockShorts) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Digital Monk",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text("Parent Dashboard", fontSize = 13.sp, color = Color(0xFF64748B))
                }
                TextButton(onClick = onLock) {
                    Text("Lock 🔒", color = Color(0xFF64748B))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── SERVICE STATUS CARD ──────────────────────────────────────────
            SectionLabel("Service Status")
            Spacer(modifier = Modifier.height(8.dp))

            StatusCard(
                title = "Accessibility Service",
                description = if (isAccessibilityOn)
                    "Active — Monitoring is running"
                else
                    "Disabled — Tap to enable in Settings",
                isActive = isAccessibilityOn,
                onClick = {
                    if (!isAccessibilityOn) {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        Toast.makeText(
                            context,
                            "Find 'Digital Monk' and turn ON",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── SHORTS BLOCKER ────────────────────────────────────────────────
            SectionLabel("Content Filters")
            Spacer(modifier = Modifier.height(8.dp))

            ToggleCard(
                title = "Block Short Videos",
                description = "Blocks YouTube Shorts, Instagram Reels, TikTok",
                emoji = "📵",
                isEnabled = blockShorts,
                onToggle = {
                    blockShorts = it
                    prefs.blockShorts = it
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── CHANGE PIN ────────────────────────────────────────────────────
            SectionLabel("Security")
            Spacer(modifier = Modifier.height(8.dp))

            ActionCard(
                title = "Change PIN",
                description = "Update your parent access PIN",
                emoji = "🔑",
                onClick = {
                    startActivity(Intent(this@MainActivity, PinSetupActivity::class.java))
                }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Footer note
            if (!isAccessibilityOn) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text("ℹ️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "The Accessibility Service must be enabled for blocking to work. " +
                                    "Go to Settings → Accessibility → Digital Monk → Turn ON.",
                            fontSize = 13.sp,
                            color = Color(0xFF93C5FD)
                        )
                    }
                }
            }
        }
    }

    // ─── REUSABLE COMPONENTS ──────────────────────────────────────────────────

    @Composable
    fun SectionLabel(text: String) {
        Text(
            text.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569),
            letterSpacing = 1.sp
        )
    }

    @Composable
    fun StatusCard(
        title: String,
        description: String,
        isActive: Boolean,
        onClick: () -> Unit
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) Color(0xFF052E16) else Color(0xFF1C1917)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isActive) "🟢" else "🔴", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                    Text(description, color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                if (!isActive) {
                    Text("FIX →", color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun ToggleCard(
        title: String,
        description: String,
        emoji: String,
        isEnabled: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                    Text(description, color = Color(0xFF64748B), fontSize = 12.sp)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF3B82F6),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            }
        }
    }

    @Composable
    fun ActionCard(title: String, description: String, emoji: String, onClick: () -> Unit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                    Text(description, color = Color(0xFF64748B), fontSize = 12.sp)
                }
                Text("→", color = Color(0xFF475569), fontSize = 18.sp)
            }
        }
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private fun isAccessibilityEnabled(context: android.content.Context): Boolean {
        val expectedComponent = ComponentName(context, GuardianAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val comp = ComponentName.unflattenFromString(splitter.next())
            if (comp == expectedComponent) return true
        }
        return false
    }
}