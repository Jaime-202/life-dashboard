package com.lifedashboard.data.repository

import android.content.Context
import android.net.Uri
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.MealDao
import com.lifedashboard.data.local.dao.ReminderDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.dao.WorkoutDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Copia de todos los datos del usuario en JSON. Nunca incluye la clave de la API de Gemini. */
@Singleton
class DataExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthDao: HealthDao,
    private val mealDao: MealDao,
    private val weightDao: WeightDao,
    private val workoutDao: WorkoutDao,
    private val reminderDao: ReminderDao,
    private val userPreferences: UserPreferencesRepository
) {

    suspend fun exportTo(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = buildExport().toString(2)
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("No se pudo abrir el archivo de destino")
            stream.bufferedWriter().use { it.write(json) }
        }
    }

    private suspend fun buildExport(): JSONObject {
        val goals = userPreferences.userGoals.first()
        return JSONObject().apply {
            put("exportedAtMillis", System.currentTimeMillis())
            put("profile", JSONObject().apply {
                put("name", goals.userName)
                put("fitnessLevel", goals.fitnessLevel)
                put("primaryGoal", goals.primaryGoal)
                put("weightKg", goals.weightKg.toDouble())
                put("heightCm", goals.heightCm)
                put("stepsTarget", goals.stepsTarget)
                put("sleepTargetHours", goals.sleepTargetHours.toDouble())
                put("intakeCaloriesTarget", goals.caloriesTarget.toDouble())
                put("activeCaloriesTarget", goals.activeCaloriesTarget.toDouble())
                put("proteinTargetGrams", goals.proteinTargetGrams.toDouble())
                put("carbsTargetGrams", goals.carbsTargetGrams.toDouble())
                put("fatTargetGrams", goals.fatTargetGrams.toDouble())
                put("workoutDaysTarget", goals.workoutDaysTarget)
                put("workoutMinutesTarget", goals.workoutMinutesTarget)
            })
            put("health", jsonArrayOf(healthDao.getAll()) { h ->
                put("dateMillis", h.dateMillis)
                put("steps", h.steps)
                put("distanceMeters", h.distanceMeters.toDouble())
                put("activeCalories", h.activeCalories.toDouble())
                put("sleepHours", h.sleepHours.toDouble())
                put("sleepDeepHours", h.sleepDeepHours.toDouble())
                put("sleepRemHours", h.sleepRemHours.toDouble())
            })
            put("meals", jsonArrayOf(mealDao.getAll()) { m ->
                put("dateMillis", m.dateMillis)
                put("mealType", m.mealType)
                put("name", m.name)
                put("brand", m.brand)
                put("grams", m.grams.toDouble())
                put("calories", m.calories.toDouble())
                put("protein", m.protein.toDouble())
                put("carbs", m.carbs.toDouble())
                put("fat", m.fat.toDouble())
                put("notes", m.notes)
                put("isFavorite", m.isFavorite)
            })
            put("weights", jsonArrayOf(weightDao.observeAllWeights().first()) { w ->
                put("dateMillis", w.dateMillis)
                put("weightKg", w.weightKg.toDouble())
                put("note", w.note ?: JSONObject.NULL)
            })
            val setsBySession = workoutDao.getAllSets().groupBy { it.sessionId }
            put("workouts", jsonArrayOf(workoutDao.getAllSessions()) { w ->
                put("dateMillis", w.dateMillis)
                put("type", w.type)
                put("durationMinutes", w.durationMinutes)
                put("notes", w.notes ?: JSONObject.NULL)
                put("source", w.sourceFile ?: JSONObject.NULL)
                put("sets", jsonArrayOf(setsBySession[w.id].orEmpty()) { set ->
                    put("exercise", set.exerciseName)
                    put("setNumber", set.setNumber)
                    put("reps", set.reps)
                    put("weightKg", set.weightKg.toDouble())
                })
            })
            put("reminders", jsonArrayOf(reminderDao.observeAll().first()) { r ->
                put("title", r.title)
                put("type", r.type)
                put("daysOfWeek", r.daysOfWeek)
                put("timeOfDay", r.timeOfDay)
                put("isEnabled", r.isEnabled)
            })
        }
    }

    private inline fun <T> jsonArrayOf(items: List<T>, fill: JSONObject.(T) -> Unit): JSONArray =
        JSONArray().also { array ->
            items.forEach { item -> array.put(JSONObject().apply { fill(item) }) }
        }
}
