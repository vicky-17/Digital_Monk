package com.example.digitalmonk.ui.components.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SectionLabel remains in Kotlin to support Compose UI.
 * It provides a consistent styling for headers throughout the app.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF94A3B8), // Updated to a lighter slate for better readability in dark mode
        letterSpacing = 1.sp,
        modifier = modifier.padding(vertical = 8.dp)
    )
}