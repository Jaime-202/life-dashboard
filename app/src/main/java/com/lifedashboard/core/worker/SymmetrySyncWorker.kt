package com.lifedashboard.core.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.ExerciseSetEntity
import com.lifedashboard.data.local.entity.WorkoutSessionEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private const val TAG = "SymmetrySyncWorker"
private const val KEY_FOLDER_URI = "symmetry_folder_uri"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * SymmetrySyncWorker
 *
 * Parsea exportaciones CSV de Symmetry desde la carpeta seleccionada
 * via Storage Access Framework (SAF). La URI de la carpeta con permiso
 * persistente se pasa como InputData.
 *
 * Formato CSV esperado de Symmetry:
 * Date,Exercise,Set,Reps,Weight(kg),Notes
 * 2024-01-15,Squat Pendulum,1,8,80,
 * 2024-01-15,Romanian Deadlift,1,10,60,
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
@HiltWorker
class SymmetrySyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val workoutDao: WorkoutDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val folderUriString = inputData.getString(KEY_FOLDER_URI)
            ?: return@withContext Result.failure(
                workDataOf("error" to "No folder URI provided")
            )

        return@withContext try {
            val folderUri = Uri.parse(folderUriString)
            val processedCount = processSymmetryFolder(folderUri)
            Log.d(TAG, "Symmetry sync: $processedCount sesiones importadas")
            Result.success(workDataOf("processed" to processedCount))
        } catch (e: SecurityException) {
            Log.e(TAG, "Permiso SAF revocado", e)
            Result.failure(workDataOf("error" to "SAF permission revoked"))
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando Symmetry", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    // ─── Recorre la carpeta y procesa cada CSV ────────────────────────────

    private suspend fun processSymmetryFolder(folderUri: Uri): Int {
        var count = 0
        val resolver = context.contentResolver

        // Listamos los archivos de la carpeta usando DocumentsContract
        val childrenUri = androidx.documentfile.provider.DocumentFile
            .fromTreeUri(context, folderUri)
            ?: return 0

        childrenUri.listFiles()
            .filter { it.isFile && it.name?.endsWith(".csv", ignoreCase = true) == true }
            .forEach { file ->
                try {
                    val uri = file.uri
                    val sessions = parseCsvFile(uri)
                    saveSessions(sessions)
                    count += sessions.size
                } catch (e: Exception) {
                    Log.w(TAG, "Error procesando ${file.name}", e)
                }
            }

        return count
    }

    // ─── Parser CSV ───────────────────────────────────────────────────────

    private fun parseCsvFile(uri: Uri): List<ParsedSession> {
        val resolver = context.contentResolver
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val rawSets = mutableListOf<RawSet>()

        resolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                var isHeader = true
                lines.forEach { line ->
                    if (isHeader) { isHeader = false; return@forEach } // Saltamos cabecera
                    if (line.isBlank()) return@forEach

                    val cols = line.split(",")
                    if (cols.size < 5) return@forEach

                    try {
                        val date = dateFormat.parse(cols[0].trim()) ?: return@forEach
                        rawSets.add(
                            RawSet(
                                dateMillis   = date.time,
                                exerciseName = normalizeExerciseName(cols[1].trim()),
                                setNumber    = cols[2].trim().toIntOrNull() ?: 1,
                                reps         = cols[3].trim().toIntOrNull() ?: 0,
                                weightKg     = cols[4].trim().toFloatOrNull() ?: 0f,
                                notes        = cols.getOrNull(5)?.trim()
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Línea inválida: $line", e)
                    }
                }
            }
        }

        // Agrupamos los sets por fecha para crear sesiones
        return rawSets
            .groupBy { it.dateMillis }
            .map { (dateMillis, sets) ->
                ParsedSession(dateMillis = dateMillis, sets = sets)
            }
    }

    // ─── Persistencia en Room ─────────────────────────────────────────────

    private suspend fun saveSessions(sessions: List<ParsedSession>) {
        sessions.forEach { parsed ->
            // Creamos la sesión
            val sessionId = workoutDao.upsertSession(
                WorkoutSessionEntity(
                    dateMillis      = parsed.dateMillis,
                    type            = "STRENGTH",
                    durationMinutes = 0,
                    sourceFile      = "symmetry_csv"
                )
            )

            // Insertamos los sets
            val setEntities = parsed.sets.map { raw ->
                ExerciseSetEntity(
                    sessionId    = sessionId,
                    exerciseName = raw.exerciseName,
                    setNumber    = raw.setNumber,
                    reps         = raw.reps,
                    weightKg     = raw.weightKg,
                    volumeKg     = raw.weightKg * raw.reps
                )
            }
            workoutDao.upsertSets(setEntities)
        }
    }

    // ─── Normalización de nombres ─────────────────────────────────────────

    /**
     * Mapea los nombres de ejercicios de Symmetry a nombres canónicos
     * usados en toda la app y en el contexto de la IA.
     */
    private fun normalizeExerciseName(raw: String): String {
        return EXERCISE_NAME_MAP[raw.lowercase()] ?: raw
    }

    companion object {
        const val WORK_NAME = "symmetry_sync"

        // Mapa de normalización: clave = nombre en CSV, valor = nombre canónico
        private val EXERCISE_NAME_MAP = mapOf(
            "squat pendulum"            to "Sentadilla en Péndulo",
            "pendulum squat"            to "Sentadilla en Péndulo",
            "romanian deadlift"         to "Peso Muerto Rumano",
            "rdl"                       to "Peso Muerto Rumano",
            "unilateral high row"       to "Remo Alto Unilateral",
            "chest press"               to "Press de Pecho",
            "machine crunch"            to "Abdominales en Máquina",
            "ab crunch machine"         to "Abdominales en Máquina",
            "lat pulldown"              to "Jalón al Pecho",
            "cable row"                 to "Remo en Polea",
            "leg press"                 to "Prensa de Pierna",
            "shoulder press"            to "Press de Hombros",
            "bicep curl"                to "Curl de Bíceps",
            "tricep pushdown"           to "Extensión de Tríceps",
        )

        fun buildRequest(folderUri: String): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<SymmetrySyncWorker>(24, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_FOLDER_URI to folderUri))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
    }
}

// ─── Data classes internas ────────────────────────────────────────────────────

private data class RawSet(
    val dateMillis: Long,
    val exerciseName: String,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float,
    val notes: String?
)

private data class ParsedSession(
    val dateMillis: Long,
    val sets: List<RawSet>
)
