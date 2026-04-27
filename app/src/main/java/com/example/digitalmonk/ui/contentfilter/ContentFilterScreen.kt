package com.example.digitalmonk.ui.contentfilter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.ui.components.cards.ToggleCard
import com.example.digitalmonk.ui.components.common.SectionLabel
import com.example.digitalmonk.ui.components.dialogs.PinDialog

/**
 * ContentFilterScreen remains in Kotlin.
 * It controls the Java-based DNS filtering and VPN settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentFilterScreen() {
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context.applicationContext) }

    // UI State for toggles, synced with Java PrefsManager
    var isSafeSearchEnabled by remember { mutableStateOf(prefs.isSafeSearchEnabled) }
    var isYoutubeFilterEnabled by remember { mutableStateOf(prefs.isYoutubeFilterEnabled) }

    // Security State
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingToggle by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showPinDialog) {
        PinDialog(
            prefs = prefs,
            onSuccess = {
                showPinDialog = false
                pendingToggle?.invoke()
                pendingToggle = null
            },
            onDismiss = {
                showPinDialog = false
                pendingToggle = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Content Filtering", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionLabel("Web Protection")

            ToggleCard(
                title = "Enforce Safe Search",
                description = "Forces Google, Bing, and DuckDuckGo into strict filtering mode.",
                isChecked = isSafeSearchEnabled,
                onCheckedChange = { newValue ->
                    pendingToggle = {
                        prefs.isSafeSearchEnabled = newValue
                        isSafeSearchEnabled = newValue
                    }
                    showPinDialog = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("Social Media")

            ToggleCard(
                title = "YouTube Restricted Mode",
                description = "Hides potentially mature videos and filters comments on YouTube.",
                isChecked = isYoutubeFilterEnabled,
                onCheckedChange = { newValue ->
                    pendingToggle = {
                        prefs.isYoutubeFilterEnabled = newValue
                        isYoutubeFilterEnabled = newValue
                    }
                    showPinDialog = true
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Helpful tip for the parent
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF3B82F6))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "These filters apply system-wide using a local VPN tunnel.",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}