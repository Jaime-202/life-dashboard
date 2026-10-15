package com.lifedashboard.domain.usecase.calendar

import com.lifedashboard.domain.model.CalendarEvent
import com.lifedashboard.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

/**
 * Use Case: obtiene eventos de calendario en un rango.
 *
 * La separación en un Use Case permite:
 * - Testear la lógica sin dependencias de Android
 * - Añadir transformaciones/filtros sin tocar el repositorio
 */
class GetCalendarEventsUseCase @Inject constructor(
    private val repository: CalendarRepository
) {
    /** Eventos en un rango de fechas personalizado */
    operator fun invoke(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> =
        repository.getEvents(from, to)

    /** Eventos de hoy */
    fun today(): Flow<List<CalendarEvent>> =
        repository.getTodayEvents()

    /** Próximos N eventos */
    fun upcoming(limit: Int = 5): Flow<List<CalendarEvent>> =
        repository.getUpcomingEvents(limit)

    /** Fuerza re-sincronización con CalendarContract */
    suspend fun sync(calendarRepository: CalendarRepository) =
        calendarRepository.syncCalendarEvents()
}
