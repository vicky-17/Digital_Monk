package com.digitalmonk.app.data.models

import android.graphics.drawable.Drawable

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val usageTimeMs: Long,
    val launchCount: Int,
    val category: String = "App"
)
