package com.lifedashboard.presentation.screen.dashboard.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.presentation.theme.*

@Composable
fun AiInsightCard(
    insight: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(20.dp)

    // Gradiente de fondo principal
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(
            Indigo700,
            Color(0xFF1E1060),
        )
    )

    // Borde decorativo con gradiente violeta-cyan
    val borderGradient = Brush.linearGradient(
        colors = listOf(
            Violet400.copy(alpha = 0.6f),
            Cyan400.copy(alpha = 0.3f),
            Violet400.copy(alpha = 0.2f),
        )
    )

    // Fix #1 — Rotación correcta: se usa rememberInfiniteTransition fuera del IconButton
    // para que la animación funcione correctamente al aplicarse al Icon.
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_angle"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            // Fix #3 — Borde con Brush.linearGradient aplicado vía border() modifier
            .border(
                width  = 1.dp,
                brush  = borderGradient,
                shape  = cardShape
            )
            .background(backgroundGradient)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // ── Encabezado ──────────────────────────────────────────────────────────
            Row(
                verticalAlignment      = Alignment.CenterVertically,
                horizontalArrangement  = Arrangement.SpaceBetween,
                modifier               = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.AutoAwesome,
                        contentDescription = "Icono de insight de IA",
                        tint               = Violet300,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = "Insight del Día",
                        style = MaterialTheme.typography.labelLarge,
                        color = Violet300
                    )
                }

                // Botón de refrescar con rotación animada correctamente aplicada
                IconButton(
                    onClick  = onRefresh,
                    enabled  = !isLoading,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Refresh,
                        contentDescription = "Actualizar insight",
                        tint               = if (isLoading) Violet300 else Gray400,
                        // Fix #1 — rotate() se aplica aquí directamente sobre el Icon
                        modifier           = Modifier
                            .size(16.dp)
                            .rotate(if (isLoading) rotationAngle else 0f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Contenido animado ────────────────────────────────────────────────
            AnimatedContent(
                targetState  = isLoading to insight,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label        = "insight_content"
            ) { (loading, text) ->
                when {
                    loading -> {
                        // Fix #2 — Estado de carga con texto descriptivo encima del shimmer
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier            = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text  = "Analizando tus datos\u2026",
                                style = MaterialTheme.typography.bodySmall,
                                color = Violet300
                            )
                            LoadingShimmer()
                        }
                    }

                    text != null -> {
                        // Insight disponible
                        Text(
                            text       = text,
                            style      = MaterialTheme.typography.bodyMedium,
                            color      = Gray100,
                            fontStyle  = FontStyle.Normal,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                        )
                    }

                    else -> {
                        // Estado inicial interactivo con botón directo
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier            = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text      = "Cruza tus datos de sueño, pasos y entreno para descubrir correlaciones clave.",
                                style     = MaterialTheme.typography.bodySmall,
                                color     = Gray400,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = onRefresh,
                                colors  = ButtonDefaults.buttonColors(
                                    containerColor = Violet400,
                                    contentColor   = Indigo900
                                ),
                                shape   = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector        = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier           = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text       = "Generar Análisis",
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Shimmer de carga ─────────────────────────────────────────────────────────
@Composable
private fun LoadingShimmer() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tres líneas de placeholder con la última más corta (apariencia de texto)
        repeat(3) { idx ->
            val widthFraction = if (idx == 2) 0.6f else 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Gray700.copy(alpha = shimmerAlpha))
            )
        }
    }
}
