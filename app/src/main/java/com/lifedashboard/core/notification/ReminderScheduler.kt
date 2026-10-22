package com.lifedashboard.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.lifedashboard.data.local.entity.ReminderEntity
import com.lifedashboard.data.local.entity.ReminderType
import java.util.Calendar

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    /**
     * Programa o actualiza la alarma exacta en AlarmManager para un recordatorio.
     */
    fun scheduleReminder(context: Context, reminder: ReminderEntity) {
        if (!reminder.isEnabled) {
            cancelReminder(context, reminder.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Extraer hora y minuto de "HH:mm"
        val parts = reminder.timeOfDay.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = System.currentTimeMillis()

        when (reminder.type) {
            ReminderType.SEMANAL -> {
                val activeDays = reminder.daysOfWeek
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toSet()

                if (activeDays.isNotEmpty()) {
                    var attempts = 0
                    while (attempts < 14) {
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
                        if (activeDays.contains(dayOfWeekIso) && calendar.timeInMillis > now) {
                            break
                        }
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        attempts++
                    }
                } else {
                    if (calendar.timeInMillis <= now) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }
            else -> { // DIARIO o HORARIO
                if (calendar.timeInMillis <= now) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("title", reminder.title)
            putExtra("emoji", reminder.iconEmoji)
            putExtra("time_of_day", reminder.timeOfDay)
            putExtra("type", reminder.type)
            putExtra("days_of_week", reminder.daysOfWeek)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Alarma programada con éxito para '${reminder.title}' (${reminder.timeOfDay}) en ${calendar.time}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Permiso de alarma exacta no concedido, recurriendo a alarma estándar: ${e.message}")
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Error programando alarma", e2)
            }
        }
    }

    /**
     * Cancela la alarma pendiente asociada al ID del recordatorio.
     */
    fun cancelReminder(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Alarma cancelada para reminderId=$reminderId")
    }

    /**
     * Reprograma todos los recordatorios activos (por ejemplo tras reinicio o actualización).
     */
    fun rescheduleAll(context: Context, reminders: List<ReminderEntity>) {
        for (reminder in reminders) {
            if (reminder.isEnabled) {
                scheduleReminder(context, reminder)
            } else {
                cancelReminder(context, reminder.id)
            }
        }
    }
}
