package com.lifedashboard.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifedashboard.R
import com.lifedashboard.presentation.MainActivity

object NotificationHelper {

    const val CHANNEL_BEDTIME_ID = "vital_bedtime_channel"
    private const val CHANNEL_BEDTIME_NAME = "Aviso de Descanso y Sueño"
    private const val CHANNEL_BEDTIME_DESC = "Notificaciones inteligentes para ir a dormir a tiempo"

    const val CHANNEL_REMINDERS_ID = "vital_reminders_channel"
    private const val CHANNEL_REMINDERS_NAME = "Tomas y Recordatorios de Salud"
    private const val CHANNEL_REMINDERS_DESC = "Avisos puntuales de suplementos, tomas y hábitos configurados"

    const val CHANNEL_HYDRATION_ID = "vital_hydration_channel"
    private const val CHANNEL_HYDRATION_NAME = "Alertas de Hidratación"
    private const val CHANNEL_HYDRATION_DESC = "Recordatorios periódicos para beber agua y pausas activas"

    const val CHANNEL_DAILY_ID = "vital_daily_channel"
    private const val CHANNEL_DAILY_NAME = "Resumen Diario y Metas"
    private const val CHANNEL_DAILY_DESC = "Notificación matutina con el resumen de tus objetivos diarios"

    const val NOTIFICATION_BEDTIME_ID = 1001
    const val NOTIFICATION_HYDRATION_ID = 1002
    const val NOTIFICATION_DAILY_ID = 1003
    const val NOTIFICATION_TEST_ID = 9999

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Canal Sueño
            val bedtimeChannel = NotificationChannel(
                CHANNEL_BEDTIME_ID,
                CHANNEL_BEDTIME_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_BEDTIME_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(bedtimeChannel)

            // 2. Canal Tomas / Recordatorios
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS_ID,
                CHANNEL_REMINDERS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_REMINDERS_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(remindersChannel)

            // 3. Eliminar canal Hidratación si existía
            try {
                manager.deleteNotificationChannel(CHANNEL_HYDRATION_ID)
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_HYDRATION_ID)
            } catch (ignored: Exception) {}

            // 4. Canal Resumen Diario
            val dailyChannel = NotificationChannel(
                CHANNEL_DAILY_ID,
                CHANNEL_DAILY_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DAILY_DESC
                enableVibration(false)
                setShowBadge(true)
            }
            manager.createNotificationChannel(dailyChannel)
        }
    }

    private fun hasPostNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /**
     * Muestra la notificación de una toma o hábito personalizado con botón de acción directa.
     */
    fun showCustomReminder(
        context: Context,
        reminderId: Long,
        title: String,
        iconEmoji: String,
        timeOfDay: String
    ) {
        createChannels(context)
        if (!hasPostNotificationPermission(context)) return

        val notificationId = (20000 + (reminderId % 50000)).toInt()

        // Intent para abrir la aplicación al tocar la notificación
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("screen", "dashboard")
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción directa "✔ Marcar como tomado" sin abrir la app
        val markIntent = Intent(context, MarkReminderCompletedReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra("notification_id", notificationId)
        }
        val markPendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            markIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$iconEmoji $title")
            .setContentText("Toma programada a las $timeOfDay. Toca para verla o márcala directamente.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Es la hora de tu toma de $title ($timeOfDay). ¡Mantén tu constancia para cumplir tus metas de salud!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "✔ Marcar como tomado",
                    markPendingIntent
                ).build()
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permiso denegado por el usuario
        }
    }

    /**
     * Envía una notificación de prueba para verificar que el sistema de avisos y sonido funciona.
     */
    fun showTestReminderNotification(context: Context) {
        createChannels(context)
        if (!hasPostNotificationPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🔔 ¡Notificaciones de Life Dashboard Activas!")
            .setContentText("Tu teléfono está listo para avisarte de tus tomas, suplementos y descansos a la hora exacta.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("✅ El sistema de notificaciones funciona a la perfección. Recibirás avisos puntuales con sonido y opción para marcar tus tomas completadas.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TEST_ID, notification)
        } catch (e: SecurityException) {
            // Permiso denegado por el usuario
        }
    }

    /**
     * Alerta periódica de hidratación deshabilitada.
     */
    fun showHydrationReminder(context: Context, title: String, message: String) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_HYDRATION_ID)
    }

    /**
     * Resumen matutino diario con tus objetivos fijados.
     */
    fun showDailySummary(context: Context, title: String, message: String) {
        createChannels(context)
        if (!hasPostNotificationPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_DAILY_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_DAILY_ID, notification)
        } catch (e: SecurityException) {
            // Permiso denegado
        }
    }

    fun showBedtimeReminder(context: Context, title: String, message: String) {
        createChannels(context)
        if (!hasPostNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BEDTIME_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_BEDTIME_ID, notification)
        } catch (e: SecurityException) {
            // Permiso denegado por el usuario
        }
    }
}
