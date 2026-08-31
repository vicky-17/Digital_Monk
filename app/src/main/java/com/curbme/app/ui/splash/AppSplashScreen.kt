package com.curbme.app.ui.splash

import com.curbme.app.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.theme.CurbMeTheme
import kotlinx.coroutines.delay

private val BgDeep = Color(0xFF04040C)
private val AccentBlue = Color(0xFF3B82F6)
private val AccentViolet = Color(0xFF8B5CF6)
private val AccentCyan = Color(0xFF06B6D4)
private val TextSecond = Color(0xFF94A3B8)

@Composable
fun AppSplashScreen(
    onSplashFinished: () -> Unit
) {
    val isPreview = LocalInspectionMode.current
    var progressTarget by remember { mutableFloatStateOf(if (isPreview) 1f else 0f) }

    // Smooth Progress Animation
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "animatedProgress"
    )

    // Fast, lightweight progress update
    LaunchedEffect(Unit) {
        if (isPreview) return@LaunchedEffect

        val totalTime = 800L
        val steps = 20
        val stepDelay = totalTime / steps

        for (i in 1..steps) {
            delay(stepDelay)
            progressTarget = i / steps.toFloat()
        }

        delay(80)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Main Icon Card - Static, clean, lightweight
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF111827))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(AccentCyan, AccentViolet)
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "CurbMe Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name with Gradient Text Effect
            Text(
                text = "CurbMe",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.White, AccentCyan, AccentViolet)
                    )
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Digital Wellbeing & Focus Engine",
                color = TextSecond,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Custom Progress Bar Container - Ultra-lightweight GPU scaling
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = animatedProgress.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(AccentBlue, AccentCyan, AccentViolet)
                            )
                        )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppSplashScreenPreview() {
    CurbMeTheme {
        AppSplashScreen(onSplashFinished = {})
    }
}
