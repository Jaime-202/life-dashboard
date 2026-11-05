package com.lifedashboard.core.health

import androidx.health.connect.client.records.ExerciseSessionRecord as Exercise

/** Conversiones puras de los tipos de Health Connect a los del dominio de la app. */
object HealthConnectMapper {

    private val CARDIO_TYPES: Map<Int, String> = mapOf(
        Exercise.EXERCISE_TYPE_RUNNING to "Carrera",
        Exercise.EXERCISE_TYPE_RUNNING_TREADMILL to "Carrera en cinta",
        Exercise.EXERCISE_TYPE_WALKING to "Caminata",
        Exercise.EXERCISE_TYPE_HIKING to "Senderismo",
        Exercise.EXERCISE_TYPE_BIKING to "Ciclismo",
        Exercise.EXERCISE_TYPE_BIKING_STATIONARY to "Bici estática",
        Exercise.EXERCISE_TYPE_ELLIPTICAL to "Elíptica",
        Exercise.EXERCISE_TYPE_ROWING to "Remo",
        Exercise.EXERCISE_TYPE_ROWING_MACHINE to "Máquina de remo",
        Exercise.EXERCISE_TYPE_STAIR_CLIMBING to "Subir escaleras",
        Exercise.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE to "Escaladora",
        Exercise.EXERCISE_TYPE_SWIMMING_POOL to "Natación",
        Exercise.EXERCISE_TYPE_SWIMMING_OPEN_WATER to "Natación",
        Exercise.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING to "HIIT",
        Exercise.EXERCISE_TYPE_DANCING to "Baile",
        Exercise.EXERCISE_TYPE_SKIING to "Esquí",
        Exercise.EXERCISE_TYPE_SOCCER to "Fútbol",
        Exercise.EXERCISE_TYPE_BASKETBALL to "Baloncesto",
        Exercise.EXERCISE_TYPE_TENNIS to "Tenis",
        Exercise.EXERCISE_TYPE_PADDLING to "Piragüismo"
    )

    /** "CARDIO" o "STRENGTH". Los tipos genéricos se tratan como fuerza, como hasta ahora. */
    fun workoutTypeFor(exerciseType: Int): String =
        if (exerciseType in CARDIO_TYPES) "CARDIO" else "STRENGTH"

    /** Nombre legible del ejercicio, o null si no aporta más que el tipo de sesión. */
    fun exerciseLabelFor(exerciseType: Int): String? = CARDIO_TYPES[exerciseType]
}
