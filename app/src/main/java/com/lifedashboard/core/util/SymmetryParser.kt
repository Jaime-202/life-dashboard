package com.lifedashboard.core.util

import android.content.Context
import android.net.Uri
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.ExerciseSetEntity
import com.lifedashboard.data.local.entity.WorkoutSessionEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

enum class MuscleGroup(val displayName: String) {
    CHEST("Pecho"),
    BACK("Espalda"),
    LEGS("Cuádriceps / Piernas"),
    HAMSTRINGS_GLUTES("Isquios / Glúteos"),
    SHOULDERS("Hombros"),
    ARMS("Bíceps / Tríceps"),
    CORE("Core / Abdomen"),
    CARDIO("Cardio")
}

data class ParsedSet(
    val dateMillis: Long,
    val exerciseName: String,
    val muscleGroup: MuscleGroup,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float,
    val notes: String?
)

object SymmetryParser {

    private val EXERCISE_NAME_MAP = mapOf(
        "squat pendulum"        to Pair("Sentadilla en Péndulo", MuscleGroup.LEGS),
        "pendulum squat"        to Pair("Sentadilla en Péndulo", MuscleGroup.LEGS),
        "leg press"             to Pair("Prensa de Pierna", MuscleGroup.LEGS),
        "romanian deadlift"     to Pair("Peso Muerto Rumano", MuscleGroup.HAMSTRINGS_GLUTES),
        "rdl"                   to Pair("Peso Muerto Rumano", MuscleGroup.HAMSTRINGS_GLUTES),
        "unilateral high row"   to Pair("Remo Alto Unilateral", MuscleGroup.BACK),
        "chest press"           to Pair("Press de Pecho", MuscleGroup.CHEST),
        "incline chest press"   to Pair("Press Inclinado", MuscleGroup.CHEST),
        "machine crunch"        to Pair("Abdominales en Máquina", MuscleGroup.CORE),
        "ab crunch machine"     to Pair("Abdominales en Máquina", MuscleGroup.CORE),
        "lat pulldown"          to Pair("Jalón al Pecho", MuscleGroup.BACK),
        "cable row"             to Pair("Remo en Polea", MuscleGroup.BACK),
        "shoulder press"        to Pair("Press de Hombros", MuscleGroup.SHOULDERS),
        "lateral raise"         to Pair("Elevaciones Laterales", MuscleGroup.SHOULDERS),
        "bicep curl"            to Pair("Curl de Bíceps", MuscleGroup.ARMS),
        "tricep pushdown"       to Pair("Extensión de Tríceps", MuscleGroup.ARMS)
    )

    fun getMuscleGroupForExercise(exerciseName: String): MuscleGroup {
        val lower = exerciseName.lowercase().trim()
        val mapped = EXERCISE_NAME_MAP[lower]
        if (mapped != null) return mapped.second

        return when {
            lower.contains("sentadilla") || lower.contains("prensa") || lower.contains("leg") || lower.contains("quad") -> MuscleGroup.LEGS
            lower.contains("deadlift") || lower.contains("rumano") || lower.contains("glute") || lower.contains("hip") -> MuscleGroup.HAMSTRINGS_GLUTES
            lower.contains("press") && (lower.contains("pecho") || lower.contains("chest") || lower.contains("inclinado")) -> MuscleGroup.CHEST
            lower.contains("row") || lower.contains("remo") || lower.contains("pulldown") || lower.contains("jalon") || lower.contains("espalda") -> MuscleGroup.BACK
            lower.contains("hombro") || lower.contains("shoulder") || lower.contains("lateral") -> MuscleGroup.SHOULDERS
            lower.contains("bicep") || lower.contains("tricep") || lower.contains("curl") -> MuscleGroup.ARMS
            lower.contains("crunch") || lower.contains("abs") || lower.contains("core") -> MuscleGroup.CORE
            else -> MuscleGroup.CHEST
        }
    }

    /**
     * Parsea un archivo CSV desde Uri (usado con el selector de archivos del sistema).
     */
    fun parseCsvFromUri(context: Context, uri: Uri): List<ParsedSet> {
        val resolver = context.contentResolver
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val rawSets = mutableListOf<ParsedSet>()

        resolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                var isHeader = true
                lines.forEach { line ->
                    if (isHeader) { isHeader = false; return@forEach }
                    if (line.isBlank()) return@forEach

                    val cols = line.split(",")
                    if (cols.size < 5) return@forEach

                    try {
                        val date = dateFormat.parse(cols[0].trim()) ?: return@forEach
                        val rawEx = cols[1].trim()
                        val (cleanName, muscleGroup) = normalizeExercise(rawEx)

                        rawSets.add(
                            ParsedSet(
                                dateMillis   = date.time,
                                exerciseName = cleanName,
                                muscleGroup  = muscleGroup,
                                setNumber    = cols[2].trim().toIntOrNull() ?: 1,
                                reps         = cols[3].trim().toIntOrNull() ?: 0,
                                weightKg     = cols[4].trim().toFloatOrNull() ?: 0f,
                                notes        = cols.getOrNull(5)?.trim()
                            )
                        )
                    } catch (e: Exception) {
                        // Ignorar líneas corruptas
                    }
                }
            }
        }
        return rawSets
    }

    /**
     * Parsea un bloque de texto CSV o formato pegado por el usuario.
     */
    fun parseCsvText(text: String): List<ParsedSet> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val rawSets = mutableListOf<ParsedSet>()

        text.lines().forEach { line ->
            if (line.isBlank() || line.startsWith("Date", ignoreCase = true)) return@forEach
            val cols = line.split(",")
            if (cols.size < 5) return@forEach

            try {
                val date = dateFormat.parse(cols[0].trim()) ?: return@forEach
                val rawEx = cols[1].trim()
                val (cleanName, muscleGroup) = normalizeExercise(rawEx)

                rawSets.add(
                    ParsedSet(
                        dateMillis   = date.time,
                        exerciseName = cleanName,
                        muscleGroup  = muscleGroup,
                        setNumber    = cols[2].trim().toIntOrNull() ?: 1,
                        reps         = cols[3].trim().toIntOrNull() ?: 0,
                        weightKg     = cols[4].trim().toFloatOrNull() ?: 0f,
                        notes        = cols.getOrNull(5)?.trim()
                    )
                )
            } catch (e: Exception) {
                // Continuar
            }
        }
        return rawSets
    }

    private fun normalizeExercise(raw: String): Pair<String, MuscleGroup> {
        val lower = raw.lowercase().trim()
        val mapped = EXERCISE_NAME_MAP[lower]
        if (mapped != null) return mapped

        val group = getMuscleGroupForExercise(raw)
        return Pair(raw, group)
    }

    suspend fun saveParsedSetsToDb(workoutDao: WorkoutDao, sets: List<ParsedSet>): Int {
        if (sets.isEmpty()) return 0

        val setsByDate = sets.groupBy { it.dateMillis }
        var importedSessions = 0

        setsByDate.forEach { (dateMillis, daySets) ->
            val sessionId = workoutDao.upsertSession(
                WorkoutSessionEntity(
                    dateMillis      = dateMillis,
                    type            = "STRENGTH",
                    durationMinutes = 45,
                    sourceFile      = "file_import"
                )
            )

            val setEntities = daySets.map { s ->
                ExerciseSetEntity(
                    sessionId    = sessionId,
                    exerciseName = s.exerciseName,
                    setNumber    = s.setNumber,
                    reps         = s.reps,
                    weightKg     = s.weightKg,
                    volumeKg     = s.weightKg * s.reps
                )
            }

            workoutDao.upsertSets(setEntities)
            importedSessions++
        }

        return importedSessions
    }
}
