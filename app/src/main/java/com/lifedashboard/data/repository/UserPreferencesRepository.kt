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
    val userName: String = "Jaime"
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val STEPS_TARGET    = intPreferencesKey("steps_target")
        val SLEEP_TARGET    = floatPreferencesKey("sleep_target")
        val CALORIES_TARGET = floatPreferencesKey("calories_target")
        val USER_NAME       = stringPreferencesKey("user_name")
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
                stepsTarget      = prefs[Keys.STEPS_TARGET] ?: 10000,
                sleepTargetHours = prefs[Keys.SLEEP_TARGET] ?: 8.0f,
                caloriesTarget   = prefs[Keys.CALORIES_TARGET] ?: 2200f,
                userName         = prefs[Keys.USER_NAME] ?: "Jaime"
            )
        }

    suspend fun updateStepsTarget(target: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STEPS_TARGET] = target
        }
    }

    suspend fun updateSleepTarget(targetHours: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SLEEP_TARGET] = targetHours
        }
    }

    suspend fun updateCaloriesTarget(targetCalories: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CALORIES_TARGET] = targetCalories
        }
    }

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = name
        }
    }
}
