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
import java.util.Calendar

@HiltWorker
class HydrationReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferences: UserPreferencesRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val goals = userPreferences.userGoals.first()
            if (!goals.hydrationReminder) {
                return Result.success()
            }

            // Solo notificar en horario diurno (entre 09:00 y 21:59)
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour in 9..21) {
                NotificationHelper.showHydrationReminder(
                    context = context,
                    title = "💧 Momento de Hidratarte",
                    message = "Bebe un vaso de agua para mantener tu metabolismo activo, hidratación y energía."
                )
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("HydrationWorker", "Error ejecutando recordatorio de hidratación", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "vital_hydration_reminder_work"
    }
}
