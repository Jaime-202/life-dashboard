package com.lifedashboard.core.worker

import android.content.Context
import androidx.work.*
import com.lifedashboard.data.repository.UserGoals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado de una sincronización solicitada por el usuario. */
enum class HealthSyncOutcome(val message: String) {
    SUCCESS("Datos de salud actualizados"),
    UNAVAILABLE("Health Connect no está disponible en este dispositivo"),
    NO_PERMISSIONS("Faltan permisos: conéctalo en Config > Health Connect"),
    FAILED("No se pudieron leer los datos de Health Connect"),
    TIMED_OUT("La sincronización sigue en curso; se actualizará al terminar")
}

/**
 * Centraliza el registro y programación de todos los Workers.
 * Se llama desde MainActivity al arrancar la app y en onBoot.
 */
@Singleton
class WorkManagerScheduler @Inject constructor(
    private val context: Context
) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    /**
     * Programa todos los workers respetando las preferencias del usuario: horas configuradas,
     * avisos desactivados y frecuencia de sincronización (incluido el modo manual).
     */
    fun scheduleAll(goals: UserGoals, symmetryFolderUri: String? = null) {
        scheduleHealthConnectSync(goals.syncFrequency)
        scheduleWidgetUpdates()
        symmetryFolderUri?.let { scheduleSymmetrySync(it) }
        syncBedtimeReminder(goals)
        if (goals.dailyReminders) {
            scheduleDailySummaryReminder(goals.wakeUpHour, goals.wakeUpMinute)
        } else {
            cancelDailySummaryReminder()
        }
        if (goals.eveningReview) scheduleEveningReviewReminder() else cancelEveningReviewReminder()
        cancelHydrationReminder()
    }

    /** Alinea el aviso de ir a dormir con los ajustes actuales. */
    fun syncBedtimeReminder(goals: UserGoals) {
        if (goals.bedtimeReminderEnabled) {
            scheduleBedtimeReminder(
                wakeUpHour = goals.wakeUpHour,
                wakeUpMinute = goals.wakeUpMinute,
                sleepTargetHours = goals.sleepTargetHours,
                minutesBefore = goals.bedtimeReminderMinutesBefore
            )
        } else {
            cancelBedtimeReminder()
        }
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

    fun scheduleDailySummaryReminder(
        wakeUpHour: Int = 8,
        wakeUpMinute: Int = 30
    ) {
        val calendar = java.util.Calendar.getInstance()
        val nowMillis = calendar.timeInMillis

        val targetCalendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, wakeUpHour)
            set(java.util.Calendar.MINUTE, wakeUpMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        if (targetCalendar.timeInMillis <= nowMillis) {
            targetCalendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelayMillis = targetCalendar.timeInMillis - nowMillis

        val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setInitialDelay(initialDelayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            DailySummaryWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelDailySummaryReminder() {
        workManager.cancelUniqueWork(DailySummaryWorker.WORK_NAME)
    }

    fun scheduleEveningReviewReminder(
        hour: Int = 21,
        minute: Int = 30
    ) {
        val calendar = java.util.Calendar.getInstance()
        val nowMillis = calendar.timeInMillis

        val targetCalendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        if (targetCalendar.timeInMillis <= nowMillis) {
            targetCalendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelayMillis = targetCalendar.timeInMillis - nowMillis

        val request = OneTimeWorkRequestBuilder<EveningReviewWorker>()
            .setInitialDelay(initialDelayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            EveningReviewWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelEveningReviewReminder() {
        workManager.cancelUniqueWork(EveningReviewWorker.WORK_NAME)
    }

    fun scheduleHydrationReminder() {
        // Desactivado: no programar avisos de hidratación
        cancelHydrationReminder()
    }

    fun cancelHydrationReminder() {
        workManager.cancelUniqueWork(HydrationReminderWorker.WORK_NAME)
    }

    fun scheduleHealthConnectSync(frequency: String = "30_MIN") {
        if (frequency == "MANUAL") {
            workManager.cancelUniqueWork(HealthConnectSyncWorker.WORK_NAME)
            return
        }

        val intervalMinutes = when (frequency) {
            "15_MIN" -> 15L
            "30_MIN" -> 30L
            "60_MIN" -> 60L
            else -> 30L
        }

        workManager.enqueueUniquePeriodicWork(
            HealthConnectSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            HealthConnectSyncWorker.buildRequest(intervalMinutes)
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

    /** Lanza una sincronización inmediata; si ya hay una en curso no se encola otra. */
    fun runHealthSyncNow() {
        workManager.enqueueUniqueWork(
            HealthConnectSyncWorker.WORK_NAME_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
        )
    }

    /** Sincroniza y espera al resultado real, para informar al usuario en vez de simular un progreso. */
    suspend fun syncHealthNowAndAwait(timeoutMillis: Long = 45_000L): HealthSyncOutcome {
        workManager.enqueueUniqueWork(
            HealthConnectSyncWorker.WORK_NAME_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
        ).await()

        val info = withTimeoutOrNull(timeoutMillis) {
            workManager.getWorkInfosForUniqueWorkFlow(HealthConnectSyncWorker.WORK_NAME_NOW)
                .map { infos -> infos.firstOrNull() }
                .first { it?.state?.isFinished == true }
        } ?: return HealthSyncOutcome.TIMED_OUT

        if (info.state == WorkInfo.State.SUCCEEDED) return HealthSyncOutcome.SUCCESS
        return when (info.outputData.getString(HealthConnectSyncWorker.KEY_ERROR)) {
            HealthConnectSyncWorker.ERROR_UNAVAILABLE -> HealthSyncOutcome.UNAVAILABLE
            HealthConnectSyncWorker.ERROR_NO_PERMISSIONS -> HealthSyncOutcome.NO_PERMISSIONS
            else -> HealthSyncOutcome.FAILED
        }
    }

    fun cancelAll() {
        workManager.cancelAllWork()
    }
}
