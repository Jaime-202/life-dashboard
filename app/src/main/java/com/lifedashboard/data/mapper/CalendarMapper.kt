package com.lifedashboard.data.mapper

import com.lifedashboard.data.local.entity.CalendarEventEntity
import com.lifedashboard.domain.model.CalendarEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Mappers entre Entity (Room) y Model (Domain).
 * Separados del repositorio para mantener SRP.
 */

fun CalendarEventEntity.toCalendarEvent(): CalendarEvent {
    val zone = ZoneId.systemDefault()
    return CalendarEvent(
        id                   = id,
        title                = title,
        description          = description,
        location             = location,
        startTime            = LocalDateTime.ofInstant(
                                   Instant.ofEpochMilli(startTimeMillis), zone
                               ),
        endTime              = LocalDateTime.ofInstant(
                                   Instant.ofEpochMilli(endTimeMillis), zone
                               ),
        isAllDay             = isAllDay,
        calendarDisplayName  = calendarDisplayName,
        calendarColor        = calendarColor,
        eventColor           = eventColor
    )
}

fun CalendarEvent.toEntity(): CalendarEventEntity {
    val zone = ZoneId.systemDefault()
    return CalendarEventEntity(
        id                   = id,
        title                = title,
        description          = description,
        location             = location,
        startTimeMillis      = startTime.atZone(zone).toInstant().toEpochMilli(),
        endTimeMillis        = endTime.atZone(zone).toInstant().toEpochMilli(),
        isAllDay             = isAllDay,
        calendarDisplayName  = calendarDisplayName,
        calendarColor        = calendarColor,
        eventColor           = eventColor
    )
}
