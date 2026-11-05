package com.lifedashboard.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ─── Colores Base Estáticos (para definición de esquemas de tema) ─────────────
val RawIndigo900     = Color(0xFF1A1040)   // Background más profundo original
val RawIndigo800     = Color(0xFF251654)   // Surface cards original
val RawIndigo700     = Color(0xFF2F1A68)   // Surface elevated original
val RawIndigo600     = Color(0xFF3D2280)
val RawGradientStart = Color(0xFF1A1040)
val RawGradientEnd   = Color(0xFF0F172A)

// ─── Colores Dinámicos Sensibles al Tema Actual (OSCURO, AMOLED, MIDNIGHT) ───
// Al usar @Composable @ReadOnlyComposable, toda la aplicación responde al cambio de tema de inmediato.
val Indigo900: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

val Indigo800: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface

val Indigo700: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outlineVariant

val Indigo600: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceVariant

val GradientStart: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

val GradientEnd: Color
    @Composable
    @ReadOnlyComposable
    get() = when (MaterialTheme.colorScheme.background) {
        Color(0xFF000000) -> Color(0xFF08080C)
        Color(0xFF0B0F19) -> Color(0xFF050811)
        else -> RawGradientEnd
    }

val RingTrack: Color
    @Composable
    @ReadOnlyComposable
    get() = when (MaterialTheme.colorScheme.background) {
        Color(0xFF000000) -> Color(0xFF181820)
        Color(0xFF0B0F19) -> Color(0xFF1A2338)
        else -> Color(0xFF2A1E5C)
    }

// ─── Acentos y Anillos ────────────────────────────────────────────────────────
val Violet400    = Color(0xFF9B6DFF)   // Primary accent
val Violet300    = Color(0xFFB894FF)   // Primary variant
val Violet200    = Color(0xFFD4BAFF)   // On-primary containers

val Cyan400      = Color(0xFF22D3EE)   // Steps ring / cardio
val Teal400      = Color(0xFF2DD4BF)   // Sleep ring
val Emerald400   = Color(0xFF34D399)   // Water ring / success
val Amber400     = Color(0xFFFBBF24)   // Warnings / streaks
val Rose400      = Color(0xFFFB7185)   // Alerts / rest day

// ─── Neutros ─────────────────────────────────────────────────────────────────
val White        = Color(0xFFFFFFFF)
val Gray100      = Color(0xFFF3F4F6)
val Gray200      = Color(0xFFE5E7EB)
val Gray300      = Color(0xFFD1D5DB)
val Gray400      = Color(0xFF9CA3AF)
val Gray500      = Color(0xFF6B7280)
val Gray600      = Color(0xFF4B5563)
val Gray700      = Color(0xFF374151)
val Gray800      = Color(0xFF1F2937)
val Gray900      = Color(0xFF111827)

// ─── Anillos de progreso ──────────────────────────────────────────────────────
val RingWorkout  = Violet400
val RingSteps    = Cyan400
val RingSleep    = Teal400
val RingWater    = Emerald400
