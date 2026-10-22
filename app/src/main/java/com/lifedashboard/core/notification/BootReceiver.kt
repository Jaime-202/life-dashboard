package com.lifedashboard.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.data.local.dao.ReminderDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun reminderDao(): ReminderDao
        fun workManagerScheduler(): WorkManagerScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "Reinicio o actualización detectada ($action). Reprogramando recordatorios y workers...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        BootEntryPoint::class.java
                    )
                    // 1. Programar workers periódicos
                    entryPoint.workManagerScheduler().scheduleAll()

                    // 2. Reprogramar recordatorios activos
                    val reminders = entryPoint.reminderDao().observeEnabled().first()
                    ReminderScheduler.rescheduleAll(context, reminders)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error en BootReceiver", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
