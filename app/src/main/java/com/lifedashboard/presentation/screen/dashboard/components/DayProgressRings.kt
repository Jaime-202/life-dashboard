package com.lifedashboard.presentation.screen.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.presentation.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * VitalityCoreVisualizer (Reemplazo creativo de la rueda tradicional)
 *
 * Muestra un núcleo biométrico de vitalidad con arco dinámico de energía,
 * porcentaje de vitalidad compuesto y micro-cápsulas de estado.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Composable
fun DayProgressRings(
    stepsProgress: Float,           // 0f..1f
    caloriesProgress: Float,        // 0f..1f
    sleepProgress: Float,           // 0f..1f
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 9.dp,
    gap: Dp = 0.dp
) {
    // Calculamos el índice de Vitalidad compuesto (0 a 100)
    val compositeScore = (
        (stepsProgress.coerceIn(0f, 1f) * 0.35f +
         sleepProgress.coerceIn(0f, 1f) * 0.35f +
         caloriesProgress.coerceIn(0f, 1f) * 0.30f)
    ).coerceIn(0f, 1f)

    val animatedScore by animateFloatAsState(
        targetValue   = compositeScore,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label         = "vitality_score_anim"
    )

    val statusText = when {
        animatedScore >= 0.85f -> "ÓPTIMO"
        animatedScore >= 0.65f -> "EN RITMO"
        animatedScore >= 0.40f -> "ACTIVO"
        else                   -> "INICIANDO"
    }

    val statusColor = when {
        animatedScore >= 0.85f -> Emerald400
        animatedScore >= 0.65f -> Cyan400
        animatedScore >= 0.40f -> Amber400
        else                   -> Violet300
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val padding = strokePx + 4.dp.toPx()
            val arcSize = Size(this.size.width - padding * 2f, this.size.height - padding * 2f)
            val topLeft = Offset(padding, padding)

            val startAngle = 140f
            val maxSweepAngle = 260f

            // 1. Pista de fondo con brillo suave
            drawArc(
                color      = RingTrack.copy(alpha = 0.6f),
                startAngle = startAngle,
                sweepAngle = maxSweepAngle,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcSize,
                style      = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // 2. Arco de energía dinámico (Gradiente Cyan -> Violeta -> Esmeralda)
            if (animatedScore > 0f) {
                val currentSweep = maxSweepAngle * animatedScore
                val gradient = Brush.sweepGradient(
                    colors = listOf(
                        Cyan400,
                        Violet400,
                        Emerald400,
                        Cyan400
                    ),
                    center = Offset(this.size.width / 2f, this.size.height / 2f)
                )

                drawArc(
                    brush      = gradient,
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // 3. Punto de energía brillante en la cabeza del arco
                val endAngleRad = Math.toRadians((startAngle + currentSweep).toDouble())
                val radius = arcSize.width / 2f
                val centerX = topLeft.x + radius
                val centerY = topLeft.y + radius
                val tipX = centerX + radius * cos(endAngleRad).toFloat()
                val tipY = centerY + radius * sin(endAngleRad).toFloat()

                // Halo exterior
                drawCircle(
                    color  = statusColor.copy(alpha = 0.35f),
                    radius = strokePx * 0.9f,
                    center = Offset(tipX, tipY)
                )
                // Centro blanco brillante
                drawCircle(
                    color  = White,
                    radius = strokePx * 0.45f,
                    center = Offset(tipX, tipY)
                )
            }
        }

        // Centro con porcentaje y estado dinámico
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(
                text       = "${(animatedScore * 100).toInt()}%",
                style      = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                fontWeight = FontWeight.ExtraBold,
                color      = White
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text          = statusText,
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = statusColor,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * CalendarDayRings (Diseño de cápsula/pills biométricas para el calendario)
 *
 * Sustituye los círculos concéntricos por un indicador limpio de 3 barras de pulso.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Composable
fun CalendarDayRings(
    workoutProgress: Float,
    stepsProgress: Float,
    sleepProgress: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier.width(28.dp).height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment     = Alignment.Bottom
    ) {
        // Pasos (Cyan)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(stepsProgress.coerceIn(0.2f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(if (stepsProgress >= 0.7f) Cyan400 else Cyan400.copy(alpha = 0.4f))
        )
        // Sueño (Teal)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(sleepProgress.coerceIn(0.2f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(if (sleepProgress >= 0.7f) Teal400 else Teal400.copy(alpha = 0.4f))
        )
        // Entreno (Violeta)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(workoutProgress.coerceIn(0.2f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(if (workoutProgress >= 0.5f) Violet400 else Violet400.copy(alpha = 0.4f))
        )
    }
}
