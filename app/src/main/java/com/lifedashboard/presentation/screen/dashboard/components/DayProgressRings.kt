package com.lifedashboard.presentation.screen.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifedashboard.presentation.theme.*

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * DayProgressRings (Rueda de Métricas)
 *
 * Tres anillos concéntricos limpios (sin texto central):
 *  - Anillo Exterior: Pasos (Cyan)
 *  - Anillo Medio:    Calorías Activas (Ámbar)
 *  - Anillo Interior: Sueño (Teal/Violeta)
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Composable
fun DayProgressRings(
    stepsProgress: Float,           // 0f..1f
    caloriesProgress: Float,        // 0f..1f
    sleepProgress: Float,           // 0f..1f
    modifier: Modifier = Modifier,
    size: Dp = 110.dp,
    strokeWidth: Dp = 10.dp,
    gap: Dp = 5.dp
) {
    val animatedSteps    by animateProgressWithDelay(stepsProgress,    delayMs = 0)
    val animatedCalories by animateProgressWithDelay(caloriesProgress, delayMs = 120)
    val animatedSleep    by animateProgressWithDelay(sleepProgress,    delayMs = 240)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val gapPx    = gap.toPx()
            val rings = listOf(
                RingConfig(animatedSteps,    RingSteps, 0f),
                RingConfig(animatedCalories, Amber400,  strokePx + gapPx),
                RingConfig(animatedSleep,    RingSleep, (strokePx + gapPx) * 2f),
            )
            rings.forEach { ring ->
                drawProgressRing(ring, strokePx)
            }
        }
    }
}

private data class RingConfig(
    val progress: Float,
    val color: Color,
    val inset: Float
)

private fun DrawScope.drawProgressRing(ring: RingConfig, strokePx: Float) {
    val inset   = ring.inset + strokePx / 2f
    val topLeft = Offset(inset, inset)
    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)

    // Track
    drawArc(
        color       = RingTrack.copy(alpha = 0.5f),
        startAngle  = -90f,
        sweepAngle  = 360f,
        useCenter   = false,
        topLeft     = topLeft,
        size        = arcSize,
        style       = Stroke(width = strokePx, cap = StrokeCap.Round)
    )

    // Progreso
    if (ring.progress > 0f) {
        drawArc(
            color       = ring.color,
            startAngle  = -90f,
            sweepAngle  = 360f * ring.progress.coerceIn(0f, 1f),
            useCenter   = false,
            topLeft     = topLeft,
            size        = arcSize,
            style       = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun animateProgressWithDelay(target: Float, delayMs: Int): State<Float> {
    return animateFloatAsState(
        targetValue  = target,
        animationSpec = tween(
            durationMillis = 800,
            delayMillis    = delayMs,
            easing         = FastOutSlowInEasing
        ),
        label = "ring_progress"
    )
}

@Composable
fun CalendarDayRings(
    workoutProgress: Float,
    stepsProgress: Float,
    sleepProgress: Float,
    modifier: Modifier = Modifier
) {
    DayProgressRings(
        stepsProgress    = stepsProgress,
        caloriesProgress = workoutProgress,
        sleepProgress    = sleepProgress,
        modifier         = modifier,
        size             = 36.dp,
        strokeWidth      = 3.5.dp,
        gap              = 2.dp
    )
}
