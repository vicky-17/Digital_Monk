package com.example.digitalmonk.service.overlay


import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Design tokens specific to the Blocked Page screen.
 * Contains all styling constants used throughout the blocked page UI.
 */
object BlockedPageTokens {
    // Colors
    val backgroundColor: Color = Color(0xB00E182C)
    val textColor: Color = Color(0xFFFFFFFF)
    val buttonBackgroundColor: Color = Color(0xFF0064A2)

    // Font sizes
    val headerFontSize = 14.sp
    val buttonFontSize = 14.sp

    // Spacing
    val horizontalPadding = 24.dp
    val spacingBetweenHeaderAndMessage = 23.dp
    val spacingBetweenMessageAndButton = 87.dp

    // Button dimensions
    val buttonCornerRadius = 4.dp
    val buttonHorizontalPadding = 20.dp
    val buttonTextHorizontalPadding = 32.dp
    val buttonTextVerticalPadding = 8.dp
}
