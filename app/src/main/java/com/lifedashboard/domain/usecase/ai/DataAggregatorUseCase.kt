package com.lifedashboard.domain.usecase.ai

import com.lifedashboard.data.local.dao.CalendarEventDao
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.core.util.DateUtils.daysAgoMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * DataAggregatorUseCase
 *
 * Recoge datos de los últimos 7 días desde Room y los serializa en un
 * JSON ligero para enviarlo a Gemini. Evita el envío de raw data
 * voluminosa — solo metadatos y métricas clave.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Singleton
class DataAggregatorUseCase @Inject constructor(
    private val healthDao: HealthDao,
    private val workoutDao: WorkoutDao,
    private val calendarDao: CalendarEventDao
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val json = Json { prettyPrint = false; encodeDefaults = false }

    suspend fun buildContextJson(): String {
        val sevenDaysAgoMillis = daysAgoMillis(7)
        val nowMillis = System.currentTimeMillis()

        // ─── Health ───────────────────────────────────────────────────────
        val healthLast7 = healthDao.getLastNDays(7)
        val healthSummary = healthLast7.map { h ->
            val date = Instant.ofEpochMilli(h.dateMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            DayHealthSummary(
                date         = date.format(formatter),
                steps        = h.steps,
                sleepHours   = (h.sleepHours * 10).toInt() / 10f,  // 1 decimal
                calories     = h.activeCalories.toInt(),
                stepsGoalMet = h.steps >= 7_000,
                sleepGoalMet = h.sleepHours >= 7.5f
            )
        }

        // ─── Workouts ─────────────────────────────────────────────────────
        val volumeByExercise = workoutDao.getVolumeByExercise(sevenDaysAgoMillis)
        val recentSessions = workoutDao.getRecentSessions(7)

        val workoutSummary = WorkoutSummary(
            sessionsCount = recentSessions.count { it.type == "STRENGTH" },
            cardioCount   = recentSessions.count { it.type == "CARDIO" },
            topExercises  = volumeByExercise.take(5).map {
                ExerciseVolume(it.exerciseName, it.totalVolume.toInt())
            }
        )

        // ─── Calendar — próximos 3 eventos ────────────────────────────────
        val upcomingEvents = calendarDao.getUpcomingEventsSnapshot(nowMillis, 3)
        val calendarSummary = upcomingEvents.map {
            val dt = Instant.ofEpochMilli(it.startTimeMillis)
                .atZone(ZoneId.systemDefault()).toLocalDateTime()
            UpcomingEvent(
                title = it.title,
                date  = dt.format(formatter),
                time  = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
            )
        }

        // ─── Contexto diario actual ───────────────────────────────────────
        val today = healthLast7.firstOrNull()
        val todayCtx = TodayContext(
            date         = java.time.LocalDate.now().format(formatter),
            dayOfWeek    = java.time.LocalDate.now().dayOfWeek.name,
            steps        = today?.steps ?: 0,
            sleepLastNight = today?.sleepHours ?: 0f,
            workoutDone  = recentSessions.firstOrNull()?.let { s ->
                Instant.ofEpochMilli(s.dateMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()
            } ?: false
        )

        val payload = GeminiContextPayload(
            today    = todayCtx,
            last7Days = healthSummary,
            workouts  = workoutSummary,
            upcoming  = calendarSummary
        )

        return json.encodeToString(payload)
    }

    // ─── DTOs de serialización ────────────────────────────────────────────────

    @Serializable
    private data class GeminiContextPayload(
        val today: TodayContext,
        val last7Days: List<DayHealthSummary>,
        val workouts: WorkoutSummary,
        val upcoming: List<UpcomingEvent>
    )

    @Serializable
    private data class TodayContext(
        val date: String,
        val dayOfWeek: String,
        val steps: Int,
        val sleepLastNight: Float,
        val workoutDone: Boolean
    )

    @Serializable
    private data class DayHealthSummary(
        val date: String,
        val steps: Int,
        val sleepHours: Float,
        val calories: Int,
        val stepsGoalMet: Boolean,
        val sleepGoalMet: Boolean
    )

    @Serializable
    private data class WorkoutSummary(
        val sessionsCount: Int,
        val cardioCount: Int,
        val topExercises: List<ExerciseVolume>
    )

    @Serializable
    private data class ExerciseVolume(val exercise: String, val volumeKg: Int)

    @Serializable
    private data class UpcomingEvent(val title: String, val date: String, val time: String)
}
