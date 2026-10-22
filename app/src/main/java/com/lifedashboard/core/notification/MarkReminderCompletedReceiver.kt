package com.lifedashboard.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.lifedashboard.core.worker.WidgetUpdateWorker
import com.lifedashboard.data.local.dao.ReminderDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MarkReminderCompletedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MarkReminderEntryPoint {
        fun reminderDao(): ReminderDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", 0L)
        val notificationId = intent.getIntExtra("notification_id", 0)

        if (notificationId != 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        if (reminderId != 0L) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        MarkReminderEntryPoint::class.java
                    )
                    val reminderDao = entryPoint.reminderDao()

                    val zone = ZoneId.systemDefault()
                    val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
                    reminderDao.toggleCompletion(reminderId, startOfDay, isCompleted = true)

                    WidgetUpdateWorker.updateAllWidgets(context)
                } catch (e: Exception) {
                    android.util.Log.e("MarkReminderReceiver", "Error marcando recordatorio completado", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
