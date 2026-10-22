package com.lifedashboard.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IndigoColorScheme = darkColorScheme(
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

private val AmoledColorScheme = darkColorScheme(
    primary          = Violet400,
    onPrimary        = Color.Black,
    primaryContainer = Color(0xFF1E1438),
    onPrimaryContainer = Violet200,

    secondary        = Cyan400,
    onSecondary      = Color.Black,
    secondaryContainer = Color(0xFF092830),
    onSecondaryContainer = Cyan400,

    tertiary         = Teal400,
    onTertiary       = Color.Black,
    tertiaryContainer = Color(0xFF092420),
    onTertiaryContainer = Teal400,

    background       = Color(0xFF000000),
    onBackground     = Gray100,

    surface          = Color(0xFF0E0E13),
    onSurface        = Gray100,
    surfaceVariant   = Color(0xFF181822),
    onSurfaceVariant = Gray300,

    outline          = Color(0xFF2E2E3E),
    outlineVariant   = Color(0xFF1E1E28),

    error            = Rose400,
    onError          = Color.Black,

    inverseSurface   = Gray100,
    inverseOnSurface = Color.Black,
    inversePrimary   = Color(0xFF181822),
)

private val MidnightColorScheme = darkColorScheme(
    primary          = Violet400,
    onPrimary        = Color(0xFF0B0F19),
    primaryContainer = Color(0xFF1E243D),
    onPrimaryContainer = Violet200,

    secondary        = Cyan400,
    onSecondary      = Color(0xFF0B0F19),
    secondaryContainer = Color(0xFF0F2636),
    onSecondaryContainer = Cyan400,

    tertiary         = Teal400,
    onTertiary       = Color(0xFF0B0F19),
    tertiaryContainer = Color(0xFF0E2B27),
    onTertiaryContainer = Teal400,

    background       = Color(0xFF0B0F19),
    onBackground     = Gray100,

    surface          = Color(0xFF141C2E),
    onSurface        = Gray100,
    surfaceVariant   = Color(0xFF1E293B),
    onSurfaceVariant = Gray300,

    outline          = Color(0xFF334155),
    outlineVariant   = Color(0xFF1E293B),

    error            = Rose400,
    onError          = Color(0xFF0B0F19),

    inverseSurface   = Gray100,
    inverseOnSurface = Color(0xFF0B0F19),
    inversePrimary   = Color(0xFF1E293B),
)

@Composable
fun LifeDashboardTheme(
    themeMode: String = "OSCURO",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        "AMOLED", "CLARO" -> AmoledColorScheme
        "MIDNIGHT", "SISTEMA" -> MidnightColorScheme
        else -> IndigoColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
