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
        symmetryFolderUri?.let { scheduleSymmetrySync(it) }
    }

    fun scheduleHealthConnectSync() {
        workManager.enqueueUniquePeriodicWork(
            HealthConnectSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,    // No reinicia si ya existe
            HealthConnectSyncWorker.buildRequest()
        )
    }

    fun scheduleSymmetrySync(folderUri: String) {
        workManager.enqueueUniquePeriodicWork(
            SymmetrySyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,  // Actualiza la URI si cambia
            SymmetrySyncWorker.buildRequest(folderUri)
        )
    }

    /** Fuerza ejecución inmediata de Health Connect (ej. al abrir la app) */
    fun runHealthSyncNow() {
        val oneTimeRequest = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
        workManager.enqueue(oneTimeRequest)
    }

    fun cancelAll() {
        workManager.cancelAllWork()
    }
}
