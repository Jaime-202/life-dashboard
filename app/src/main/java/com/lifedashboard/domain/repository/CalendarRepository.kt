package com.lifedashboard.domain.repository

import com.lifedashboard.domain.model.CalendarEvent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface CalendarRepository {
    /**
     * Devuelve todos los eventos en un rango de fechas.
     * Implementación usa CalendarContract — sin internet.
     */
    fun getEvents(
        from: LocalDate,
        to: LocalDate
    ): Flow<List<CalendarEvent>>

    /** Eventos de hoy */
    fun getTodayEvents(): Flow<List<CalendarEvent>>

    /** Próximos N eventos a partir de ahora */
    fun getUpcomingEvents(limit: Int = 5): Flow<List<CalendarEvent>>

    /** Fuerza re-lectura desde CalendarContract y actualiza Room */
    suspend fun syncCalendarEvents()
}
