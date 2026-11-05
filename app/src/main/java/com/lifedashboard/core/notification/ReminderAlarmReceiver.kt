package com.lifedashboard.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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

class ReminderAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderAlarmEntryPoint {
        fun reminderDao(): ReminderDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = ReminderScheduler.reminderIdFrom(intent)
        if (reminderId == 0L) return
        val isSnooze = ReminderScheduler.isSnoozeIntent(intent)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminderDao = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ReminderAlarmEntryPoint::class.java
                ).reminderDao()

                // La base de datos manda: el recordatorio puede haberse editado o borrado
                val reminder = reminderDao.getById(reminderId)
                if (reminder == null || !reminder.isEnabled) {
                    ReminderScheduler.cancelReminder(context, reminderId)
                    return@launch
                }

                val today = LocalDate.now()
                val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val alreadyDone = reminderDao.getLog(reminderId, startOfDay)?.isCompleted == true
                val appliesToday = isSnooze || ReminderScheduler.appliesOnDay(
                    reminder.type,
                    reminder.daysOfWeek,
                    today.dayOfWeek.value
                )

                if (appliesToday && !alreadyDone) {
                    NotificationHelper.showCustomReminder(
                        context = context,
                        reminderId = reminderId,
                        title = reminder.title,
                        iconEmoji = reminder.iconEmoji,
                        timeOfDay = reminder.timeOfDay
                    )
                }

                // Un aplazamiento no toca la programación: la alarma habitual sigue en pie
                if (!isSnooze) ReminderScheduler.scheduleReminder(context, reminder)
            } catch (e: Exception) {
                Log.e("ReminderAlarmReceiver", "Error procesando el recordatorio #$reminderId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
