package com.lifedashboard.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

data class UserGoals(
    val stepsTarget: Int = 10000,
    val sleepTargetHours: Float = 8.0f,
    val caloriesTarget: Float = 2200f,
    val workoutDaysTarget: Int = 4,
    val workoutMinutesTarget: Int = 180,
    val userName: String = "Jaime",
    val fitnessLevel: String = "INTERMEDIO",       // "PRINCIPIANTE" | "INTERMEDIO" | "AVANZADO" | "ATLETA"
    val primaryGoal: String = "SALUD_GENERAL",     // "GRASA" | "MUSCULO" | "CARDIO" | "SALUD_GENERAL"
    val weightKg: Float = 75.0f,
    val heightCm: Int = 175,
    val showHeroRings: Boolean = true,
    val showAiInsight: Boolean = true,
    val showUpcomingEvents: Boolean = true,
    val dailyReminders: Boolean = true,
    val hydrationReminder: Boolean = true,
    val inactivityAlerts: Boolean = false,
    val eveningReview: Boolean = true,
    val aiStyle: String = "CIENTIFICO",            // "CIENTIFICO" | "MOTIVADOR" | "DIRECTO"
    val syncFrequency: String = "30_MIN",          // "15_MIN" | "30_MIN" | "60_MIN" | "MANUAL"
    val themeMode: String = "OSCURO",
    val geminiApiKey: String = "",
    val bedtimeReminderEnabled: Boolean = true,
    val wakeUpHour: Int = 8,
    val wakeUpMinute: Int = 30,
    val bedtimeReminderMinutesBefore: Int = 30,
    val proteinTargetGrams: Float = 140f,
    val carbsTargetGrams: Float = 250f,
    val fatTargetGrams: Float = 70f
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val STEPS_TARGET           = intPreferencesKey("steps_target")
        val SLEEP_TARGET           = floatPreferencesKey("sleep_target")
        val CALORIES_TARGET        = floatPreferencesKey("calories_target")
        val WORKOUT_DAYS_TARGET    = intPreferencesKey("workout_days_target")
        val WORKOUT_MINUTES_TARGET = intPreferencesKey("workout_minutes_target")
        val USER_NAME              = stringPreferencesKey("user_name")
        val FITNESS_LEVEL          = stringPreferencesKey("fitness_level")
        val PRIMARY_GOAL           = stringPreferencesKey("primary_goal")
        val WEIGHT_KG              = floatPreferencesKey("weight_kg")
        val HEIGHT_CM              = intPreferencesKey("height_cm")
        val SHOW_HERO_RINGS        = booleanPreferencesKey("show_hero_rings")
        val SHOW_AI_INSIGHT        = booleanPreferencesKey("show_ai_insight")
        val SHOW_UPCOMING_EVENTS   = booleanPreferencesKey("show_upcoming_events")
        val DAILY_REMINDERS        = booleanPreferencesKey("daily_reminders")
        val HYDRATION_REMINDER     = booleanPreferencesKey("hydration_reminder")
        val INACTIVITY_ALERTS      = booleanPreferencesKey("inactivity_alerts")
        val EVENING_REVIEW         = booleanPreferencesKey("evening_review")
        val AI_STYLE               = stringPreferencesKey("ai_style")
        val SYNC_FREQUENCY         = stringPreferencesKey("sync_frequency")
        val THEME_MODE             = stringPreferencesKey("theme_mode")
        val GEMINI_API_KEY         = stringPreferencesKey("gemini_api_key")
        val BEDTIME_REMINDER       = booleanPreferencesKey("bedtime_reminder_enabled")
        val WAKE_UP_HOUR           = intPreferencesKey("wake_up_hour")
        val WAKE_UP_MINUTE         = intPreferencesKey("wake_up_minute")
        val BEDTIME_MINUTES_BEFORE = intPreferencesKey("bedtime_minutes_before")
        val PROTEIN_TARGET         = floatPreferencesKey("protein_target")
        val CARBS_TARGET           = floatPreferencesKey("carbs_target")
        val FAT_TARGET             = floatPreferencesKey("fat_target")
    }

    val userGoals: Flow<UserGoals> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UserGoals(
                stepsTarget          = prefs[Keys.STEPS_TARGET] ?: 10000,
                sleepTargetHours     = prefs[Keys.SLEEP_TARGET] ?: 8.0f,
                caloriesTarget       = prefs[Keys.CALORIES_TARGET] ?: 2200f,
                workoutDaysTarget    = prefs[Keys.WORKOUT_DAYS_TARGET] ?: 4,
                workoutMinutesTarget = prefs[Keys.WORKOUT_MINUTES_TARGET] ?: 180,
                userName             = prefs[Keys.USER_NAME] ?: "Jaime",
                fitnessLevel         = prefs[Keys.FITNESS_LEVEL] ?: "INTERMEDIO",
                primaryGoal          = prefs[Keys.PRIMARY_GOAL] ?: "SALUD_GENERAL",
                weightKg             = prefs[Keys.WEIGHT_KG] ?: 75.0f,
                heightCm             = prefs[Keys.HEIGHT_CM] ?: 175,
                showHeroRings        = prefs[Keys.SHOW_HERO_RINGS] ?: true,
                showAiInsight        = prefs[Keys.SHOW_AI_INSIGHT] ?: true,
                showUpcomingEvents   = prefs[Keys.SHOW_UPCOMING_EVENTS] ?: true,
                dailyReminders       = prefs[Keys.DAILY_REMINDERS] ?: true,
                hydrationReminder    = prefs[Keys.HYDRATION_REMINDER] ?: true,
                inactivityAlerts     = prefs[Keys.INACTIVITY_ALERTS] ?: false,
                eveningReview        = prefs[Keys.EVENING_REVIEW] ?: true,
                aiStyle              = prefs[Keys.AI_STYLE] ?: "CIENTIFICO",
                syncFrequency        = prefs[Keys.SYNC_FREQUENCY] ?: "30_MIN",
                themeMode            = prefs[Keys.THEME_MODE] ?: "OSCURO",
                geminiApiKey         = prefs[Keys.GEMINI_API_KEY] ?: "",
                bedtimeReminderEnabled = prefs[Keys.BEDTIME_REMINDER] ?: true,
                wakeUpHour           = prefs[Keys.WAKE_UP_HOUR] ?: 8,
                wakeUpMinute         = prefs[Keys.WAKE_UP_MINUTE] ?: 30,
                bedtimeReminderMinutesBefore = prefs[Keys.BEDTIME_MINUTES_BEFORE] ?: 30,
                proteinTargetGrams   = prefs[Keys.PROTEIN_TARGET] ?: 140f,
                carbsTargetGrams     = prefs[Keys.CARBS_TARGET] ?: 250f,
                fatTargetGrams       = prefs[Keys.FAT_TARGET] ?: 70f
            )
        }

    suspend fun updateStepsTarget(target: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.STEPS_TARGET] = target }
    }

    suspend fun updateSleepTarget(targetHours: Float) {
        context.dataStore.edit { prefs -> prefs[Keys.SLEEP_TARGET] = targetHours }
    }

    suspend fun updateCaloriesTarget(targetCalories: Float) {
        context.dataStore.edit { prefs -> prefs[Keys.CALORIES_TARGET] = targetCalories }
    }

    suspend fun updateWorkoutDaysTarget(days: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.WORKOUT_DAYS_TARGET] = days }
    }

    suspend fun updateWorkoutMinutesTarget(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.WORKOUT_MINUTES_TARGET] = minutes }
    }

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { prefs -> prefs[Keys.USER_NAME] = name }
    }

    suspend fun updateFitnessProfile(level: String, goal: String, weight: Float, height: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FITNESS_LEVEL] = level
            prefs[Keys.PRIMARY_GOAL] = goal
            prefs[Keys.WEIGHT_KG] = weight
            prefs[Keys.HEIGHT_CM] = height
        }
    }

    suspend fun updateShowHeroRings(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SHOW_HERO_RINGS] = enabled }
    }

    suspend fun updateShowAiInsight(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SHOW_AI_INSIGHT] = enabled }
    }

    suspend fun updateShowUpcomingEvents(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SHOW_UPCOMING_EVENTS] = enabled }
    }

    suspend fun updateDailyReminders(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.DAILY_REMINDERS] = enabled }
    }

    suspend fun updateHydrationReminder(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.HYDRATION_REMINDER] = enabled }
    }

    suspend fun updateInactivityAlerts(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.INACTIVITY_ALERTS] = enabled }
    }

    suspend fun updateEveningReview(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.EVENING_REVIEW] = enabled }
    }

    suspend fun updateAiStyle(style: String) {
        context.dataStore.edit { prefs -> prefs[Keys.AI_STYLE] = style }
    }

    suspend fun updateSyncFrequency(freq: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SYNC_FREQUENCY] = freq }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode }
    }

    suspend fun updateGeminiApiKey(apiKey: String) {
        context.dataStore.edit { prefs -> prefs[Keys.GEMINI_API_KEY] = apiKey.trim() }
    }

    suspend fun updateBedtimeReminder(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.BEDTIME_REMINDER] = enabled }
    }

    suspend fun updateWakeUpTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WAKE_UP_HOUR] = hour
            prefs[Keys.WAKE_UP_MINUTE] = minute
        }
    }

    suspend fun updateBedtimeMinutesBefore(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.BEDTIME_MINUTES_BEFORE] = minutes }
    }

    suspend fun updateMacroTargets(protein: Float, carbs: Float, fat: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROTEIN_TARGET] = protein
            prefs[Keys.CARBS_TARGET] = carbs
            prefs[Keys.FAT_TARGET] = fat
        }
    }
}
