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
import androidx.compose.ui.graphics.Color
import com.lifedashboard.data.local.dao.CalendarEventDao
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─── Paleta Glassmorphic Translúcida ─────────────────────────────────────────
// Permite que el fondo de pantalla del usuario se perciba sutilmente a través del widget
private val GlassBackground     = Color(0xD90D0722) // ~85% opacidad obsidian profundo
private val GlassCard           = Color(0x401A1040) // ~25% translúcido interno
private val GlassCardElevated   = Color(0x66251654) // ~40% translúcido destacado
private val NeonCyan            = Color(0xFF22D3EE)
private val NeonAmber           = Color(0xFFFBBF24)
private val NeonTeal            = Color(0xFF2DD4BF)
private val NeonViolet          = Color(0xFFB894FF)
private val TrackColor          = Color(0x552A1E5C)

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * 1. LifeDashboard1x1Widget — Badge Biométrico Glassmorphic (1x1)
 *
 * Muestra pasos en grande y micro-cápsulas para calorías y descanso.
 * Fondo translúcido con esquinas curvadas.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class LifeDashboard1x1Widget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(GlassBackground))
                    .padding(8.dp),
                verticalAlignment   = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                // Cabecera con Vitality Score
                Row(
                    modifier            = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "✦ ${data.vitalityScore}%",
                        style = TextStyle(color = ColorProvider(NeonViolet), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(GlanceModifier.height(2.dp))

                // Métrica principal: Pasos
                Text(
                    text  = data.steps.formatK(),
                    style = TextStyle(color = ColorProvider(NeonCyan), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text  = "pasos",
                    style = TextStyle(color = ColorProvider(Gray400), fontSize = 8.sp)
                )

                Spacer(GlanceModifier.height(4.dp))

                // Fila micro con calorías y sueño
                Row(
                    modifier            = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "${data.calories}cal",
                        style = TextStyle(color = ColorProvider(NeonAmber), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text  = "·",
                        style = TextStyle(color = ColorProvider(Gray500), fontSize = 8.sp)
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text  = "${data.sleepHours}h",
                        style = TextStyle(color = ColorProvider(NeonTeal), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(GlanceModifier.height(3.dp))

                // Micro barra de pasos
                LinearProgressIndicator(
                    progress        = (data.steps.toFloat() / data.stepsTarget).coerceIn(0f, 1f),
                    modifier        = GlanceModifier.fillMaxWidth().height(3.dp),
                    color           = ColorProvider(NeonCyan),
                    backgroundColor = ColorProvider(TrackColor)
                )
            }
        }
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * 2. LifeDashboard1x2Widget — Cápsula Translúcida Tri-Métrica (2x1 o 1x2)
 *
 * Muestra Pasos (Cyan), Calorías (Ámbar) y Sueño (Teal) en 3 micro-tarjetas.
 * Estilo cápsula moderna ideal para estantes de pantalla de inicio o bloqueo.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class LifeDashboard1x2Widget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(GlassBackground))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment   = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                // 1. Pasos (Cyan)
                Column(
                    modifier            = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "🏃 ${data.steps.formatK()}",
                        style = TextStyle(color = ColorProvider(NeonCyan), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text  = "pasos",
                        style = TextStyle(color = ColorProvider(Gray400), fontSize = 8.sp)
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    LinearProgressIndicator(
                        progress        = (data.steps.toFloat() / data.stepsTarget).coerceIn(0f, 1f),
                        modifier        = GlanceModifier.fillMaxWidth().height(2.dp),
                        color           = ColorProvider(NeonCyan),
                        backgroundColor = ColorProvider(TrackColor)
                    )
                }

                Spacer(GlanceModifier.width(8.dp))

                // 2. Calorías Activas (Ámbar)
                Column(
                    modifier            = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "🔥 ${data.calories}",
                        style = TextStyle(color = ColorProvider(NeonAmber), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text  = "kcal",
                        style = TextStyle(color = ColorProvider(Gray400), fontSize = 8.sp)
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    LinearProgressIndicator(
                        progress        = (data.calories.toFloat() / data.caloriesTarget).coerceIn(0f, 1f),
                        modifier        = GlanceModifier.fillMaxWidth().height(2.dp),
                        color           = ColorProvider(NeonAmber),
                        backgroundColor = ColorProvider(TrackColor)
                    )
                }

                Spacer(GlanceModifier.width(8.dp))

                // 3. Sueño (Teal)
                Column(
                    modifier            = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "🌙 ${data.sleepHours}h",
                        style = TextStyle(color = ColorProvider(NeonTeal), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text  = "sueño",
                        style = TextStyle(color = ColorProvider(Gray400), fontSize = 8.sp)
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    LinearProgressIndicator(
                        progress        = (data.sleepHours / data.sleepTarget).coerceIn(0f, 1f),
                        modifier        = GlanceModifier.fillMaxWidth().height(2.dp),
                        color           = ColorProvider(NeonTeal),
                        backgroundColor = ColorProvider(TrackColor)
                    )
                }
            }
        }
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * 3. LifeDashboard2x2Widget — Cuadrante Glassmorphic Avanzado (2x2)
 *
 * Muestra Pasos, Calorías y Sueño con barras completas, cabecera e índice vital.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class LifeDashboard2x2Widget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(GlassBackground))
                    .padding(12.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Header estilizado con Vital Score
                Row(
                    modifier            = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.Start,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "⚡ VITAL",
                        style = TextStyle(color = ColorProvider(White), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text  = "${data.vitalityScore}% · ${data.dateString}",
                        style = TextStyle(color = ColorProvider(NeonViolet), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(GlanceModifier.height(8.dp))

                // 1. Pasos
                GlassMetricRow(
                    icon     = "🏃",
                    label    = "Pasos",
                    value    = "${data.steps.formatK()} / ${data.stepsTarget.formatK()}",
                    progress = (data.steps.toFloat() / data.stepsTarget).coerceIn(0f, 1f),
                    color    = ColorProvider(NeonCyan)
                )

                Spacer(GlanceModifier.height(6.dp))

                // 2. Calorías
                GlassMetricRow(
                    icon     = "🔥",
                    label    = "Calorías",
                    value    = "${data.calories} / ${data.caloriesTarget} kcal",
                    progress = (data.calories.toFloat() / data.caloriesTarget).coerceIn(0f, 1f),
                    color    = ColorProvider(NeonAmber)
                )

                Spacer(GlanceModifier.height(6.dp))

                // 3. Sueño
                GlassMetricRow(
                    icon     = "🌙",
                    label    = "Sueño",
                    value    = "${data.sleepHours}h / ${data.sleepTarget.toInt()}h",
                    progress = (data.sleepHours / data.sleepTarget).coerceIn(0f, 1f),
                    color    = ColorProvider(NeonTeal)
                )
            }
        }
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * 4. LifeDashboardWidget — Widget Amplio Completo (4x2 / Responsivo)
 *
 * Incluye Pasos, Calorías, Sueño, Entreno y Próximo Evento de Calendario.
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
            .background(ColorProvider(GlassBackground))
            .padding(14.dp)
    ) {
        // Cabecera
        Row(
            modifier              = GlanceModifier.fillMaxWidth(),
            horizontalAlignment   = Alignment.Horizontal.Start,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text  = "⚡ VITAL SUITE",
                style = TextStyle(color = ColorProvider(White), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text  = "${data.dateString} · ${data.vitalityScore}%",
                style = TextStyle(color = ColorProvider(NeonViolet), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
        }

        Spacer(GlanceModifier.height(10.dp))

        // Fila 1: Pasos
        GlassMetricRow(
            icon     = "🏃",
            label    = "Pasos",
            value    = "${data.steps.formatK()} / ${data.stepsTarget.formatK()}",
            progress = (data.steps.toFloat() / data.stepsTarget).coerceIn(0f, 1f),
            color    = ColorProvider(NeonCyan)
        )

        Spacer(GlanceModifier.height(6.dp))

        // Fila 2: Calorías Activas
        GlassMetricRow(
            icon     = "🔥",
            label    = "Calorías Activas",
            value    = "${data.calories} / ${data.caloriesTarget} kcal",
            progress = (data.calories.toFloat() / data.caloriesTarget).coerceIn(0f, 1f),
            color    = ColorProvider(NeonAmber)
        )

        Spacer(GlanceModifier.height(6.dp))

        // Fila 3: Sueño
        GlassMetricRow(
            icon     = "🌙",
            label    = "Sueño",
            value    = "${data.sleepHours}h / ${data.sleepTarget.toInt()}h",
            progress = (data.sleepHours / data.sleepTarget).coerceIn(0f, 1f),
            color    = ColorProvider(NeonTeal)
        )

        // Próximo evento de agenda
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
            .background(ColorProvider(GlassBackground))
            .padding(10.dp),
        verticalAlignment   = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Row(
            modifier            = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Text(text = "Vital", style = TextStyle(color = ColorProvider(White), fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text(text = "${data.vitalityScore}%", style = TextStyle(color = ColorProvider(NeonViolet), fontSize = 9.sp, fontWeight = FontWeight.Bold))
        }

        Spacer(GlanceModifier.height(6.dp))

        Row(
            modifier            = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                Text(text = "🏃 ${data.steps.formatK()}", style = TextStyle(color = ColorProvider(NeonCyan), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                Text(text = "pasos", style = TextStyle(color = ColorProvider(Gray400), fontSize = 8.sp))
            }
            Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                Text(text = "🔥 ${data.calories}", style = TextStyle(color = ColorProvider(NeonAmber), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                Text(text = "kcal", style = TextStyle(color = ColorProvider(Gray400), fontSize = 8.sp))
            }
            Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                Text(text = "🌙 ${data.sleepHours}h", style = TextStyle(color = ColorProvider(NeonTeal), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                Text(text = "sueño", style = TextStyle(color = ColorProvider(Gray400), fontSize = 8.sp))
            }
        }

        Spacer(GlanceModifier.height(4.dp))

        Text(
            text  = if (data.workoutDone) "✓ Entrenado" else "○ Entreno hoy",
            style = TextStyle(color = ColorProvider(if (data.workoutDone) NeonViolet else Gray400), fontSize = 9.sp)
        )
    }
}

@Composable
private fun GlassMetricRow(
    icon: String,
    label: String,
    value: String,
    progress: Float,
    color: ColorProvider
) {
    Column {
        Row(
            modifier            = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Text(text = "$icon $label", style = TextStyle(color = ColorProvider(Gray300), fontSize = 10.sp))
            Spacer(GlanceModifier.defaultWeight())
            Text(text = value, style = TextStyle(color = ColorProvider(White), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator(
            progress        = progress,
            modifier        = GlanceModifier.fillMaxWidth().height(4.dp),
            color           = color,
            backgroundColor = ColorProvider(TrackColor)
        )
    }
}

// ─── Carga de datos unificada con Pasos, Calorías y Sueño ────────────────────

suspend fun loadWidgetData(context: Context): WidgetData =
    withContext(Dispatchers.IO) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )
            val healthDao       = entryPoint.healthDao()
            val workoutDao      = entryPoint.workoutDao()
            val calendarDao     = entryPoint.calendarEventDao()
            val userPreferences = entryPoint.userPreferences()

            val goals = userPreferences.userGoals.first()

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

            val steps = health?.steps ?: 0
            val calories = health?.activeCalories?.toInt() ?: 0
            val sleep = (health?.sleepHours ?: 0f).let { (it * 10).toInt() / 10f }

            val stepsProg = (steps.toFloat() / goals.stepsTarget).coerceIn(0f, 1f)
            val calProg   = (calories.toFloat() / goals.caloriesTarget).coerceIn(0f, 1f)
            val sleepProg = (sleep / goals.sleepTargetHours).coerceIn(0f, 1f)
            val vitality  = ((stepsProg * 0.35f + sleepProg * 0.35f + calProg * 0.30f) * 100).toInt()

            WidgetData(
                steps          = steps,
                stepsTarget    = goals.stepsTarget,
                calories       = calories,
                caloriesTarget = goals.caloriesTarget.toInt(),
                sleepHours     = sleep,
                sleepTarget    = goals.sleepTargetHours,
                workoutDone    = workoutToday,
                vitalityScore  = vitality,
                nextEvent      = nextEvent,
                dateString     = today.format(DateTimeFormatter.ofPattern("d MMM"))
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
    fun userPreferences(): UserPreferencesRepository
}

data class WidgetData(
    val steps: Int = 0,
    val stepsTarget: Int = 10000,
    val calories: Int = 0,
    val caloriesTarget: Int = 2200,
    val sleepHours: Float = 0f,
    val sleepTarget: Float = 8.0f,
    val workoutDone: Boolean = false,
    val vitalityScore: Int = 0,
    val nextEvent: String? = null,
    val dateString: String = ""
)

fun Int.formatK(): String = if (this >= 1000) "${this / 1000}.${(this % 1000) / 100}K" else toString()
