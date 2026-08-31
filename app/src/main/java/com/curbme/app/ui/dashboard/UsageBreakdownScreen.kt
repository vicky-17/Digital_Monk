package com.curbme.app.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.curbme.app.data.models.AppUsageInfo
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private val AccentCyan = Color(0xFF06B6D4)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageBreakdownScreen(
    viewModel: UsageViewModel,
    onBack: () -> Unit
) {
    val weeklyData by viewModel.weeklyData.collectAsState()
    val selectedIndex by viewModel.selectedDayIndex.collectAsState()
    val usageStats by viewModel.usageStats.collectAsState()
    val weekOffset by viewModel.weekOffset.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF04040c), // BgDeep
                        Color(0xFF080B1A), // Deep subtle tint
                        Color(0xFF04040c)
                    )
                )
            )
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Usage Statistics", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── Week Selector ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { viewModel.changeWeek(-1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = AccentCyan)
                    }
                    
                    Text(
                        text = getWeekRangeLabel(weekOffset),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    IconButton(
                        onClick = { viewModel.changeWeek(1) },
                        enabled = weekOffset < 0
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight, 
                            null, 
                            tint = if (weekOffset < 0) AccentCyan else TextSecond.copy(alpha = 0.3f)
                        )
                    }
                }

                // ── Weekly Activity Chart ───────────────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "WEEKLY ACTIVITY",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        
                        WeeklyBarChart(
                            data = weeklyData,
                            selectedIndex = selectedIndex,
                            onDaySelected = { viewModel.selectDay(it) }
                        )
                    }
                }

                // ── Selected Day Summary ────────────────────────────────────────
                val totalTime = usageStats.sumOf { it.usageTimeMs }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = formatDuration(totalTime),
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (weekOffset == 0 && selectedIndex == 6) "TOTAL TODAY" else "DAILY TOTAL",
                        color = TextSecond,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // ── App List ────────────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(usageStats) { app ->
                        DetailedAppUsageRow(app)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBarChart(
    data: List<DayUsageData>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        
        val barAreaWidth = width * 0.9f
        val startOffset = (width - barAreaWidth) / 2f
        val gap = barAreaWidth / 7f
        val barWidth = gap * 0.4f
        
        val maxValue = data.maxOfOrNull { it.valueHours } ?: 1f
        val effectiveMax = if (maxValue == 0f) 1f else maxValue

        Canvas(modifier = Modifier.fillMaxSize().clickable { 
            // Interaction logic could go here, but using boxes for touch is easier
        }) {
            data.forEachIndexed { i, day ->
                val x = startOffset + gap * i + gap / 2f
                val ratio = day.valueHours / effectiveMax
                val barHeight = (height * 0.7f * ratio).coerceAtLeast(4.dp.toPx())
                
                // Draw Bar
                drawRoundRect(
                    color = if (i == selectedIndex) AccentCyan else AccentCyan.copy(alpha = 0.3f),
                    topLeft = Offset(x - barWidth / 2f, height * 0.8f - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                
                // Draw Label
                // Using drawIntoCanvas for text if needed, or just overlays
            }
        }
        
        // Touch layer & Labels
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.width(startOffset.dp / 2.7f)) // Approx align
            data.forEachIndexed { i, day ->
                Column(
                    modifier = Modifier
                        .width((gap / 2.7f).dp) // Approx align
                        .fillMaxHeight()
                        .clickable { onDaySelected(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        day.label,
                        color = if (i == selectedIndex) AccentCyan else TextSecond,
                        fontSize = 11.sp,
                        fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailedAppUsageRow(app: AppUsageInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberAsyncImagePainter(app.icon),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.category.uppercase(), color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "• ${app.launchCount} launches",
                        color = TextSecond,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                formatDuration(app.usageTimeMs),
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            )
        }
    }
}

private fun getWeekRangeLabel(offset: Int): String {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    calendar.add(Calendar.WEEK_OF_YEAR, offset)
    val start = calendar.time
    
    calendar.add(Calendar.DAY_OF_YEAR, 6)
    val end = calendar.time
    
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return "${sdf.format(start)} – ${sdf.format(end)}"
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
