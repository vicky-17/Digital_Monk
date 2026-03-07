package com.example.digitalmonk.ui.dashboard

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun DashboardScreen() {
    Text("Dashboard")
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    com.example.digitalmonk.ui.theme.DigitalMonkTheme {
        DashboardScreen()
    }
}
