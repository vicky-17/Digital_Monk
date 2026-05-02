package com.example.digitalmonk.service.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.digitalmonk.ui.theme.inclusiveSans
import com.example.digitalmonk.ui.theme.inknutAntiqua

/**
 * Splash screen composable that displays the app branding.
 *
 * This screen shows a two-tone background with a header section in steel blue
 * and a main content area in dark gray, featuring the "Protected by Digital Monk" tagline.
 *
 * @param modifier Optional modifier for customizing the layout.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DesignTokens.colorDarkNavy)
    ) {
        // Header section with steel blue background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesignTokens.headerHeight)
                .background(DesignTokens.colorSteelBlue)
        )

        // Main content area with branding text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(DesignTokens.colorDarkNavy),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🛡️  Protected by Digital Monk",
                color = DesignTokens.colorWhite,
                style = TextStyle(
                    fontSize = DesignTokens.fontSizeMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = inknutAntiqua,
                    color = DesignTokens.colorWhite
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    AppTheme {
        SplashScreen()
    }
}
