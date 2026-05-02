package com.example.digitalmonk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.digitalmonk.R

// 1. Define the Font Families from res/font
val inknutAntiqua = FontFamily(
    Font(R.font.inknutantiqua_bold, FontWeight.Bold)
)

val inclusiveSans = FontFamily(
    Font(R.font.inclusivesans_regular, FontWeight.Normal)
)

// 2. Set up Material 3 Typography
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = inclusiveSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = inknutAntiqua,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    )
)