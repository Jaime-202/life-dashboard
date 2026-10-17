package com.lifedashboard.core.worker

import android.content.Context
import androidx.work.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centraliza el registro y programación de todos los Workers.
 * Se llama desde MainActivity al arrancar la app y en onBoot.
 */
@Singleton
class WorkManagerScheduler @Inject constructor(
    private val context: Context
) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    /** Programa todos los workers si no están ya activos */
    fun scheduleAll(symmetryFolderUri: String? = null) {
        scheduleHealthConnectSync()
        scheduleWidgetUpdates()
        symmetryFolderUri?.let { scheduleSymmetrySync(it) }
        scheduleBedtimeReminder()
    }

    fun scheduleBedtimeReminder(
        wakeUpHour: Int = 8,
        wakeUpMinute: Int = 30,
        sleepTargetHours: Float = 8.0f,
        minutesBefore: Int = 30
    ) {
        val calendar = java.util.Calendar.getInstance()
        val nowMillis = calendar.timeInMillis

        val wakeUpMinutes = wakeUpHour * 60 + wakeUpMinute
        val sleepMinutes = (sleepTargetHours * 60).toInt()
        var bedtimeMinutes = (wakeUpMinutes - sleepMinutes) % 1440
        if (bedtimeMinutes < 0) bedtimeMinutes += 1440

        var reminderMinutes = (bedtimeMinutes - minutesBefore) % 1440
        if (reminderMinutes < 0) reminderMinutes += 1440

        val reminderHour = reminderMinutes / 60
        val reminderMinute = reminderMinutes % 60

        val targetCalendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, reminderHour)
            set(java.util.Calendar.MINUTE, reminderMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        if (targetCalendar.timeInMillis <= nowMillis) {
            targetCalendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelayMillis = targetCalendar.timeInMillis - nowMillis

        val request = OneTimeWorkRequestBuilder<BedtimeReminderWorker>()
            .setInitialDelay(initialDelayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            BedtimeReminderWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelBedtimeReminder() {
        workManager.cancelUniqueWork(BedtimeReminderWorker.WORK_NAME)
    }

    fun scheduleHealthConnectSync() {
        workManager.enqueueUniquePeriodicWork(
            HealthConnectSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            HealthConnectSyncWorker.buildRequest()
        )
    }

    fun scheduleWidgetUpdates() {
        workManager.enqueueUniquePeriodicWork(
            WidgetUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            WidgetUpdateWorker.buildPeriodicRequest()
        )
    }

    fun scheduleSymmetrySync(folderUri: String) {
        workManager.enqueueUniquePeriodicWork(
            SymmetrySyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,  // Actualiza la URI si cambia
            SymmetrySyncWorker.buildRequest(folderUri)
        )
    }

    /** Fuerza ejecución inmediata de Health Connect y actualiza los widgets al instante */
    fun runHealthSyncNow() {
        val syncRequest = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
        val widgetRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
        workManager.beginWith(syncRequest)
            .then(widgetRequest)
            .enqueue()
    }

    fun cancelAll() {
        workManager.cancelAllWork()
    }
}
