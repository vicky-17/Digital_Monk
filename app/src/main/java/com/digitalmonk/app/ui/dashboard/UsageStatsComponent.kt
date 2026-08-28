package com.digitalmonk.app.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.digitalmonk.app.data.models.AppUsageInfo
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

// ── Liquid Glass Palette ──────────────────────────────────────────────────────
private val GlassBg       = Color(0xFFf5f6fb).copy(alpha = 0.07f)
private val GlassBorder   = Color(0xFFf5f6fb).copy(alpha = 0.16f)
private val AccentBlue     = Color(0xFF3B82F6)
private val AccentViolet   = Color(0xFF8B5CF6)
private val AccentPink     = Color(0xFFEC4899)
private val AccentOrange   = Color(0xFFF59E0B)
private val AccentYellow   = Color(0xFFFACC15)
private val AccentTeal     = Color(0xFF14B8A6)
private val AccentGreen    = Color(0xFF34D399)
private val TextPrimary    = Color(0xFFf5f6fb)
private val TextSecond     = Color(0xFFf5f6fb).copy(alpha = 0.62f)
private val TextMuted      = Color(0xFFf5f6fb).copy(alpha = 0.38f)

@Composable
fun UsageStatsSection(
    viewModel: UsageViewModel,
    onOpenAllStats: () -> Unit
) {
    val usageStats by viewModel.todayStats.collectAsState()
    val comparisonPercent by viewModel.comparisonPercent.collectAsState()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
    val context = LocalContext.current

    UsageStatsSection(
        usageStats = usageStats,
        comparisonPercent = comparisonPercent,
        isPermissionGranted = isPermissionGranted,
        onOpenAllStats = onOpenAllStats,
        onGrantPermission = {
            try {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {}
        }
    )
}

@Composable
fun UsageStatsSection(
    usageStats: List<AppUsageInfo>,
    comparisonPercent: Int,
    isPermissionGranted: Boolean,
    onOpenAllStats: () -> Unit,
    onGrantPermission: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GlassBg),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, GlassBorder)
        ){
            if (!isPermissionGranted) {
                PermissionRequestCard(onGrant = onGrantPermission)
            } else {
                UsageHero(usageStats, comparisonPercent)

                Spacer(Modifier.height(24.dp))

                InsightCard(comparisonPercent)

                Spacer(Modifier.height(14.dp))

                MostUsedAppsSection(usageStats, onOpenAllStats)
            }
        }

    }
}

@Composable
private fun UsageHero(stats: List<AppUsageInfo>, comparison: Int) {
    val totalTimeMs = stats.sumOf { it.usageTimeMs }
    // Take 3 specific apps, "Others" will be the 4th item
    val topApps = stats.take(3)
    val remainingMs = totalTimeMs - topApps.sumOf { it.usageTimeMs }

    val colors = listOf(AccentBlue, AccentViolet, AccentPink, AccentOrange, AccentYellow, AccentTeal)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left Side: Precision Dual Ring Layout ────────────────────────
        Box(
            modifier = Modifier
                .weight(1.35f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            UsageRingLayout(topApps, totalTimeMs.toFloat().coerceAtLeast(1f), colors)

            // ── Inner Solid Circle (Strictly contains all text safely) ──
            Box(
                modifier = Modifier
                    .fillMaxSize(0.74f) // Increased size to prevent text hitting the rings
                    .clip(CircleShape)
                    .background(Color(0xFF04040c))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("Total screen time", color = TextSecond, fontSize = 8.5.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = formatDuration(totalTimeMs),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    val todayStr = remember {
                        java.text.SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())
                    }
                    Text("Today, $todayStr", color = TextSecond, fontSize = 8.5.sp)

                    Spacer(Modifier.height(4.dp))

                    val isLess = comparison <= 0
                    Text(
                        text = "${if (isLess) "↓" else "↑"} ${kotlin.math.abs(comparison)}% vs yesterday",
                        color = if (isLess) AccentGreen else Color(0xFFF87171),
                        fontSize = 9.5.sp, // Scaled to ensure full text fits inside
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // ── Right Side: Compact Legend ──────────────────────────────────
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            topApps.forEachIndexed { index, app ->
                LegendItem(app.appName, formatDurationShort(app.usageTimeMs), colors[index % colors.size], app.icon)
            }
            if (remainingMs > 0) {
                LegendItem("Others", formatDurationShort(remainingMs), Color.White.copy(alpha = 0.12f), null)
            }
        }
    }
}

@Composable
private fun LegendItem(name: String, duration: String, color: Color, icon: Any?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.5.dp, height = 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )

        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            icon?.let {
                Image(
                    painter = rememberAsyncImagePainter(it),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } ?: Box(Modifier.size(20.dp).background(Color.White.copy(0.08f), CircleShape))
        }

        Column {
            Text(
                name,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = duration,
                color = TextSecond,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun UsageRingLayout(topApps: List<AppUsageInfo>, totalMs: Float, colors: List<Color>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val ringRadius = (size.minDimension / 2f) * 0.9f
        val strokeWidth = 16.dp.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // ── Circle 1: The Background Track (Subtle Outer Rim) ────────────────
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = ringRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )

        val topAppsTotal = topApps.sumOf { it.usageTimeMs }.toFloat()

        // ── 1. Draw "Others" segment FIRST (so it is at the bottom of the stack) ──
        if (topAppsTotal < totalMs) {
            val topAppsSweep = (topAppsTotal / totalMs) * 360f
            val remainingSweep = 360f - topAppsSweep
            drawArc(
                color = Color.White.copy(alpha = 0.15f),
                startAngle = -90f + topAppsSweep,
                sweepAngle = remainingSweep,
                useCenter = false,
                topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // ── 2. Draw Apps (on top of the Others segment) ──
        var startAngle = -90f
        topApps.forEachIndexed { index, app ->
            val sweepAngle = (app.usageTimeMs.toFloat() / totalMs) * 360f
            val color = colors[index % colors.size]

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle * 0.98f, // Slight gap for clarity
                useCenter = false,
                topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            startAngle += sweepAngle
        }
    }
}


@Composable
private fun InsightCard(comparison: Int) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 10.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBg),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Brush.linearGradient(listOf(AccentBlue.copy(0.5f), AccentViolet.copy(0.5f))), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFeaf1ff), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            val isLess = comparison <= 0
            Text(
                text = buildAnnotatedString {
                    append("Your screen time is ")
                    withStyle(SpanStyle(color = AccentGreen, fontWeight = FontWeight.Bold)) {
                        append("${kotlin.math.abs(comparison)}% ${if (isLess) "less" else "more"}")
                    }
                    append(" than yesterday. ${if (isLess) "Keep it up!" else "Try to focus!"}")
                },
                color = TextSecond,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MostUsedAppsSection(stats: List<AppUsageInfo>, onShowAll: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Most used apps", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            Text(
                "Show all",
                color = Color(0xFF7fb4ff),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onShowAll() }
            )
        }

        Spacer(Modifier.height(14.dp))

        val topApps = stats.take(3)
        val maxUsage = topApps.firstOrNull()?.usageTimeMs?.toFloat() ?: 1f

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            topApps.forEachIndexed { index, app ->
                AppRow(app, app.usageTimeMs.toFloat() / maxUsage, index)
            }
        }
    }
}

@Composable
private fun AppRow(app: AppUsageInfo, fraction: Float, index: Int) {
    val colors = listOf(AccentBlue, AccentViolet, AccentGreen)
    Row(
        modifier = Modifier.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        app.icon?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(app.appName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(formatDurationShort(app.usageTimeMs), color = TextSecond, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(colors[index % colors.size], colors[index % colors.size].copy(alpha = 0.5f))),
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun PermissionRequestCard(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Usage Access Required", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "Enable insights to see your dashboard data.",
                color = TextSecond,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Text("Grant Permission")
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    return com.digitalmonk.app.core.utils.TimeUtils.formatDuration(ms)
}

private fun formatDurationShort(ms: Long): String {
    return com.digitalmonk.app.core.utils.TimeUtils.formatDurationShort(ms)
}

@Preview(showBackground = true, backgroundColor = 0xFF04040c)
@Composable
fun UsageStatsPreview() {
    val mockUsageData = listOf(
        AppUsageInfo("com.google.android.youtube", "YouTube", null, 120 * 60 * 1000L, 10),
        AppUsageInfo("com.instagram.android", "Instagram", null, 45 * 60 * 1000L, 5),
        AppUsageInfo("com.zhiliaoapp.musically", "TikTok", null, 30 * 60 * 1000L, 8),
        AppUsageInfo("com.whatsapp", "WhatsApp", null, 15 * 60 * 1000L, 20),
        AppUsageInfo("com.android.chrome", "Chrome", null, 10 * 60 * 1000L, 3)
    )

    Box(modifier = Modifier.background(Color(0xFF04040c)).padding(16.dp)) {
        UsageStatsSection(
            usageStats = mockUsageData,
            comparisonPercent = -15,
            isPermissionGranted = true,
            onOpenAllStats = {},
            onGrantPermission = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF04040c)
@Composable
fun UsageStatsPermissionPreview() {
    Box(modifier = Modifier.background(Color(0xFF04040c)).padding(16.dp)) {
        UsageStatsSection(
            usageStats = emptyList(),
            comparisonPercent = 0,
            isPermissionGranted = false,
            onOpenAllStats = {},
            onGrantPermission = {}
        )
    }
}
