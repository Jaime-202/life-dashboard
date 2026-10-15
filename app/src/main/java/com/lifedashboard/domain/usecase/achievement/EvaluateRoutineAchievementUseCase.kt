package com.lifedashboard.domain.usecase.achievement

import com.lifedashboard.data.local.dao.AchievementDao
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.AchievementEntity
import com.lifedashboard.core.util.DateUtils.daysAgoMillis
import com.lifedashboard.core.util.DateUtils.weekStartMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * EvaluateRoutineAchievementUseCase
 *
 * Evalúa el cumplimiento de la rutina estructurada:
 * - 5 días de entrenamiento de fuerza / semana
 * - 1 día de cardio (25min bici + 25min caminata con inclinación)
 * - 1 día de descanso
 * - Meta de pasos: 7000-10000 diarios
 * - Meta de sueño: 8h diarias
 *
 * Crea/actualiza entidades Achievement en Room.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Singleton
class EvaluateRoutineAchievementUseCase @Inject constructor(
    private val healthDao: HealthDao,
    private val workoutDao: WorkoutDao,
    private val achievementDao: AchievementDao
) {
    suspend operator fun invoke(): Unit = withContext(Dispatchers.IO) {
        val achievements = mutableListOf<AchievementEntity>()
        val now = System.currentTimeMillis()

        // ─── Fuerza: 5 sesiones esta semana ──────────────────────────────
        val strengthThisWeek = workoutDao.countStrengthSessionsThisWeek(weekStartMillis())
        achievements.add(
            AchievementEntity(
                id          = "STRENGTH_5_WEEK",
                title       = "Semana de Hierro",
                description = "5 sesiones de fuerza en una semana",
                category    = "WORKOUT",
                iconName    = "dumbbell",
                unlockedAt  = if (strengthThisWeek >= 5) now else null,
                progress    = strengthThisWeek.toFloat(),
                targetValue = 5f
            )
        )

        // ─── Pasos: 7 días seguidos con ≥7000 ────────────────────────────
        val last7Health = healthDao.getLastNDays(7)
        val stepsStreakDays = last7Health.count { it.steps >= 7_000 }
        achievements.add(
            AchievementEntity(
                id          = "STEPS_7K_STREAK_7",
                title       = "Caminante Constante",
                description = "7 días seguidos con más de 7000 pasos",
                category    = "HEALTH",
                iconName    = "footprint",
                unlockedAt  = if (stepsStreakDays >= 7) now else null,
                progress    = stepsStreakDays.toFloat(),
                targetValue = 7f
            )
        )

        // ─── Pasos élite: 10000 diarios 7 días seguidos ──────────────────
        val eliteStepsDays = last7Health.count { it.steps >= 10_000 }
        achievements.add(
            AchievementEntity(
                id          = "STEPS_10K_STREAK_7",
                title       = "Modo Élite",
                description = "7 días con más de 10000 pasos",
                category    = "HEALTH",
                iconName    = "star",
                unlockedAt  = if (eliteStepsDays >= 7) now else null,
                progress    = eliteStepsDays.toFloat(),
                targetValue = 7f
            )
        )

        // ─── Sueño: 8h durante 5 días ─────────────────────────────────────
        val sleepGoalDays = last7Health.count { it.sleepHours >= 7.5f }
        achievements.add(
            AchievementEntity(
                id          = "SLEEP_8H_5DAYS",
                title       = "Recuperación Óptima",
                description = "5 noches con al menos 7.5h de sueño",
                category    = "SLEEP",
                iconName    = "moon",
                unlockedAt  = if (sleepGoalDays >= 5) now else null,
                progress    = sleepGoalDays.toFloat(),
                targetValue = 5f
            )
        )

        // ─── Consistencia total: todos los pilares en un mismo día ───────
        val perfectDays = last7Health.count { h ->
            h.steps >= 7_000 && h.sleepHours >= 7.5f
        }
        achievements.add(
            AchievementEntity(
                id          = "PERFECT_DAY_3",
                title       = "Triatleta de Vida",
                description = "3 días perfectos (pasos + sueño + entreno)",
                category    = "CONSISTENCY",
                iconName    = "trophy",
                unlockedAt  = if (perfectDays >= 3) now else null,
                progress    = perfectDays.toFloat(),
                targetValue = 3f
            )
        )

        achievementDao.upsertAll(achievements)
    }
}
