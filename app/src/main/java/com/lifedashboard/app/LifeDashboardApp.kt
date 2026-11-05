package com.lifedashboard.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lifedashboard.BuildConfig
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
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()
}
