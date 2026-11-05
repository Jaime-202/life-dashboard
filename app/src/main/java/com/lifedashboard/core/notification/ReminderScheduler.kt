package com.lifedashboard.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.lifedashboard.data.local.entity.ReminderEntity
import com.lifedashboard.data.local.entity.ReminderType
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    private const val ACTION_FIRE_SNOOZED = "com.lifedashboard.FIRE_SNOOZED_REMINDER"
    private const val EXTRA_REMINDER_ID = "reminder_id"
    private const val MAX_DAYS_AHEAD = 8L

    /**
     * Próxima vez que debe sonar un recordatorio, estrictamente posterior a [now].
     * Los días de la semana usan ISO (1 = lunes ... 7 = domingo).
     */
    fun nextOccurrence(
        type: String,
        daysOfWeek: String,
        timeOfDay: String,
        now: LocalDateTime
    ): LocalDateTime {
        val parts = timeOfDay.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
        val activeDays = if (type == ReminderType.SEMANAL) parseDays(daysOfWeek) else emptySet()

        val today = now.toLocalDate().atTime(hour, minute)
        for (offset in 0..MAX_DAYS_AHEAD) {
            val candidate = today.plusDays(offset)
            val dayMatches = activeDays.isEmpty() || candidate.dayOfWeek.value in activeDays
            if (dayMatches && candidate.isAfter(now)) return candidate
        }
        return today.plusDays(1)
    }

    /** Indica si un recordatorio se aplica en un día concreto (1 = lunes ... 7 = domingo). */
    fun appliesOnDay(type: String, daysOfWeek: String, isoDayOfWeek: Int): Boolean {
        if (type != ReminderType.SEMANAL) return true
        val days = parseDays(daysOfWeek)
        return days.isEmpty() || isoDayOfWeek in days
    }

    private fun parseDays(daysOfWeek: String): Set<Int> =
        daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

    /**
     * Programa o actualiza la alarma de un recordatorio. Los datos del aviso se leen de la base de
     * datos al dispararse, así que aquí basta con identificar el recordatorio.
     */
    fun scheduleReminder(context: Context, reminder: ReminderEntity) {
        if (!reminder.isEnabled) {
            cancelReminder(context, reminder.id)
            return
        }

        val trigger = nextOccurrence(
            reminder.type,
            reminder.daysOfWeek,
            reminder.timeOfDay,
            LocalDateTime.now()
        )
        val triggerAtMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        setAlarm(context, triggerAtMillis, regularPendingIntent(context, reminder.id))
        Log.d(TAG, "Alarma programada para el recordatorio #${reminder.id} en $trigger")
    }

    /** Vuelve a avisar de un recordatorio dentro de un rato sin alterar su programación habitual. */
    fun scheduleSnooze(context: Context, reminderId: Long, triggerAtMillis: Long) {
        setAlarm(context, triggerAtMillis, snoozePendingIntent(context, reminderId))
    }

    /**
     * Programa una alarma exacta si el sistema lo permite y, si no, una aproximada:
     * mejor un aviso con unos minutos de margen que ninguno.
     */
    fun setAlarm(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canBeExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Alarma exacta denegada, se usa una aproximada: ${e.message}")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    /** Cancela tanto la alarma habitual como un posible aplazamiento pendiente. */
    fun cancelReminder(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(regularPendingIntent(context, reminderId))
        alarmManager.cancel(snoozePendingIntent(context, reminderId))
        Log.d(TAG, "Alarma cancelada para reminderId=$reminderId")
    }

    fun cancelSnooze(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(snoozePendingIntent(context, reminderId))
    }

    /** Reprograma todos los recordatorios activos (por ejemplo tras reinicio o actualización). */
    fun rescheduleAll(context: Context, reminders: List<ReminderEntity>) {
        for (reminder in reminders) {
            if (reminder.isEnabled) {
                scheduleReminder(context, reminder)
            } else {
                cancelReminder(context, reminder.id)
            }
        }
    }

    fun isSnoozeIntent(intent: Intent): Boolean = intent.action == ACTION_FIRE_SNOOZED

    fun reminderIdFrom(intent: Intent): Long = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)

    private fun regularPendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // La acción distinta impide que el aplazamiento sustituya a la alarma habitual
    private fun snoozePendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_SNOOZED
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
