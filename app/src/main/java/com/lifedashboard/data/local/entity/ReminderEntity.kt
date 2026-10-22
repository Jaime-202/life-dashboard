package com.lifedashboard.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Frecuencias soportadas para las tomas/recordatorios:
 * - DIARIO: Todos los días
 * - SEMANAL: Días específicos de la semana (1 = Lunes ... 7 = Domingo, almacenados como "1,3,5")
 * - HORARIO: A una hora específica del día (almacenado como "HH:mm", ej. "08:30")
 */
object ReminderType {
    const val DIARIO = "DIARIO"
    const val SEMANAL = "SEMANAL"
    const val HORARIO = "HORARIO"
}

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: String = ReminderType.DIARIO, // "DIARIO", "SEMANAL", "HORARIO"
    val daysOfWeek: String = "",           // Ej: "1,2,3,4,5" (1=Mon, 7=Sun)
    val timeOfDay: String = "09:00",       // Formato "HH:mm"
    val iconEmoji: String = "💊",          // Emoji visual
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reminder_logs",
    indices = [
        Index(value = ["reminderId", "dateMillis"], unique = true),
        Index(value = ["dateMillis"])
    ]
)
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reminderId: Long,
    val dateMillis: Long,               // Inicio de día en millis
    val isCompleted: Boolean = false,
    val completedAt: Long = 0L
)
