package com.lifedashboard.data.local.dao

import androidx.room.*
import com.lifedashboard.data.local.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {

    @Query("""
        SELECT * FROM calendar_events
        WHERE startTimeMillis >= :fromMillis
          AND startTimeMillis <= :toMillis
        ORDER BY startTimeMillis ASC
    """)
    fun getEventsBetween(fromMillis: Long, toMillis: Long): Flow<List<CalendarEventEntity>>

    @Query("""
        SELECT * FROM calendar_events
        WHERE startTimeMillis >= :nowMillis
        ORDER BY startTimeMillis ASC
        LIMIT :limit
    """)
    fun getUpcomingEvents(nowMillis: Long, limit: Int): Flow<List<CalendarEventEntity>>

    /** Versión suspend (snapshot) para usar en DataAggregatorUseCase */
    @Query("""
        SELECT * FROM calendar_events
        WHERE startTimeMillis >= :nowMillis
        ORDER BY startTimeMillis ASC
        LIMIT :limit
    """)
    suspend fun getUpcomingEventsSnapshot(nowMillis: Long, limit: Int): List<CalendarEventEntity>

    @Upsert
    suspend fun upsertAll(events: List<CalendarEventEntity>)

    /** Elimina eventos que ya pasaron hace más de N días (limpieza periódica) */
    @Query("DELETE FROM calendar_events WHERE endTimeMillis < :cutoffMillis")
    suspend fun deletePastEvents(cutoffMillis: Long)

    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()
}
