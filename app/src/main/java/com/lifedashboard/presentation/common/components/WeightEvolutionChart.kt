package com.lifedashboard.presentation.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.data.local.entity.WeightEntryEntity
import com.lifedashboard.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gráfica visual interactiva de evolución de peso con curva bezier suave,
 * relleno degradado brillante, puntos de control y etiquetas de fecha y peso.
 */
@Composable
fun WeightEvolutionChart(
    weightHistory: List<WeightEntryEntity>,
    modifier: Modifier = Modifier,
    lineColor: Color = Violet400,
    accentColor: Color = Cyan400,
    showStats: Boolean = true
) {
    // Ordenar cronológicamente (más antiguo al más reciente)
    val sortedEntries = remember(weightHistory) {
        weightHistory.sortedBy { it.dateMillis }
    }

    if (sortedEntries.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Indigo900.copy(alpha = 0.5f))
                .border(1.dp, Indigo700.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Registra tu primer pesaje para visualizar tu gráfica de evolución.",
                style = MaterialTheme.typography.bodySmall,
                color = Gray500
            )
        }
        return
    }

    val weights = remember(sortedEntries) { sortedEntries.map { it.weightKg } }
    val minWeight = remember(weights) { (weights.minOrNull() ?: 70f) - 0.5f }
    val maxWeight = remember(weights) { (weights.maxOrNull() ?: 75f) + 0.5f }
    val weightRange = remember(minWeight, maxWeight) { (maxWeight - minWeight).coerceAtLeast(1.0f) }

    // Entrada seleccionada para inspección
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selectedEntry = selectedIndex?.let { sortedEntries.getOrNull(it) } ?: sortedEntries.lastOrNull()

    // Animación de entrada de la línea
    val progress = remember { Animatable(0f) }
    LaunchedEffect(sortedEntries) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 800))
    }

    val dateFormat = remember { SimpleDateFormat("d MMM", Locale("es")) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Indigo900.copy(alpha = 0.7f))
            .border(1.dp, Indigo700, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Cabecera de la gráfica con punto activo inspeccionado
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = if (selectedIndex != null) "Pesaje inspeccionado" else "Evolución histórica",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray400
                )
                if (selectedEntry != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.1f", selectedEntry.weightKg),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                        Text(
                            text = " kg",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Gray400,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "· ${dateFormat.format(Date(selectedEntry.dateMillis))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Violet300,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }

            // Indicadores Min y Max
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "MÁX: ${String.format(Locale.US, "%.1f", weights.maxOrNull() ?: 0f)} kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = Rose400,
                    fontSize = 10.sp
                )
                Text(
                    text = "MÍN: ${String.format(Locale.US, "%.1f", weights.minOrNull() ?: 0f)} kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = Emerald400,
                    fontSize = 10.sp
                )
            }
        }

        // Lienzo Canvas con la gráfica
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(sortedEntries) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val n = sortedEntries.size
                            if (n > 1) {
                                val stepX = width / (n - 1)
                                val tappedIdx = ((offset.x + stepX / 2) / stepX).toInt().coerceIn(0, n - 1)
                                selectedIndex = tappedIdx
                            } else {
                                selectedIndex = 0
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val paddingVertical = 20f
                val availableHeight = h - paddingVertical * 2

                if (sortedEntries.size == 1) {
                    val cy = h / 2f
                    drawLine(
                        color = Gray700,
                        start = Offset(0f, cy),
                        end = Offset(w, cy),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                    drawCircle(color = lineColor, radius = 6.dp.toPx(), center = Offset(w / 2f, cy))
                    drawCircle(color = White, radius = 3.dp.toPx(), center = Offset(w / 2f, cy))
                    return@Canvas
                }

                // Líneas guía horizontales punteadas
                val lineCount = 3
                for (i in 0 until lineCount) {
                    val gy = paddingVertical + (availableHeight / (lineCount - 1)) * i
                    drawLine(
                        color = Color(0x25FFFFFF),
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                }

                // Mapear puntos a coordenadas
                val n = sortedEntries.size
                val stepX = w / (n - 1)
                val points = sortedEntries.mapIndexed { idx, entry ->
                    val x = idx * stepX
                    val normY = (entry.weightKg - minWeight) / weightRange
                    val y = paddingVertical + availableHeight * (1f - normY)
                    Offset(x, y)
                }

                // Construir Path suave tipo Spline Bezier
                val path = Path()
                val fillPath = Path()

                path.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(points.first().x, h)
                fillPath.lineTo(points.first().x, points.first().y)

                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }

                fillPath.lineTo(points.last().x, h)
                fillPath.close()

                // Dibujar relleno con degradado brillante
                val gradientBrush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.35f * progress.value),
                        accentColor.copy(alpha = 0.12f * progress.value),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h
                )
                drawPath(fillPath, brush = gradientBrush)

                // Dibujar línea principal de la curva
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Dibujar puntos / nodos
                points.forEachIndexed { idx, pt ->
                    val isSelected = (idx == selectedIndex) || (selectedIndex == null && idx == points.size - 1)
                    if (isSelected) {
                        // Halo exterior brillante
                        drawCircle(
                            color = lineColor.copy(alpha = 0.3f),
                            radius = 9.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = White,
                            radius = 2.5.dp.toPx(),
                            center = pt
                        )
                    } else {
                        drawCircle(
                            color = lineColor.copy(alpha = 0.7f),
                            radius = 3.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }

        // Fechas en el eje horizontal
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            val firstDate = sortedEntries.firstOrNull()?.dateMillis
            val lastDate = sortedEntries.lastOrNull()?.dateMillis
            if (firstDate != null && lastDate != null) {
                Text(
                    text = dateFormat.format(Date(firstDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray500,
                    fontSize = 10.sp
                )
                if (sortedEntries.size > 2) {
                    val mid = sortedEntries[sortedEntries.size / 2]
                    Text(
                        text = dateFormat.format(Date(mid.dateMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray500,
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = dateFormat.format(Date(lastDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Violet300,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        // Estadísticas agregadas
        if (showStats && sortedEntries.size >= 2) {
            val firstWeight = sortedEntries.first().weightKg
            val lastWeight = sortedEntries.last().weightKg
            val delta = lastWeight - firstWeight

            HorizontalDivider(color = Indigo700, thickness = 1.dp)

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Inicial", style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 10.sp)
                    Text("${String.format(Locale.US, "%.1f", firstWeight)} kg", style = MaterialTheme.typography.labelSmall, color = White, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Variación Total", style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 10.sp)
                    Text(
                        text = "${if (delta > 0) "+" else ""}${String.format(Locale.US, "%.1f", delta)} kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (delta <= 0) Emerald400 else Rose400,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Actual", style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 10.sp)
                    Text("${String.format(Locale.US, "%.1f", lastWeight)} kg", style = MaterialTheme.typography.labelSmall, color = Cyan400, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
