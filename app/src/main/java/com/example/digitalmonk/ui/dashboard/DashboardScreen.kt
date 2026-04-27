package com.example.digitalmonk.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.digitalmonk.ui.components.cards.ActionCard
import com.example.digitalmonk.ui.components.cards.StatusCard
import com.example.digitalmonk.ui.components.common.SectionLabel

/**
 * DashboardScreen remains in Kotlin.
 * It displays the overall system status by observing Java-driven background states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToAppBlock: () -> Unit,
    onNavigateToContentFilter: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // Observing state from ViewModel which interacts with Java PermissionHelper
    val isVpnActive by viewModel.isVpnActive.collectAsState()
    val isAccessibilityActive by viewModel.isAccessibilityActive.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Digital Monk", color = Color.White) },
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
            SectionLabel("System Status")

            // Status Cards show real-time state of your Java Services
            StatusCard(
                label = "Web Protection (VPN)",
                statusText = if (isVpnActive) "Active & Filtering" else "Protection Disabled",
                isActive = isVpnActive
            )

            StatusCard(
                label = "Guardian Eye (Accessibility)",
                statusText = if (isAccessibilityActive) "Monitoring Active" else "Setup Required",
                isActive = isAccessibilityActive
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel("Controls")

            // Action Cards navigate to other sections
            ActionCard(
                title = "App Blocking",
                description = "Restrict specific apps or block YouTube Shorts.",
                icon = Icons.Default.Apps,
                onClick = onNavigateToAppBlock
            )

            Spacer(modifier = Modifier.height(12.dp))

            ActionCard(
                title = "Content Filtering",
                description = "Manage SafeSearch and DNS level filters.",
                icon = Icons.Default.Security,
                onClick = onNavigateToContentFilter
            )

            Spacer(modifier = Modifier.height(12.dp))

            ActionCard(
                title = "Parental Settings",
                description = "Change PIN and manage app preferences.",
                icon = Icons.Default.Settings,
                onClick = onNavigateToSettings
            )
        }
    }
}