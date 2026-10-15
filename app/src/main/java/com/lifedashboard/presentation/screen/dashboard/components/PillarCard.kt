package com.lifedashboard.presentation.screen.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.presentation.theme.*

/**
 * Tarjeta de un pilar (Entrenamiento / Pasos / Sueño / Calorías).
 * Muestra valor principal, progreso, subtítulo y es interactiva para navegar al detalle.
 */
@Composable
fun PillarCard(
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    progress: Float,
    ringColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = ringColor,
    onClick: (() -> Unit)? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue  = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "pillar_progress"
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Indigo800, Indigo700)
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .then(clickModifier)
            .padding(16.dp)
    ) {
        Column {
            // ─── Header ──────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint     = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = Gray300,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (onClick != null) {
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Ver más",
                            tint = Gray500,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                // Mini progress indicator circular
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                    CircularProgressIndicator(
                        progress     = { animatedProgress },
                        modifier     = Modifier.fillMaxSize(),
                        color        = accentColor,
                        trackColor   = RingTrack,
                        strokeWidth  = 3.dp
                    )
                    if (animatedProgress >= 1f) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completado",
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Valor principal ──────────────────────────────────────────
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text       = value,
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color      = White
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text     = unit,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Gray400,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            // ─── Subtítulo ────────────────────────────────────────────────
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Gray500
            )

            Spacer(Modifier.height(10.dp))

            // ─── Barra de progreso lineal ─────────────────────────────────
            LinearProgressIndicator(
                progress    = { animatedProgress },
                modifier    = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color       = accentColor,
                trackColor  = RingTrack,
            )
        }
    }
}
