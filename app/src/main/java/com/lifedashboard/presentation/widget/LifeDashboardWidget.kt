package com.lifedashboard.presentation.widget

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.lifedashboard.data.local.dao.CalendarEventDao
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * 1. LifeDashboard1x1Widget — Tile / Badge Biométrico Ultracompacto (1x1)
 *
 * Ideal para pantalla de bloqueo o un rincón mínimo en la pantalla de inicio.
 * Muestra el total de pasos acumulados con indicador de pulso luminoso.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class LifeDashboard1x1Widget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Indigo800))
                    .padding(8.dp),
                verticalAlignment   = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                // Indicador de pulso
                Row(
                    modifier            = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Text(
                        text  = "✦",
                        style = TextStyle(color = ColorProvider(Cyan400), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text  = data.steps.formatK(),
                    style = TextStyle(color = ColorProvider(White), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text  = "PASOS",
                    style = TextStyle(color = ColorProvider(Cyan400), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.height(4.dp))
                LinearProgressIndicator(
                    progress        = (data.steps / 10_000f).coerceIn(0f, 1f),
                    modifier        = GlanceModifier.fillMaxWidth().height(3.dp),
                    color           = ColorProvider(Cyan400),
                    backgroundColor = ColorProvider(RingTrack)
                )
            }
        }
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * 2. LifeDashboard1x2Widget — Cápsula Biometría Horizontal / Pill (2x1 o 1x2)
 *
 * Barra estilizada de pulso vital: Pasos, Sueño y Entreno en una sola fila.
 * Formato ideal para los estantes de la pantalla de bloqueo y cabecera de inicio.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class LifeDashboard1x2Widget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Indigo800))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment   = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                // Pasos
                Column(
                    modifier            = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment   = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text  = data.steps.formatK(),
                        style = TextStyle(color = ColorProvider(Cyan400), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text  = "pasos",
                        style = TextStyle(color = ColorProvider(Gray400), fontSize = 9.sp)
                    )
                }

                // Separador sutil
                Text(
                    text  = "·",
                    style = TextStyle(color = ColorProvider(Indigo600), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )

                // Sueño
                Column(
                    modifier            = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment   = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text  = "${data.sleepHours}h",
                        style = TextStyle(color = ColorProvider(Teal400), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text  = "sueño",
                        style = TextStyle(color = ColorProvider(Gray400), fontSize = 9.sp)
                    )
                }

                // Separador sutil
                Text(
                    text  = "·",
                    style = TextStyle(color = ColorProvider(Indigo600), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )

                // Entreno
                Column(
                    modifier            = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment   = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text  = if (data.workoutDone) "✓ Hecho" else "○ Hoy",
                        style = TextStyle(
                            color      = ColorProvider(if (data.workoutDone) Violet300 else Gray400),
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text  = "entreno",
                        style = TextStyle(color = ColorProvider(Gray400), fontSize = 9.sp)
                    )
                }
            }
        }
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * 3. LifeDashboard2x2Widget — Cuadrante Biométrico Completo (2x2)
 *
 * Muestra las tres métricas clave con barras de progreso lineales y cabecera de fecha.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class LifeDashboard2x2Widget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Indigo800))
                    .padding(12.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Header
                Row(
                    modifier            = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.Start,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "Life Pulse",
                        style = TextStyle(color = ColorProvider(White), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text  = data.dateString,
                        style = TextStyle(color = ColorProvider(Gray400), fontSize = 10.sp)
                    )
                }

                Spacer(GlanceModifier.height(8.dp))

                // Pasos
                WidgetProgressRow(
                    label    = "Pasos",
                    value    = "${data.steps.formatK()} / 10K",
                    progress = (data.steps / 10_000f).coerceIn(0f, 1f),
                    color    = ColorProvider(Cyan400)
                )

                Spacer(GlanceModifier.height(6.dp))

                // Sueño
                WidgetProgressRow(
                    label    = "Sueño",
                    value    = "${data.sleepHours}h / 8h",
                    progress = (data.sleepHours / 8f).coerceIn(0f, 1f),
                    color    = ColorProvider(Teal400)
                )

                Spacer(GlanceModifier.height(6.dp))

                // Entreno
                WidgetProgressRow(
                    label    = "Entreno",
                    value    = if (data.workoutDone) "Completado ✓" else "Pendiente",
                    progress = if (data.workoutDone) 1f else 0f,
                    color    = ColorProvider(Violet400)
                )
            }
        }
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * 4. LifeDashboardWidget — Widget Amplio Completo (4x2 / Responsivo)
 *
 * Incluye agenda del día, barras biométricas completas y fecha detallada.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class LifeDashboardWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT_SIZE = DpSize(120.dp, 100.dp)
        private val EXPANDED_SIZE = DpSize(220.dp, 100.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, EXPANDED_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)

        provideContent {
            val size = LocalSize.current
            if (size.width < 180.dp) {
                // Vista reducida si el usuario lo encoje
                CompactWidgetContent(data = data)
            } else {
                ExpandedWidgetContent(data = data)
            }
        }
    }
}

@Composable
fun ExpandedWidgetContent(data: WidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Indigo800))
            .padding(14.dp)
    ) {
        Row(
            modifier              = GlanceModifier.fillMaxWidth(),
            horizontalAlignment   = Alignment.Horizontal.Start,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text  = "Life Dashboard",
                style = TextStyle(color = ColorProvider(White), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text  = data.dateString,
                style = TextStyle(color = ColorProvider(Gray400), fontSize = 11.sp)
            )
        }

        Spacer(GlanceModifier.height(10.dp))

        WidgetProgressRow(
            label    = "Pasos",
            value    = "${data.steps.formatK()} / 10K",
            progress = (data.steps / 10_000f).coerceIn(0f, 1f),
            color    = ColorProvider(Cyan400)
        )

        Spacer(GlanceModifier.height(7.dp))

        WidgetProgressRow(
            label    = "Sueño",
            value    = "${data.sleepHours}h / 8h",
            progress = (data.sleepHours / 8f).coerceIn(0f, 1f),
            color    = ColorProvider(Teal400)
        )

        Spacer(GlanceModifier.height(7.dp))

        WidgetProgressRow(
            label    = "Entreno",
            value    = if (data.workoutDone) "Completo ✓" else "Pendiente",
            progress = if (data.workoutDone) 1f else 0f,
            color    = ColorProvider(Violet400)
        )

        if (data.nextEvent != null) {
            Spacer(GlanceModifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = "📅 ${data.nextEvent}",
                    style    = TextStyle(color = ColorProvider(Gray300), fontSize = 10.sp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CompactWidgetContent(data: WidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Indigo800))
            .padding(10.dp),
        verticalAlignment   = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Row(
            modifier            = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Text(text = "Hoy", style = TextStyle(color = ColorProvider(White), fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text(text = data.dateString, style = TextStyle(color = ColorProvider(Gray400), fontSize = 9.sp))
        }

        Spacer(GlanceModifier.height(6.dp))

        Row(
            modifier            = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                Text(text = data.steps.formatK(), style = TextStyle(color = ColorProvider(Cyan400), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Text(text = "pasos", style = TextStyle(color = ColorProvider(Gray400), fontSize = 8.sp))
            }
            Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                Text(text = "${data.sleepHours}h", style = TextStyle(color = ColorProvider(Teal400), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Text(text = "sueño", style = TextStyle(color = ColorProvider(Gray400), fontSize = 8.sp))
            }
        }

        Spacer(GlanceModifier.height(4.dp))

        Text(
            text  = if (data.workoutDone) "✓ Entrenado" else "○ Entreno hoy",
            style = TextStyle(color = ColorProvider(if (data.workoutDone) Violet300 else Gray400), fontSize = 9.sp)
        )
    }
}

@Composable
private fun WidgetProgressRow(
    label: String,
    value: String,
    progress: Float,
    color: ColorProvider
) {
    Column {
        Row(
            modifier            = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            Text(text = label, style = TextStyle(color = ColorProvider(Gray400), fontSize = 10.sp))
            Spacer(GlanceModifier.defaultWeight())
            Text(text = value, style = TextStyle(color = ColorProvider(White), fontSize = 10.sp))
        }
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator(
            progress        = progress,
            modifier        = GlanceModifier.fillMaxWidth().height(4.dp),
            color           = color,
            backgroundColor = ColorProvider(RingTrack)
        )
    }
}

// ─── Carga de datos unificada ────────────────────────────────────────────────

suspend fun loadWidgetData(context: Context): WidgetData =
    withContext(Dispatchers.IO) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )
            val healthDao   = entryPoint.healthDao()
            val workoutDao  = entryPoint.workoutDao()
            val calendarDao = entryPoint.calendarEventDao()

            val today = LocalDate.now()
            val zone  = ZoneId.systemDefault()
            val todayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()

            val health = healthDao.getHealthForDay(todayMillis)
            val sessions = workoutDao.getRecentSessions(1)
            val workoutToday = sessions.firstOrNull()?.let { s ->
                Instant.ofEpochMilli(s.dateMillis).atZone(zone).toLocalDate() == today
            } ?: false

            val nextEvent = calendarDao
                .getUpcomingEventsSnapshot(System.currentTimeMillis(), 1)
                .firstOrNull()?.let { ev ->
                    val dt = Instant.ofEpochMilli(ev.startTimeMillis)
                        .atZone(zone).toLocalDateTime()
                    val timeStr = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                    "${ev.title} ($timeStr)"
                }

            WidgetData(
                steps       = health?.steps ?: 0,
                sleepHours  = (health?.sleepHours ?: 0f).let { (it * 10).toInt() / 10f },
                workoutDone = workoutToday,
                nextEvent   = nextEvent,
                dateString  = today.format(DateTimeFormatter.ofPattern("d MMM"))
            )
        } catch (e: Exception) {
            WidgetData()
        }
    }

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface WidgetEntryPoint {
    fun healthDao(): HealthDao
    fun workoutDao(): WorkoutDao
    fun calendarEventDao(): CalendarEventDao
}

data class WidgetData(
    val steps: Int = 0,
    val sleepHours: Float = 0f,
    val workoutDone: Boolean = false,
    val nextEvent: String? = null,
    val dateString: String = ""
)

fun Int.formatK(): String = if (this >= 1000) "${this / 1000}.${(this % 1000) / 100}K" else toString()
