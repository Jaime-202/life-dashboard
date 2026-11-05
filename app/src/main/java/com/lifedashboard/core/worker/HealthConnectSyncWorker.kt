package com.lifedashboard.core.worker

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lifedashboard.core.health.HealthConnectAccess
import com.lifedashboard.core.health.HealthConnectMapper
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.HealthDataEntity
import com.lifedashboard.data.local.entity.WorkoutSessionEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
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
        try {
            if (!HealthConnectAccess.isAvailable(context)) {
                Log.w(TAG, "Health Connect no disponible en este dispositivo")
                return@withContext failure(ERROR_UNAVAILABLE)
            }

            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (HealthConnectAccess.READ_PERMISSIONS.none { it in granted }) {
                Log.w(TAG, "Sin permisos de lectura de Health Connect")
                return@withContext failure(ERROR_NO_PERMISSIONS)
            }

            // La sincronización periódica y la manual pueden coincidir: sin este bloqueo,
            // ambas borran y reinsertan las sesiones del día y aparecen duplicados.
            val daysUpdated = syncMutex.withLock { syncRecentDays(client, granted) }
            updateWidget()

            if (daysUpdated == 0) failure(ERROR_READ_FAILED) else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando Health Connect", e)
            failure(ERROR_READ_FAILED)
        }
    }

    private fun failure(error: String): Result = Result.failure(workDataOf(KEY_ERROR to error))

    /** @return número de días para los que se ha podido leer al menos una métrica. */
    private suspend fun syncRecentDays(client: HealthConnectClient, granted: Set<String>): Int {
        val today = LocalDate.now()
        val results = (0L until DAYS_TO_SYNC).mapNotNull { daysAgo ->
            buildEntityForDay(client, granted, today.minusDays(daysAgo))
        }
        if (results.isNotEmpty()) healthDao.upsertAll(results)
        Log.d(TAG, "Health Connect sync: ${results.size} días actualizados")
        return results.size
    }

    /**
     * Lee un día. Cada métrica es independiente: si una no se puede leer (permiso parcial o
     * rechazo en segundo plano) se conserva el valor ya guardado en vez de pisarlo con un cero.
     *
     * @return null si no se ha podido leer ninguna métrica de ese día.
     */
    private suspend fun buildEntityForDay(
        client: HealthConnectClient,
        granted: Set<String>,
        date: LocalDate
    ): HealthDataEntity? {
        val zone = ZoneId.systemDefault()
        val dayMillis = date.toStartOfDayMillis()
        val startInstant = date.atStartOfDay(zone).toInstant()
        val endInstant = date.plusDays(1).atStartOfDay(zone).toInstant()
        val timeRange = TimeRangeFilter.between(startInstant, endInstant)

        val hasSteps = HealthConnectAccess.READ_STEPS in granted
        val hasDistance = HealthConnectAccess.READ_DISTANCE in granted
        val hasCalories = HealthConnectAccess.READ_ACTIVE_CALORIES in granted

        // ─── Agregación con Deduplicación Nativa de Health Connect ─────────
        var steps: Int? = null
        var distanceMeters: Float? = null
        var calories: Float? = null

        val metrics = buildSet {
            if (hasSteps) add(StepsRecord.COUNT_TOTAL)
            if (hasDistance) add(DistanceRecord.DISTANCE_TOTAL)
            if (hasCalories) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        if (metrics.isNotEmpty()) {
            try {
                val aggResp = client.aggregate(AggregateRequest(metrics, timeRange))
                if (hasSteps) steps = aggResp[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
                if (hasDistance) distanceMeters = aggResp[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.toFloat() ?: 0f
                if (hasCalories) calories = aggResp[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toFloat() ?: 0f
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error consultando aggregate en Health Connect: ${e.message}")
            }
        }

        // Si aggregate no arrojó datos, se recurre a los registros en crudo (fuente mayoritaria)
        if (hasSteps && (steps ?: 0) <= 0) {
            readOrNull("StepsRecord") {
                client.readRecords(ReadRecordsRequest(StepsRecord::class, timeRange)).records
                    .groupBy { it.metadata.dataOrigin.packageName }
                    .values.maxOfOrNull { list -> list.sumOf { it.count } }?.toInt() ?: 0
            }?.let { steps = it }
        }
        if (hasDistance && (distanceMeters ?: 0f) <= 0f) {
            readOrNull("DistanceRecord") {
                client.readRecords(ReadRecordsRequest(DistanceRecord::class, timeRange)).records
                    .groupBy { it.metadata.dataOrigin.packageName }
                    .values.maxOfOrNull { list -> list.sumOf { it.distance.inMeters } }?.toFloat() ?: 0f
            }?.let { distanceMeters = it }
        }
        if (hasCalories && (calories ?: 0f) <= 0f) {
            readOrNull("ActiveCaloriesBurnedRecord") {
                client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRange)).records
                    .groupBy { it.metadata.dataOrigin.packageName }
                    .values.maxOfOrNull { list -> list.sumOf { it.energy.inKilocalories } }?.toFloat() ?: 0f
            }?.let { calories = it }
        }

        // ─── Sueño con Deduplicación Inteligente y Selección Óptima ─────────
        // Una noche pertenece al día en que termina: de 18:00 del día anterior a 18:00 del día actual
        val sleepStart = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val sleepEnd = date.atTime(18, 0).atZone(zone).toInstant()
        val sleep: SleepTotals? = if (HealthConnectAccess.READ_SLEEP in granted) {
            readOrNull("SleepSessionRecord") {
                val records = client.readRecords(
                    ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(sleepStart, sleepEnd))
                ).records.filter { it.endTime >= sleepStart && it.endTime < sleepEnd }
                sleepTotalsOf(filterAndDeduplicateSleepSessions(records))
            }
        } else {
            null
        }

        // ─── Sesiones de ejercicio ──────────────────────────────────────────
        val exerciseRead = if (HealthConnectAccess.READ_EXERCISE in granted) {
            readOrNull("ExerciseSessionRecord") {
                val sessions = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, timeRange)).records
                    .filter { it.startTime >= startInstant && it.startTime < endInstant }
                    .map { it.toEntity(dayMillis) }
                workoutDao.replaceHealthConnectSessionsForDay(dayMillis, sessions)
            }
        } else {
            null
        }

        val nothingRead = steps == null && distanceMeters == null && calories == null &&
            sleep == null && exerciseRead == null
        if (nothingRead) return null

        val previous = healthDao.getHealthForDay(dayMillis)
        val finalSteps = steps ?: previous?.steps ?: 0
        val readCalories = calories ?: previous?.activeCalories ?: 0f

        return HealthDataEntity(
            dateMillis      = dayMillis,
            steps           = finalSteps,
            distanceMeters  = distanceMeters ?: previous?.distanceMeters ?: 0f,
            // Sin calorías activas registradas, se estima a partir de los pasos
            activeCalories  = if (readCalories > 0f) readCalories else finalSteps * KCAL_PER_STEP,
            sleepHours      = sleep?.totalHours ?: previous?.sleepHours ?: 0f,
            sleepDeepHours  = sleep?.deepHours ?: previous?.sleepDeepHours ?: 0f,
            sleepRemHours   = sleep?.remHours ?: previous?.sleepRemHours ?: 0f
        )
    }

    private suspend fun <T> readOrNull(label: String, read: suspend () -> T): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Error leyendo $label: ${e.message}")
        null
    }

    private fun ExerciseSessionRecord.toEntity(dayMillis: Long): WorkoutSessionEntity {
        val minutes = Duration.between(startTime, endTime).toMinutes().toInt()
        return WorkoutSessionEntity(
            dateMillis      = dayMillis,
            type            = HealthConnectMapper.workoutTypeFor(exerciseType),
            durationMinutes = minutes.coerceAtLeast(0),
            notes           = title?.takeIf { it.isNotBlank() }
                ?: HealthConnectMapper.exerciseLabelFor(exerciseType)
                ?: notes,
            sourceFile      = "health_connect"
        )
    }

    private class SleepTotals(val totalHours: Float, val deepHours: Float, val remHours: Float)

    /** Tiempo dormido real: se descuentan las fases en las que el reloj marca que estabas despierto. */
    private fun sleepTotalsOf(sessions: List<SleepSessionRecord>): SleepTotals {
        var asleepMinutes = 0L
        var deepMinutes = 0L
        var remMinutes = 0L

        sessions.forEach { session ->
            var awakeMinutes = 0L
            session.stages.forEach { stage ->
                val stageMinutes = Duration.between(stage.startTime, stage.endTime).toMinutes()
                when (stage.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> deepMinutes += stageMinutes
                    SleepSessionRecord.STAGE_TYPE_REM -> remMinutes += stageMinutes
                    SleepSessionRecord.STAGE_TYPE_AWAKE,
                    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awakeMinutes += stageMinutes
                }
            }
            val sessionMinutes = Duration.between(session.startTime, session.endTime).toMinutes()
            asleepMinutes += (sessionMinutes - awakeMinutes).coerceAtLeast(0L)
        }
        return SleepTotals(asleepMinutes / 60f, deepMinutes / 60f, remMinutes / 60f)
    }

    /**
     * Deduplica y resuelve sesiones de sueño solapadas o conflictivas procedentes del reloj
     * o de múltiples fuentes en Health Connect (ej. intervalos 12:00-06:00 y 12:00-09:00).
     *
     * - Las sesiones independientes (ej. siestas previas no solapadas) se preservan.
     * - Las sesiones solapadas se agrupan en un cluster y se selecciona la óptima:
     *   aquella que ocupe más tiempo y sea más coherente (fases Deep/REM, origen fiable).
     */
    private fun filterAndDeduplicateSleepSessions(records: List<SleepSessionRecord>): List<SleepSessionRecord> {
        // Filtrar sesiones residuales o ruido (< 15 minutos)
        val validRecords = records.filter {
            Duration.between(it.startTime, it.endTime).toMinutes() >= 15
        }
        if (validRecords.size <= 1) return validRecords

        val sorted = validRecords.sortedBy { it.startTime }
        val clusters = mutableListOf<MutableList<SleepSessionRecord>>()

        for (session in sorted) {
            val matchingClusters = clusters.filter { cluster ->
                cluster.any { existing -> sessionsOverlapSignificantly(existing, session) }
            }

            if (matchingClusters.isEmpty()) {
                clusters.add(mutableListOf(session))
            } else {
                val primary = matchingClusters.first()
                primary.add(session)
                for (i in 1 until matchingClusters.size) {
                    val other = matchingClusters[i]
                    primary.addAll(other)
                    clusters.remove(other)
                }
            }
        }

        return clusters.map { cluster ->
            pickBestSessionFromCluster(cluster)
        }
    }

    private fun sessionsOverlapSignificantly(a: SleepSessionRecord, b: SleepSessionRecord): Boolean {
        val maxStart = maxOf(a.startTime, b.startTime)
        val minEnd = minOf(a.endTime, b.endTime)
        if (maxStart >= minEnd) return false

        val overlapMinutes = Duration.between(maxStart, minEnd).toMinutes()
        val durA = Duration.between(a.startTime, a.endTime).toMinutes()
        val durB = Duration.between(b.startTime, b.endTime).toMinutes()
        val minDuration = minOf(durA, durB)

        // Se consideran la misma sesión si el solapamiento es >= 20 minutos
        // o si cubre >= 30% de la duración de la sesión más corta
        return overlapMinutes >= 20 || (minDuration > 0 && (overlapMinutes.toFloat() / minDuration) >= 0.30f)
    }

    private fun pickBestSessionFromCluster(cluster: List<SleepSessionRecord>): SleepSessionRecord {
        if (cluster.size == 1) return cluster[0]

        // Selecciona la sesión que mejor se adapte (mayor duración y coherencia biométrica)
        return cluster.maxByOrNull { session ->
            val durationMin = Duration.between(session.startTime, session.endTime).toMinutes()
            val hasDeepOrRem = session.stages.any {
                it.stage == SleepSessionRecord.STAGE_TYPE_DEEP || it.stage == SleepSessionRecord.STAGE_TYPE_REM
            }
            val stageBonus = when {
                hasDeepOrRem -> 45L
                session.stages.isNotEmpty() -> 20L
                else -> 0L
            }
            val pkg = session.metadata.dataOrigin.packageName.lowercase()
            val sourceBonus = when {
                pkg.contains("shealth") || pkg.contains("samsung") -> 10L
                pkg.contains("fitness") || pkg.contains("health")   -> 5L
                else -> 0L
            }
            durationMin + stageBonus + sourceBonus
        } ?: cluster.first()
    }

    private suspend fun updateWidget() {
        WidgetUpdateWorker.updateAllWidgets(context)
    }

    companion object {
        const val WORK_NAME = "health_connect_sync"
        const val WORK_NAME_NOW = "health_connect_sync_now"

        const val KEY_ERROR = "error"
        const val ERROR_UNAVAILABLE = "unavailable"
        const val ERROR_NO_PERMISSIONS = "no_permissions"
        const val ERROR_READ_FAILED = "read_failed"

        private const val DAYS_TO_SYNC = 14L
        private const val KCAL_PER_STEP = 0.04f
        private val syncMutex = Mutex()

        fun buildRequest(intervalMinutes: Long = 30): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES)
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
