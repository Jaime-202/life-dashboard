package com.lifedashboard.core.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lifedashboard.presentation.widget.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "WidgetUpdateWorker"

@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            updateAllWidgets(context)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error en auto-actualización de widgets", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "widget_auto_update_periodic"

        /**
         * Actualiza directamente todas las instancias de widgets de Glance y emite
         * el broadcast de actualización con sus IDs exactos al AppWidgetManager.
         */
        suspend fun updateAllWidgets(context: Context) {
            withContext(Dispatchers.IO) {
                try {
                    // 1. Actualización nativa de Glance
                    LifeDashboard1x1Widget().updateAll(context)
                    LifeDashboard1x2Widget().updateAll(context)
                    LifeDashboard2x2Widget().updateAll(context)
                    LifeDashboardWidget().updateAll(context)
                    Log.d(TAG, "Widgets de Glance actualizados con updateAll()")
                } catch (e: Exception) {
                    Log.w(TAG, "Fallo updateAll() en Glance: ${e.message}")
                }

                // 2. Notificación explícita de broadcast al AppWidgetManager con IDs de widget reales
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val receivers = listOf(
                        LifeDashboard1x1WidgetReceiver::class.java,
                        LifeDashboard1x2WidgetReceiver::class.java,
                        LifeDashboard2x2WidgetReceiver::class.java,
                        LifeDashboardWidgetReceiver::class.java
                    )
                    receivers.forEach { receiverClass ->
                        val component = ComponentName(context, receiverClass)
                        val ids = appWidgetManager.getAppWidgetIds(component)
                        if (ids.isNotEmpty()) {
                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                setComponent(component)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                            }
                            context.sendBroadcast(intent)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fallo al enviar broadcast de AppWidgetManager: ${e.message}")
                }
            }
        }

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()
    }
}
