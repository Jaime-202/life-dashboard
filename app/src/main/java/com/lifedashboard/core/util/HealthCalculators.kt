package com.lifedashboard.core.util

/** Puntuación de preparación diaria. El panel y los widgets deben mostrar la misma cifra. */
data class Readiness(val score: Int, val status: String)

object ReadinessCalculator {
    const val STATUS_NO_SLEEP_DATA = "Sin datos de sueño"

    fun calculate(
        sleepHours: Float,
        sleepTargetHours: Float,
        previousDaySteps: Int,
        stepsTarget: Int
    ): Readiness {
        val hasSleepData = sleepHours > 0f
        val target = if (sleepTargetHours > 0f) sleepTargetHours else 8f
        // Sin registro de sueño se usa un valor neutro: no haber llevado el reloj no es dormir 0 h
        val sleepPoints = if (hasSleepData) (sleepHours / target).coerceIn(0f, 1.2f) * 45f else 30f

        val stepsGoal = if (stepsTarget > 0) stepsTarget else 10_000
        val activityRatio = (previousDaySteps.toFloat() / stepsGoal).coerceIn(0f, 1.5f)
        val activityPoints = when {
            activityRatio in 0.7f..1.2f -> 35f
            activityRatio > 1.2f -> 28f
            else -> 25f
        }
        val recoveryPoints = if (sleepHours >= 7f) 20f else 14f

        val score = (sleepPoints + activityPoints + recoveryPoints).toInt().coerceIn(35, 99)
        val status = when {
            !hasSleepData -> STATUS_NO_SLEEP_DATA
            score >= 85 -> "Óptima"
            score >= 70 -> "Buena"
            score >= 55 -> "Moderada"
            else -> "Recuperación"
        }
        return Readiness(score, status)
    }
}

data class EnergyTargets(
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val bmr: Float,
    val tdee: Float
)

/** Sexo biológico, usado solo para el ajuste de la fórmula metabólica. */
object Sex {
    const val MALE = "HOMBRE"
    const val FEMALE = "MUJER"
    fun normalize(value: String): String = if (value.equals(FEMALE, ignoreCase = true)) FEMALE else MALE
}

/** Cálculo de gasto y objetivos calóricos con la ecuación de Mifflin-St Jeor. */
object EnergyCalculator {
    const val DEFAULT_AGE = 25
    const val MIN_AGE = 14
    const val MAX_AGE = 100
    private const val MALE_OFFSET = 5f
    private const val FEMALE_OFFSET = -161f

    fun bmr(weightKg: Float, heightCm: Int, ageYears: Int, sex: String): Float {
        val sexOffset = if (Sex.normalize(sex) == Sex.FEMALE) FEMALE_OFFSET else MALE_OFFSET
        return 10f * weightKg + 6.25f * heightCm - 5f * ageYears + sexOffset
    }

    fun activityMultiplier(fitnessLevel: String): Float = when (fitnessLevel) {
        "PRINCIPIANTE" -> 1.25f
        "INTERMEDIO" -> 1.375f
        "AVANZADO" -> 1.55f
        "ATLETA" -> 1.725f
        else -> 1.35f
    }

    /** @param goal objetivo en cualquiera de sus dos formatos ("GRASA" o "PERDIDA_PESO", etc.). */
    fun targets(
        weightKg: Float,
        heightCm: Int,
        fitnessLevel: String,
        goal: String,
        ageYears: Int = DEFAULT_AGE,
        sex: String = Sex.MALE
    ): EnergyTargets {
        val validWeight = if (weightKg > 30f) weightKg else 75f
        val validHeight = if (heightCm > 100) heightCm else 175
        val validAge = ageYears.coerceIn(MIN_AGE, MAX_AGE)
        val bmr = bmr(validWeight, validHeight, validAge, sex)
        val tdee = bmr * activityMultiplier(fitnessLevel)

        val calories = when (FitnessGoals.toNutritionGoal(goal)) {
            FitnessGoals.NUTRITION_LOSE -> (tdee - 450f).coerceAtLeast(1400f)
            FitnessGoals.NUTRITION_GAIN -> tdee + 300f
            else -> tdee
        }
        val protein = (validWeight * 2f).coerceIn(90f, 240f)
        val fat = (validWeight * 0.85f).coerceIn(40f, 110f)
        val carbs = ((calories - (protein * 4f + fat * 9f)) / 4f).coerceAtLeast(50f)

        return EnergyTargets(calories, protein, carbs, fat, bmr, tdee)
    }
}

/**
 * La app maneja dos vocabularios de objetivo: el del perfil ("GRASA", "MUSCULO", "CARDIO",
 * "SALUD_GENERAL") y el de nutrición ("PERDIDA_PESO", "VOLUMEN", "MANTENIMIENTO").
 * En preferencias solo debe guardarse el del perfil.
 */
object FitnessGoals {
    const val NUTRITION_LOSE = "PERDIDA_PESO"
    const val NUTRITION_GAIN = "VOLUMEN"
    const val NUTRITION_MAINTAIN = "MANTENIMIENTO"

    fun toNutritionGoal(goal: String): String = when (goal) {
        "GRASA", NUTRITION_LOSE -> NUTRITION_LOSE
        "MUSCULO", NUTRITION_GAIN -> NUTRITION_GAIN
        else -> NUTRITION_MAINTAIN
    }

    /** Convierte a objetivo de perfil sin perder "CARDIO" si el objetivo nutricional no cambia. */
    fun toProfileGoal(goal: String, currentProfileGoal: String = "SALUD_GENERAL"): String = when (goal) {
        NUTRITION_LOSE, "GRASA" -> "GRASA"
        NUTRITION_GAIN, "MUSCULO" -> "MUSCULO"
        NUTRITION_MAINTAIN -> when (currentProfileGoal) {
            "CARDIO", "SALUD_GENERAL" -> currentProfileGoal
            else -> "SALUD_GENERAL"
        }
        else -> goal
    }
}
