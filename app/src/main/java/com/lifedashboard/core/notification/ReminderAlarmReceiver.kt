package com.lifedashboard.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lifedashboard.data.local.entity.ReminderEntity
import com.lifedashboard.data.local.entity.ReminderType
import java.util.Calendar

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", 0L)
        if (reminderId == 0L) return

        val title = intent.getStringExtra("title") ?: "Recordatorio"
        val emoji = intent.getStringExtra("emoji") ?: "💊"
        val timeOfDay = intent.getStringExtra("time_of_day") ?: "09:00"
        val type = intent.getStringExtra("type") ?: ReminderType.DIARIO
        val daysOfWeek = intent.getStringExtra("days_of_week") ?: ""

        Log.d("ReminderAlarmReceiver", "Disparando aviso para #$reminderId: $emoji $title")

        // Verificar si hoy corresponde disparar (en caso de recordatorio semanal)
        var shouldNotify = true
        if (type == ReminderType.SEMANAL && daysOfWeek.isNotBlank()) {
            val activeDays = daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            val calendar = Calendar.getInstance()
            val dayOfWeekIso = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
            shouldNotify = activeDays.contains(dayOfWeekIso)
        }

        if (shouldNotify) {
            NotificationHelper.showCustomReminder(
                context = context,
                reminderId = reminderId,
                title = title,
                iconEmoji = emoji,
                timeOfDay = timeOfDay
            )
        }

        // Programar la próxima ocurrencia
        val reminder = ReminderEntity(
            id = reminderId,
            title = title,
            type = type,
            daysOfWeek = daysOfWeek,
            timeOfDay = timeOfDay,
            iconEmoji = emoji,
            isEnabled = true
        )
        ReminderScheduler.scheduleReminder(context, reminder)
    }
}
