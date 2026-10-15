package com.lifedashboard.presentation.widget

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.*
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
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
 * LifeDashboardWidget — Jetpack Glance
 *
 * Diseño:
 *  ┌─────────────────────────────────┐
 *  │  Buenos días          12/10     │
 *  │  ████░░ 7.543 pasos             │
 *  │  ████░░ 7.2h sueño              │
 *  │  ██░░░░ entreno                 │
 *  │  📅 Próximo: Reunión a las 15h  │
 *  └─────────────────────────────────┘
 *
 * Lee directamente de Room — sin red.
 * Se actualiza cuando HealthConnectSyncWorker termina.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class LifeDashboardWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Leemos datos frescos desde Room
        val data = loadWidgetData(context)

        provideContent {
            WidgetContent(data = data)
        }
    }

    @Composable
    private fun WidgetContent(data: WidgetData) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Indigo800))
                .padding(16.dp)
        ) {
            // ─── Header ──────────────────────────────────────────────────
            Row(
                modifier              = GlanceModifier.fillMaxWidth(),
                horizontalAlignment   = Alignment.Horizontal.Start,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "Life Dashboard",
                    style = TextStyle(
                        color    = ColorProvider(White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text  = data.dateString,
                    style = TextStyle(
                        color    = ColorProvider(Gray400),
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(GlanceModifier.height(12.dp))

            // ─── Pasos ────────────────────────────────────────────────────
            ProgressRow(
                label    = "Pasos",
                value    = "${data.steps.formatK()} / 10K",
                progress = (data.steps / 10_000f).coerceIn(0f, 1f),
                color    = ColorProvider(Cyan400)
            )

            Spacer(GlanceModifier.height(8.dp))

            // ─── Sueño ────────────────────────────────────────────────────
            ProgressRow(
                label    = "Sueño",
                value    = "${data.sleepHours}h / 8h",
                progress = (data.sleepHours / 8f).coerceIn(0f, 1f),
                color    = ColorProvider(Teal400)
            )

            Spacer(GlanceModifier.height(8.dp))

            // ─── Entreno ──────────────────────────────────────────────────
            ProgressRow(
                label    = "Entreno",
                value    = if (data.workoutDone) "Completo ✓" else "Pendiente",
                progress = if (data.workoutDone) 1f else 0f,
                color    = ColorProvider(Violet400)
            )

            Spacer(GlanceModifier.height(12.dp))

            // ─── Próximo evento ───────────────────────────────────────────
            if (data.nextEvent != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "📅 ${data.nextEvent}",
                        style = TextStyle(
                            color    = ColorProvider(Gray300),
                            fontSize = 11.sp
                        ),
                        maxLines = 2
                    )
                }
            }
        }
    }

    @Composable
    private fun ProgressRow(
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
                Text(
                    text  = label,
                    style = TextStyle(color = ColorProvider(Gray400), fontSize = 10.sp)
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text  = value,
                    style = TextStyle(color = ColorProvider(White), fontSize = 10.sp)
                )
            }
            Spacer(GlanceModifier.height(3.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = color,
                backgroundColor = ColorProvider(RingTrack)
            )
        }
    }

    // ─── Carga de datos ──────────────────────────────────────────────────────

    private suspend fun loadWidgetData(context: Context): WidgetData =
        withContext(Dispatchers.IO) {
            try {
                // Accedemos a Hilt entry point para DAOs
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
                val tomorrowMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

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
                        "${ev.title} a las $timeStr"
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
}

// ─── Hilt Entry Point para DAOs en Widget (sin DI directo) ───────────────────

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface WidgetEntryPoint {
    fun healthDao(): HealthDao
    fun workoutDao(): WorkoutDao
    fun calendarEventDao(): CalendarEventDao
}

// ─── Data class del widget ────────────────────────────────────────────────────

private data class WidgetData(
    val steps: Int = 0,
    val sleepHours: Float = 0f,
    val workoutDone: Boolean = false,
    val nextEvent: String? = null,
    val dateString: String = ""
)

private fun Int.formatK(): String = if (this >= 1000) "${this / 1000}.${(this % 1000) / 100}K" else toString()
