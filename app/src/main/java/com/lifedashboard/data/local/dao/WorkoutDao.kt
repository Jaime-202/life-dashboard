package com.lifedashboard.data.local.dao

import androidx.room.*
import com.lifedashboard.data.local.entity.ExerciseSetEntity
import com.lifedashboard.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // ─── Sessions ────────────────────────────────────────────────────────────

    @Upsert
    suspend fun upsertSession(session: WorkoutSessionEntity): Long

    @Upsert
    suspend fun upsertSessions(sessions: List<WorkoutSessionEntity>)

    @Query("""
        SELECT * FROM workout_sessions
        WHERE dateMillis >= :fromMillis AND dateMillis <= :toMillis
        ORDER BY dateMillis DESC
    """)
    fun getSessionsInRange(fromMillis: Long, toMillis: Long): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions ORDER BY dateMillis DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<WorkoutSessionEntity>

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE type = 'STRENGTH' AND dateMillis >= :weekStartMillis")
    suspend fun countStrengthSessionsThisWeek(weekStartMillis: Long): Int

    // ─── Exercise Sets ───────────────────────────────────────────────────────

    @Upsert
    suspend fun upsertSets(sets: List<ExerciseSetEntity>)

    @Query("SELECT * FROM exercise_sets WHERE sessionId = :sessionId ORDER BY setNumber ASC")
    fun getSetsForSession(sessionId: Long): Flow<List<ExerciseSetEntity>>

    /** Volumen total por ejercicio en los últimos N días — para IA */
    @Query("""
        SELECT es.exerciseName, SUM(es.volumeKg) as totalVolume
        FROM exercise_sets es
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id
        WHERE ws.dateMillis >= :fromMillis
        GROUP BY es.exerciseName
        ORDER BY totalVolume DESC
    """)
    suspend fun getVolumeByExercise(fromMillis: Long): List<ExerciseVolumeResult>

    /** Máximo peso levantado por ejercicio — para detectar PRs */
    @Query("""
        SELECT es.exerciseName, MAX(es.weightKg) as maxWeight, ws.dateMillis
        FROM exercise_sets es
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id
        WHERE ws.dateMillis >= :fromMillis
        GROUP BY es.exerciseName
    """)
    suspend fun getMaxWeightByExercise(fromMillis: Long): List<ExerciseMaxWeightResult>
}

data class ExerciseVolumeResult(
    val exerciseName: String,
    val totalVolume: Float
)

data class ExerciseMaxWeightResult(
    val exerciseName: String,
    val maxWeight: Float,
    val dateMillis: Long
)
