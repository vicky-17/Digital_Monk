package com.digitalmonk.app.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
private val AccentGray     = Color(0xFF8B93A7)
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

@Composable
private fun UsageHero(stats: List<AppUsageInfo>, comparison: Int) {
    val totalTimeMs = stats.sumOf { it.usageTimeMs }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        UsageRingLayout(stats)
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .size(160.dp)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(Color(0xFF04040c))
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                .padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Total screen time", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = formatDuration(totalTimeMs),
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            val todayStr = remember { 
                java.text.SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date()) 
            }
            Text("Today, $todayStr", color = TextSecond, fontSize = 10.sp)
            
            Spacer(Modifier.height(6.dp))
            
            val isLess = comparison <= 0
            Text(
                text = "${if (isLess) "↓" else "↑"} ${kotlin.math.abs(comparison)}% vs yesterday",
                color = if (isLess) AccentGreen else Color(0xFFF87171),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UsageRingLayout(stats: List<AppUsageInfo>) {
    val totalMs = stats.sumOf { it.usageTimeMs }.toFloat().coerceAtLeast(1f)
    val topApps = stats.take(6)
    val colors = listOf(AccentGray, AccentBlue, AccentViolet, AccentPink, AccentOrange, AccentYellow, AccentTeal)

    // ── Unified Angle Mapping (Top, Bottom, and 4 Corners) ──────────────
    val appAngles = remember(topApps, totalMs) {
        var currentAngle = -90f
        val actualMids = topApps.map { app ->
            val sweep = (app.usageTimeMs.toFloat() / totalMs) * 360f
            val mid = currentAngle + (sweep / 2f)
            currentAngle += sweep
            mid
        }
        
        // Define 6 safe slots: Top (-90), Top-Right (-40), Bottom-Right (40), Bottom (90), Bottom-Left (140), Top-Left (220)
        // This specifically avoids 0 (Right) and 180 (Left) to prevent screen edge cutting
        val targetSlots = listOf(-90f, -40f, 40f, 90f, 140f, 220f)
        
        actualMids.mapIndexed { index, actualMid ->
            actualMid to targetSlots[index % targetSlots.size]
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val centerX = widthPx / 2f
        val centerY = widthPx / 2f
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringRadius = (size.minDimension / 2f) * 0.55f
            val strokeWidth = 20.dp.toPx()
            val glowWidth = 28.dp.toPx()

            // Background track
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = ringRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = strokeWidth)
            )

            var startAngle = -90f
            topApps.forEachIndexed { index, app ->
                val sweepAngle = (app.usageTimeMs.toFloat() / totalMs) * 360f
                val color = colors[index % colors.size]
                val (actualMidAngle, targetAngle) = appAngles[index]

                // Segment Glow
                drawArc(
                    color = color.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * 0.98f,
                    useCenter = false,
                    topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
                    size = Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = glowWidth, cap = StrokeCap.Round)
                )
                
                // Main Segment
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * 0.98f,
                    useCenter = false,
                    topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
                    size = Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // ── pointer lines connecting actual segment to unified chip ──
                val actualRad = Math.toRadians(actualMidAngle.toDouble())
                val targetRad = Math.toRadians(targetAngle.toDouble())
                
                // Start point on ring
                val p1Distance = ringRadius + strokeWidth/2 + 2.dp.toPx()
                val p1x = centerX + p1Distance * cos(actualRad).toFloat()
                val p1y = centerY + p1Distance * sin(actualRad).toFloat()
                
                // End point near the chip
                val p3Distance = (size.minDimension / 2f) * 0.81f
                val p3x = centerX + p3Distance * cos(targetRad).toFloat()
                val p3y = centerY + p3Distance * sin(targetRad).toFloat()

                val path = Path().apply {
                    moveTo(p1x, p1y)
                    // Quadratic curve for a smoother connection
                    val controlDist = (p1Distance + p3Distance) / 2f
                    val controlX = centerX + controlDist * cos(actualRad).toFloat()
                    val controlY = centerY + controlDist * sin(actualRad).toFloat()
                    quadraticTo(controlX, controlY, p3x, p3y)
                }
                
                drawPath(path = path, color = color.copy(alpha = 0.35f), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(color = color.copy(alpha = 0.8f), radius = 3.dp.toPx(), center = Offset(p1x, p1y))
                drawCircle(color = color.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = Offset(p3x, p3y))

                startAngle += sweepAngle
            }
        }

        // ── Floating App Chips (Positioned at Unified Angles) ────────────────
        topApps.forEachIndexed { index, app ->
            val (_, targetAngle) = appAngles[index]
            val angleRad = Math.toRadians(targetAngle.toDouble())
            
            // Positioning chip at the targetAngle (Evenly distributed)
            val distance = (maxWidth.value / 2f) * 0.88f
            val xOffset = (distance * cos(angleRad)).dp
            val yOffset = (distance * sin(angleRad)).dp

            val percent = if (totalMs > 0) (app.usageTimeMs * 100 / totalMs.toLong()) else 0

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(xOffset, yOffset)
            ) {
                AppChip(app, percent.toInt())
            }
        }
    }
}

@Composable
private fun AppChip(app: AppUsageInfo, percent: Int) {
    Surface(
        color = Color.Black.copy(alpha = 0.1f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.widthIn(min = 100.dp)
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(26.dp),
                contentAlignment = Alignment.Center
            ) {
                app.icon?.let {
                    Image(
                        painter = rememberAsyncImagePainter(it),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Column {
                Text(app.appName, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    text = "${formatDurationShort(app.usageTimeMs)} · $percent%",
                    color = TextSecond,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun InsightCard(comparison: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GlassBg),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GlassBg),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
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
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatDurationShort(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
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
