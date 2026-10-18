package com.example.taptix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fixed dark color scheme — no dynamic wallpaper colors (prevents see-through background)
private val TaptixDarkColorScheme = darkColorScheme(
    primary          = Color(0xFF00E5FF),
    onPrimary        = Color(0xFF000000),
    secondary        = Color(0xFF1565C0),
    onSecondary      = Color(0xFFFFFFFF),
    background       = Color(0xFF0D0F14),
    onBackground     = Color(0xFFFFFFFF),
    surface          = Color(0xFF151821),
    onSurface        = Color(0xFFFFFFFF),
    surfaceVariant   = Color(0xFF1E2230),
    onSurfaceVariant = Color(0xFF8892A4)
)

@Composable
fun TaptixTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TaptixDarkColorScheme,
        typography  = Typography,
        content     = content
    )
}
