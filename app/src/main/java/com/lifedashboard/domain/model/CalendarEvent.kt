package com.lifedashboard.domain.model

import java.time.LocalDateTime

/**
 * Modelo de dominio para eventos de calendario.
 * Puro Kotlin — sin referencias a Android.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val isAllDay: Boolean,
    val calendarDisplayName: String,
    val calendarColor: Int,
    val eventColor: Int?
) {
    /** Duración en minutos */
    val durationMinutes: Long
        get() = java.time.Duration.between(startTime, endTime).toMinutes()

    /** ¿Es hoy? */
    fun isToday(): Boolean {
        val today = LocalDateTime.now().toLocalDate()
        return startTime.toLocalDate() == today
    }

    /** ¿Ocurre en los próximos N días? */
    fun isWithinNextDays(days: Int): Boolean {
        val now = LocalDateTime.now()
        val limit = now.plusDays(days.toLong())
        return startTime.isAfter(now) && startTime.isBefore(limit)
    }
}
