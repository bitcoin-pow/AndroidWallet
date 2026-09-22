package com.wallet.btcw.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)
