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
    val userName: String = "Jaime",
    val showHeroRings: Boolean = true,
    val showAiInsight: Boolean = true,
    val showUpcomingEvents: Boolean = true,
    val dailyReminders: Boolean = true,
    val themeMode: String = "OSCURO",
    val geminiApiKey: String = ""
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val STEPS_TARGET         = intPreferencesKey("steps_target")
        val SLEEP_TARGET         = floatPreferencesKey("sleep_target")
        val CALORIES_TARGET      = floatPreferencesKey("calories_target")
        val USER_NAME            = stringPreferencesKey("user_name")
        val SHOW_HERO_RINGS      = booleanPreferencesKey("show_hero_rings")
        val SHOW_AI_INSIGHT      = booleanPreferencesKey("show_ai_insight")
        val SHOW_UPCOMING_EVENTS = booleanPreferencesKey("show_upcoming_events")
        val DAILY_REMINDERS      = booleanPreferencesKey("daily_reminders")
        val THEME_MODE           = stringPreferencesKey("theme_mode")
        val GEMINI_API_KEY       = stringPreferencesKey("gemini_api_key")
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
                stepsTarget        = prefs[Keys.STEPS_TARGET] ?: 10000,
                sleepTargetHours   = prefs[Keys.SLEEP_TARGET] ?: 8.0f,
                caloriesTarget     = prefs[Keys.CALORIES_TARGET] ?: 2200f,
                userName           = prefs[Keys.USER_NAME] ?: "Jaime",
                showHeroRings      = prefs[Keys.SHOW_HERO_RINGS] ?: true,
                showAiInsight      = prefs[Keys.SHOW_AI_INSIGHT] ?: true,
                showUpcomingEvents = prefs[Keys.SHOW_UPCOMING_EVENTS] ?: true,
                dailyReminders     = prefs[Keys.DAILY_REMINDERS] ?: true,
                themeMode          = prefs[Keys.THEME_MODE] ?: "OSCURO",
                geminiApiKey       = prefs[Keys.GEMINI_API_KEY] ?: ""
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

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { prefs -> prefs[Keys.USER_NAME] = name }
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

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode }
    }

    suspend fun updateGeminiApiKey(apiKey: String) {
        context.dataStore.edit { prefs -> prefs[Keys.GEMINI_API_KEY] = apiKey.trim() }
    }
}
