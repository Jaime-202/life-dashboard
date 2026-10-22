package com.lifedashboard.core.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifedashboard.core.notification.NotificationHelper
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.repository.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@HiltWorker
class EveningReviewWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferences: UserPreferencesRepository,
    private val healthDao: HealthDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val goals = userPreferences.userGoals.first()
            if (!goals.eveningReview) {
                return Result.success()
            }

            val today = LocalDate.now()
            val todayMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val healthData = healthDao.getHealthForDay(todayMillis)

            val steps = healthData?.steps ?: 0
            val stepsTarget = goals.stepsTarget
            val percent = if (stepsTarget > 0) ((steps.toFloat() / stepsTarget) * 100).toInt() else 0

            val title = "🌙 Resumen Nocturno de Recuperación"
            val message = if (steps > 0) {
                String.format(
                    Locale.getDefault(),
                    "Hoy registraste %,d pasos (%d%% de tu meta diaria). ¡Excelente trabajo! Desconecta y prepárate para un descanso reparador.",
                    steps,
                    percent
                )
            } else {
                "Es momento de desacelerar, atenuar las pantallas y relajarte. ¡Un descanso óptimo es la clave para rendir mañana!"
            }

            NotificationHelper.showDailySummary(context, title, message)

            // Reprogramar para la noche siguiente a las 21:30
            WorkManagerScheduler(context).scheduleEveningReviewReminder()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando revisión nocturna", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "vital_evening_review_work"
        private const val TAG = "EveningReviewWorker"
    }
}
