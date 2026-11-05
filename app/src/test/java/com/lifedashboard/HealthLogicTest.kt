package com.lifedashboard

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.lifedashboard.core.health.HealthConnectMapper
import com.lifedashboard.core.notification.ReminderScheduler
import com.lifedashboard.core.util.EnergyCalculator
import com.lifedashboard.core.util.FitnessGoals
import com.lifedashboard.core.util.ImageUtils
import com.lifedashboard.core.util.ReadinessCalculator
import com.lifedashboard.core.util.Sex
import com.lifedashboard.data.local.entity.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ReminderScheduleTest {

    // 4 de noviembre de 2026 es miércoles
    private val wednesdayAtNoon = LocalDateTime.of(2026, 11, 4, 12, 0)

    @Test
    fun `un recordatorio diario suena hoy si la hora aun no ha pasado`() {
        val next = ReminderScheduler.nextOccurrence(ReminderType.DIARIO, "", "18:30", wednesdayAtNoon)
        assertEquals(LocalDateTime.of(2026, 11, 4, 18, 30), next)
    }

    @Test
    fun `un recordatorio diario pasa a manana si la hora ya ha pasado`() {
        val next = ReminderScheduler.nextOccurrence(ReminderType.DIARIO, "", "08:00", wednesdayAtNoon)
        assertEquals(LocalDateTime.of(2026, 11, 5, 8, 0), next)
    }

    @Test
    fun `la hora exacta actual no se considera futura`() {
        val next = ReminderScheduler.nextOccurrence(ReminderType.DIARIO, "", "12:00", wednesdayAtNoon)
        assertEquals(LocalDateTime.of(2026, 11, 5, 12, 0), next)
    }

    @Test
    fun `un recordatorio semanal salta al siguiente dia activo`() {
        val next = ReminderScheduler.nextOccurrence(ReminderType.SEMANAL, "1,5", "09:00", wednesdayAtNoon)
        assertEquals(DayOfWeek.FRIDAY, next.dayOfWeek)
        assertEquals(LocalDateTime.of(2026, 11, 6, 9, 0), next)
    }

    @Test
    fun `un recordatorio semanal de un unico dia ya pasado espera a la semana siguiente`() {
        val next = ReminderScheduler.nextOccurrence(ReminderType.SEMANAL, "3", "09:00", wednesdayAtNoon)
        assertEquals(LocalDateTime.of(2026, 11, 11, 9, 0), next)
    }

    @Test
    fun `una hora invalida usa las nueve por defecto`() {
        val next = ReminderScheduler.nextOccurrence(ReminderType.DIARIO, "", "xx:yy", wednesdayAtNoon)
        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), next)
    }

    @Test
    fun `solo los semanales dependen del dia`() {
        assertTrue(ReminderScheduler.appliesOnDay(ReminderType.DIARIO, "1", 3))
        assertTrue(ReminderScheduler.appliesOnDay(ReminderType.SEMANAL, "1,3", 3))
        assertFalse(ReminderScheduler.appliesOnDay(ReminderType.SEMANAL, "1,2", 3))
        assertTrue(ReminderScheduler.appliesOnDay(ReminderType.SEMANAL, "", 3))
    }
}

class ReadinessCalculatorTest {

    @Test
    fun `sin datos de sueno no se informa de falta de recuperacion`() {
        val readiness = ReadinessCalculator.calculate(0f, 8f, 8_000, 10_000)
        assertEquals(ReadinessCalculator.STATUS_NO_SLEEP_DATA, readiness.status)
        assertTrue(readiness.score >= 55)
    }

    @Test
    fun `dormir el objetivo con actividad adecuada es optimo`() {
        val readiness = ReadinessCalculator.calculate(8f, 8f, 8_000, 10_000)
        assertEquals(99, readiness.score)
        assertEquals("Óptima", readiness.status)
    }

    @Test
    fun `dormir poco baja la puntuacion`() {
        val rested = ReadinessCalculator.calculate(8f, 8f, 8_000, 10_000)
        val tired = ReadinessCalculator.calculate(4f, 8f, 8_000, 10_000)
        assertTrue(tired.score < rested.score)
        assertEquals(71, tired.score)
        assertEquals("Buena", tired.status)
    }

    @Test
    fun `objetivos a cero no provocan divisiones invalidas`() {
        val readiness = ReadinessCalculator.calculate(7f, 0f, 5_000, 0)
        assertTrue(readiness.score in 35..99)
    }
}

class EnergyCalculatorTest {

    @Test
    fun `calcula la tasa basal de un hombre con Mifflin-St Jeor`() {
        // 10*80 + 6.25*180 - 5*30 + 5
        assertEquals(1780f, EnergyCalculator.bmr(80f, 180, 30, Sex.MALE), 0.01f)
    }

    @Test
    fun `una mujer tiene una tasa basal menor que un hombre igual`() {
        val male = EnergyCalculator.bmr(70f, 170, 30, Sex.MALE)
        val female = EnergyCalculator.bmr(70f, 170, 30, Sex.FEMALE)
        // La ecuación resta 161 a la mujer y suma 5 al hombre: 166 kcal de diferencia
        assertEquals(166f, male - female, 0.01f)
    }

    @Test
    fun `mas edad reduce el gasto basal`() {
        val young = EnergyCalculator.bmr(70f, 170, 20, Sex.MALE)
        val old = EnergyCalculator.bmr(70f, 170, 60, Sex.MALE)
        assertEquals(200f, young - old, 0.01f)
    }

    @Test
    fun `perdida de peso aplica deficit y volumen superavit`() {
        val maintain = EnergyCalculator.targets(80f, 180, "INTERMEDIO", "SALUD_GENERAL")
        val lose = EnergyCalculator.targets(80f, 180, "INTERMEDIO", "GRASA")
        val gain = EnergyCalculator.targets(80f, 180, "INTERMEDIO", "MUSCULO")
        assertEquals(maintain.calories - 450f, lose.calories, 0.01f)
        assertEquals(maintain.calories + 300f, gain.calories, 0.01f)
    }

    @Test
    fun `ambos vocabularios de objetivo dan el mismo resultado`() {
        val profile = EnergyCalculator.targets(80f, 180, "AVANZADO", "GRASA")
        val nutrition = EnergyCalculator.targets(80f, 180, "AVANZADO", "PERDIDA_PESO")
        assertEquals(profile, nutrition)
    }

    @Test
    fun `el nivel de actividad cambia el gasto total`() {
        val beginner = EnergyCalculator.targets(80f, 180, "PRINCIPIANTE", "SALUD_GENERAL")
        val athlete = EnergyCalculator.targets(80f, 180, "ATLETA", "SALUD_GENERAL")
        assertTrue(athlete.tdee > beginner.tdee)
    }

    @Test
    fun `un perfil invalido usa valores de referencia`() {
        val targets = EnergyCalculator.targets(0f, 0, "INTERMEDIO", "SALUD_GENERAL")
        assertEquals(EnergyCalculator.bmr(75f, 175, EnergyCalculator.DEFAULT_AGE, Sex.MALE), targets.bmr, 0.01f)
    }
}

class FitnessGoalsTest {

    @Test
    fun `el objetivo de nutricion se guarda como objetivo de perfil`() {
        assertEquals("GRASA", FitnessGoals.toProfileGoal("PERDIDA_PESO"))
        assertEquals("MUSCULO", FitnessGoals.toProfileGoal("VOLUMEN"))
        assertEquals("SALUD_GENERAL", FitnessGoals.toProfileGoal("MANTENIMIENTO"))
    }

    @Test
    fun `mantenimiento conserva un objetivo de cardio`() {
        assertEquals("CARDIO", FitnessGoals.toProfileGoal("MANTENIMIENTO", "CARDIO"))
    }

    @Test
    fun `el objetivo sobrevive a la ida y vuelta entre vocabularios`() {
        listOf("GRASA", "MUSCULO", "SALUD_GENERAL", "CARDIO").forEach { goal ->
            val nutrition = FitnessGoals.toNutritionGoal(goal)
            assertEquals(goal, FitnessGoals.toProfileGoal(nutrition, goal))
        }
    }
}

class HealthConnectMapperTest {

    @Test
    fun `las sesiones de cardio no cuentan como fuerza`() {
        assertEquals("CARDIO", HealthConnectMapper.workoutTypeFor(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))
        assertEquals("CARDIO", HealthConnectMapper.workoutTypeFor(ExerciseSessionRecord.EXERCISE_TYPE_WALKING))
        assertEquals("STRENGTH", HealthConnectMapper.workoutTypeFor(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING))
        assertEquals("STRENGTH", HealthConnectMapper.workoutTypeFor(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT))
    }

    @Test
    fun `solo los tipos conocidos tienen etiqueta propia`() {
        assertEquals("Carrera", HealthConnectMapper.exerciseLabelFor(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))
        assertEquals(null, HealthConnectMapper.exerciseLabelFor(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT))
    }
}

class ImageUtilsTest {

    @Test
    fun `una foto pequena no se reduce`() {
        assertEquals(1, ImageUtils.sampleSizeFor(1200, 800, 1600))
    }

    @Test
    fun `una foto de 50 megapixeles se reduce manteniendo resolucion suficiente`() {
        val sample = ImageUtils.sampleSizeFor(8160, 6120, 1600)
        assertEquals(4, sample)
        assertTrue(8160 / sample >= 1600)
    }
}
