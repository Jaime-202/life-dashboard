package com.lifedashboard.core.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifedashboard.core.notification.NotificationHelper
import com.lifedashboard.data.repository.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Locale

@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferences: UserPreferencesRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val goals = userPreferences.userGoals.first()
            if (!goals.dailyReminders) {
                return Result.success()
            }

            val name = goals.userName.ifBlank { "Campeón" }
            val steps = goals.stepsTarget
            val activeKcal = goals.activeCaloriesTarget.toInt()

            val title = "☀️ ¡Buenos días, $name!"
            val message = String.format(
                Locale.getDefault(),
                "Tus objetivos de hoy: %d pasos y %d kcal activas. ¡Mantén tu constancia y a por el día!",
                steps,
                activeKcal
            )

            NotificationHelper.showDailySummary(context, title, message)

            // Reprogramar para la mañana siguiente
            WorkManagerScheduler(context).scheduleDailySummaryReminder(
                wakeUpHour = goals.wakeUpHour,
                wakeUpMinute = goals.wakeUpMinute
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando resumen matutino", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "vital_daily_summary_work"
        private const val TAG = "DailySummaryWorker"
    }
}
