package com.example.digitalmonk.service.overlay


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.settingspageoverlay.ui.theme.AppTheme
import com.example.settingspageoverlay.ui.theme.inknutAntiqua
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * A screen displaying a protection status banner at the bottom.
 *
 * This screen shows a full-screen blue background with a footer banner
 * indicating the app is protected by Digital Monk.
 *
 * @sample ProtectionBannerScreenPreview
 */
@Composable
fun ProtectionBannerScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.colorPrimary)
    ) {
        // Main content area
        Spacer(modifier = Modifier.weight(1f))

        // Protection banner at bottom
        ProtectionBanner()
    }
}

/**
 * A banner component displaying protection status.
 *
 * Shows a semi-transparent dark banner with shield emoji and protection text.
 * Typically placed at the bottom of a screen.
 */
@Composable
fun ProtectionBanner() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(DesignTokens.bannerHeight)
            .background(DesignTokens.colorBannerBackground)
            .padding(
                horizontal = DesignTokens.spacingLarge,
                vertical = DesignTokens.spacingMedium
            )
    ) {
        Text(
            text = stringResource(id = R.string.protected_by_digital_monk),
            color = DesignTokens.colorWhite,
            style = TextStyle(
                fontSize = DesignTokens.textSizeNormal,
                fontWeight = FontWeight.Bold,
                fontFamily = inknutAntiqua
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProtectionBannerScreenPreview() {
    AppTheme {
        ProtectionBannerScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun ProtectionBannerPreview() {
    AppTheme {
        ProtectionBanner()
    }
}
