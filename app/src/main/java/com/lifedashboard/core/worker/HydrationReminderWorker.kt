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
        // Alerta de hidratación deshabilitada por el usuario
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "vital_hydration_reminder_work"
    }
}
