package com.lifedashboard.core.worker

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lifedashboard.core.util.DateUtils.toStartOfDayMillis
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.HealthDataEntity
import com.lifedashboard.data.local.entity.WorkoutSessionEntity
import com.lifedashboard.presentation.widget.LifeDashboardWidgetReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val TAG = "HealthConnectWorker"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * HealthConnectSyncWorker
 *
 * Lee pasos, distancia, calorías, sueño y sesiones de ejercicio de Health Connect
 * y los persiste en Room. Se ejecuta periódicamente o al pulsar Sincronizar.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthDao: HealthDao,
    private val workoutDao: WorkoutDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val status = try {
                HealthConnectClient.getSdkStatus(context)
            } catch (e: Throwable) {
                HealthConnectClient.SDK_UNAVAILABLE
            }

            if (status != HealthConnectClient.SDK_AVAILABLE) {
                Log.w(TAG, "Health Connect no disponible en este dispositivo (status=$status)")
                return@withContext Result.failure()
            }

            val client = HealthConnectClient.getOrCreate(context)
            workoutDao.deduplicateSessions()
            syncLast7Days(client)
            workoutDao.deduplicateSessions()
            updateWidget()
            Result.success()
        } catch (e: SecurityException) {
            Log.w(TAG, "Permiso Health Connect revocado", e)
            Result.failure()
        } catch (e: Throwable) {
            Log.e(TAG, "Error sincronizando Health Connect", e)
            Result.failure()
        }
    }

    private suspend fun syncLast7Days(client: HealthConnectClient) {
        val today = LocalDate.now()
        val results = mutableListOf<HealthDataEntity>()

        for (daysAgo in 0..6) {
            val date = today.minusDays(daysAgo.toLong())
            val entity = buildEntityForDay(client, date)
            results.add(entity)
        }

        healthDao.upsertAll(results)
        Log.d(TAG, "Health Connect sync: ${results.size} días actualizados")
    }

    private suspend fun buildEntityForDay(
        client: HealthConnectClient,
        date: LocalDate
    ): HealthDataEntity {
        val zone = ZoneId.systemDefault()
        val startInstant = date.atStartOfDay(zone).toInstant()
        val endInstant = date.plusDays(1).atStartOfDay(zone).toInstant()
        val timeRange = TimeRangeFilter.between(startInstant, endInstant)

        // Pasos
        val stepsResp = client.readRecords(
            ReadRecordsRequest(StepsRecord::class, timeRange)
        )
        val steps = stepsResp.records.sumOf { it.count }.toInt()

        // Distancia
        val distResp = client.readRecords(
            ReadRecordsRequest(DistanceRecord::class, timeRange)
        )
        val distanceMeters = distResp.records
            .sumOf { it.distance.inMeters }.toFloat()

        // Calorías activas
        val calResp = client.readRecords(
            ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRange)
        )
        val calories = calResp.records
            .sumOf { it.energy.inKilocalories }.toFloat()

        // Sueño
        val sleepStart = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val sleepEnd = date.atTime(12, 0).atZone(zone).toInstant()
        val sleepRange = TimeRangeFilter.between(sleepStart, sleepEnd)
        val sleepResp = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, sleepRange)
        )

        var totalSleepMinutes = 0L
        var deepMinutes = 0L
        var remMinutes = 0L

        sleepResp.records.forEach { session ->
            val sessionMinutes = Duration.between(session.startTime, session.endTime).toMinutes()
            totalSleepMinutes += sessionMinutes

            session.stages.forEach { stage ->
                val stageMin = Duration.between(stage.startTime, stage.endTime).toMinutes()
                when (stage.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> deepMinutes += stageMin
                    SleepSessionRecord.STAGE_TYPE_REM  -> remMinutes  += stageMin
                }
            }
        }

        // Sesiones de ejercicio (Health Connect / Entrenamientos registrados)
        try {
            val exResp = client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, timeRange)
            )
            workoutDao.deleteHealthConnectSessionsForDay(date.toStartOfDayMillis())
            exResp.records.forEach { exSession ->
                val durationMin = Duration.between(exSession.startTime, exSession.endTime).toMinutes().toInt()
                val typeStr = when (exSession.exerciseType) {
                    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "CARDIO"
                    else -> "STRENGTH"
                }
                workoutDao.upsertSession(
                    WorkoutSessionEntity(
                        dateMillis      = date.toStartOfDayMillis(),
                        type            = typeStr,
                        durationMinutes = if (durationMin > 0) durationMin else 45,
                        notes           = exSession.title ?: exSession.notes,
                        sourceFile      = "health_connect"
                    )
                )
            }
        } catch (e: Exception) {
            // Ignorar si no hay registro o permiso
        }

        return HealthDataEntity(
            dateMillis      = date.toStartOfDayMillis(),
            steps           = steps,
            distanceMeters  = distanceMeters,
            activeCalories  = calories,
            sleepHours      = totalSleepMinutes / 60f,
            sleepDeepHours  = deepMinutes / 60f,
            sleepRemHours   = remMinutes / 60f
        )
    }

    private suspend fun updateWidget() {
        WidgetUpdateWorker.updateAllWidgets(context)
    }

    companion object {
        const val WORK_NAME = "health_connect_sync"

        fun buildRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()
    }
}

private fun LocalDate.toStartOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
