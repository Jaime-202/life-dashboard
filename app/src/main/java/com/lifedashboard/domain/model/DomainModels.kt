package com.lifedashboard.domain.model

import java.time.LocalDate

data class HealthData(
    val date: LocalDate,
    val steps: Int,
    val distanceMeters: Float,
    val activeCalories: Float,
    val sleepHours: Float,
    val sleepDeepHours: Float,
    val sleepRemHours: Float
) {
    val stepsProgress: Float get() = (steps / 10_000f).coerceIn(0f, 1f)
    val sleepProgress: Float get() = (sleepHours / 8f).coerceIn(0f, 1f)
    val stepsGoalMet: Boolean get() = steps >= 7_000
    val sleepGoalMet: Boolean get() = sleepHours >= 7.5f
}

data class WorkoutSession(
    val id: Long,
    val date: LocalDate,
    val type: WorkoutType,
    val durationMinutes: Int,
    val sets: List<ExerciseSet> = emptyList(),
    val notes: String? = null
) {
    val totalVolume: Float get() = sets.sumOf { it.volume.toDouble() }.toFloat()
}

data class ExerciseSet(
    val id: Long,
    val sessionId: Long,
    val exerciseName: String,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float,
    val rpe: Float? = null
) {
    val volume: Float get() = weightKg * reps
}

enum class WorkoutType { STRENGTH, CARDIO, REST }

data class WaterIntake(
    val id: Long,
    val date: LocalDate,
    val timestampMillis: Long,
    val amountMl: Int
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val iconName: String,
    val unlockedAt: Long?,
    val progress: Float,
    val targetValue: Float
) {
    val isUnlocked: Boolean get() = unlockedAt != null
    val progressPercent: Float get() = (progress / targetValue).coerceIn(0f, 1f)
}

enum class AchievementCategory { HEALTH, WORKOUT, SLEEP, CONSISTENCY }

/**
 * Snapshot de progreso diario — usado por el calendario y el widget.
 */
data class DailyProgress(
    val date: LocalDate,
    val workoutCompleted: Boolean,
    val workoutType: WorkoutType?,
    val stepsProgress: Float,       // 0f..1f
    val sleepProgress: Float,       // 0f..1f
    val waterProgress: Float = 0f,
    val actualSteps: Int = 0,       // Pasos reales de Health Connect
    val allGoalsMet: Boolean = workoutCompleted &&
            stepsProgress >= 1f && sleepProgress >= 1f
)
