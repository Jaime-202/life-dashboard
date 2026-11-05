package com.lifedashboard.core.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

class SnoozeReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE_REMINDER = "com.lifedashboard.ACTION_SNOOZE_REMINDER"
        const val ACTION_SNOOZE_BEDTIME  = "com.lifedashboard.ACTION_SNOOZE_BEDTIME"
        private const val ACTION_TRIGGER_SNOOZED_BEDTIME = "com.lifedashboard.TRIGGER_SNOOZED_BEDTIME"
        const val SNOOZE_MINUTES = 15
        private const val BEDTIME_SNOOZE_REQUEST_CODE = 9988
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", 0)
        if (notificationId != 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60 * 1000L

        when (intent.action) {
            ACTION_SNOOZE_BEDTIME -> {
                val bedtimeIntent = Intent(context, SnoozeReminderReceiver::class.java).apply {
                    action = ACTION_TRIGGER_SNOOZED_BEDTIME
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    BEDTIME_SNOOZE_REQUEST_CODE,
                    bedtimeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                ReminderScheduler.setAlarm(context, triggerAt, pending)
                Toast.makeText(context, "Aviso de descanso pospuesto $SNOOZE_MINUTES min", Toast.LENGTH_SHORT).show()
            }
            ACTION_TRIGGER_SNOOZED_BEDTIME -> {
                NotificationHelper.showBedtimeReminder(
                    context = context,
                    title = "🌙 Hora de ir a dormir (Pospuesto)",
                    message = "Han pasado los $SNOOZE_MINUTES minutos. Es hora de desconectar para asegurar un sueño reparador."
                )
            }
            else -> {
                val reminderId = intent.getLongExtra("reminder_id", 0L)
                if (reminderId == 0L) return
                val title = intent.getStringExtra("title") ?: "Toma"
                val emoji = intent.getStringExtra("emoji") ?: "💊"

                ReminderScheduler.scheduleSnooze(context, reminderId, triggerAt)
                Toast.makeText(context, "Pospuesto $SNOOZE_MINUTES minutos: $emoji $title", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
