package com.wallet.btcw.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C4B00), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4C5), onPrimaryContainer = Color(0xFF482100),
    secondary = Color(0xFF276859), secondaryContainer = Color(0xFFD3EDE2),
    background = Color(0xFFF5F6F8), onBackground = Color(0xFF172333),
    surface = Color.White, onSurface = Color(0xFF172333),
    surfaceVariant = Color(0xFFEBEEF2), onSurfaceVariant = Color(0xFF526071),
    outline = Color(0xFF788391), outlineVariant = Color(0xFFDDE2E8),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB96C), onPrimary = Color(0xFF482100),
    primaryContainer = Color(0xFF643600), onPrimaryContainer = Color(0xFFFFE4C5),
    secondary = Color(0xFF96D5BC), secondaryContainer = Color(0xFF174F40),
    background = Color(0xFF0D141F), onBackground = Color(0xFFE8EDF4),
    surface = Color(0xFF172231), onSurface = Color(0xFFE8EDF4),
    surfaceVariant = Color(0xFF223144), onSurfaceVariant = Color(0xFFB5C1D1),
    outline = Color(0xFF8592A4), outlineVariant = Color(0xFF344154),
)

@Composable
fun BTCWTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
