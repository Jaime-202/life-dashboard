package com.lifedashboard.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = Violet400,
    onPrimary        = Indigo900,
    primaryContainer = Indigo700,
    onPrimaryContainer = Violet200,

    secondary        = Cyan400,
    onSecondary      = Indigo900,
    secondaryContainer = Color(0xFF0E3A45),
    onSecondaryContainer = Cyan400,

    tertiary         = Teal400,
    onTertiary       = Indigo900,
    tertiaryContainer = Color(0xFF0E3430),
    onTertiaryContainer = Teal400,

    background       = Indigo900,
    onBackground     = Gray100,

    surface          = Indigo800,
    onSurface        = Gray100,
    surfaceVariant   = Indigo700,
    onSurfaceVariant = Gray300,

    outline          = Color(0xFF3D2280),
    outlineVariant   = Color(0xFF2F1A68),

    error            = Rose400,
    onError          = Indigo900,

    inverseSurface   = Gray100,
    inverseOnSurface = Indigo900,
    inversePrimary   = Indigo700,
)

// No implementamos Light theme — la app es siempre dark
private val LightColorScheme = DarkColorScheme

@Composable
fun LifeDashboardTheme(
    darkTheme: Boolean = true,  // Siempre dark por diseño
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = AppTypography,
        content     = content
    )
}
