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
class BedtimeReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferences: UserPreferencesRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val goals = userPreferences.userGoals.first()
            if (!goals.bedtimeReminderEnabled) {
                return Result.success()
            }

            val wakeUpHour = goals.wakeUpHour
            val wakeUpMinute = goals.wakeUpMinute
            val sleepHours = goals.sleepTargetHours
            val minutesBefore = goals.bedtimeReminderMinutesBefore

            // Minutos desde medianoche para despertar
            val wakeUpMinutesFromMidnight = wakeUpHour * 60 + wakeUpMinute
            // Minutos de sueño objetivo
            val sleepMinutesNeeded = (sleepHours * 60).toInt()
            // Hora calculada de acostarse
            var bedtimeMinutesFromMidnight = (wakeUpMinutesFromMidnight - sleepMinutesNeeded) % 1440
            if (bedtimeMinutesFromMidnight < 0) {
                bedtimeMinutesFromMidnight += 1440
            }

            val bedtimeHour = bedtimeMinutesFromMidnight / 60
            val bedtimeMinute = bedtimeMinutesFromMidnight % 60

            val title = "🌙 Hora de desconectar (Vital)"
            val message = String.format(
                Locale.getDefault(),
                "Para levantarte a las %02d:%02d con %.1fh de descanso, deberías dormirte a las %02d:%02d. ¡Prepárate para descansar en %d min!",
                wakeUpHour,
                wakeUpMinute,
                sleepHours,
                bedtimeHour,
                bedtimeMinute,
                minutesBefore
            )

            NotificationHelper.showBedtimeReminder(context, title, message)

            // Reprogramar para la noche siguiente
            WorkManagerScheduler(context).scheduleBedtimeReminder(
                wakeUpHour = wakeUpHour,
                wakeUpMinute = wakeUpMinute,
                sleepTargetHours = sleepHours,
                minutesBefore = minutesBefore
            )

            Result.success()
        } catch (e: Exception) {
            Log.e("BedtimeReminderWorker", "Error executing bedtime reminder", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "vital_bedtime_reminder_work"
    }
}
