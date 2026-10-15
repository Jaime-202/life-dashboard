package com.lifedashboard.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class.
 *
 * Implementa [Configuration.Provider] para que WorkManager use
 * HiltWorkerFactory — esto es OBLIGATORIO para inyectar dependencias
 * en Workers con @HiltWorker.
 */
@HiltAndroidApp
class LifeDashboardApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        try {
            WorkManager.initialize(this, workManagerConfiguration)
        } catch (e: Exception) {
            // Continuar si ya está inicializado por otra vía
        }
    }
}
