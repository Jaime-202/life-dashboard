package com.lifedashboard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room para eventos de calendario.
 * Actúa como caché local de lo leído desde CalendarContract.
 */
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey
    val id: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startTimeMillis: Long,      // UTC epoch millis
    val endTimeMillis: Long,
    val isAllDay: Boolean,
    val calendarDisplayName: String,
    val calendarColor: Int,
    val eventColor: Int?,
    val syncedAt: Long = System.currentTimeMillis()
)
