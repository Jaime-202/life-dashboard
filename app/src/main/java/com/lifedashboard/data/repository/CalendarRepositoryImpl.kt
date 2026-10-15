package com.lifedashboard.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import android.util.Log
import com.lifedashboard.data.local.dao.CalendarEventDao
import com.lifedashboard.data.local.entity.CalendarEventEntity
import com.lifedashboard.data.mapper.toCalendarEvent
import com.lifedashboard.domain.model.CalendarEvent
import com.lifedashboard.domain.repository.CalendarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CalendarRepositoryImpl"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * CalendarRepositoryImpl
 *
 * Lee eventos directamente de CalendarContract (Content Provider nativo).
 * SIN internet. SIN OAuth. SIN Google Calendar API.
 *
 * Flujo:
 *  1. [syncCalendarEvents] consulta CalendarContract.Instances vía cursor.
 *  2. Parsea y guarda en Room (caché local).
 *  3. Las funciones de lectura sirven datos desde Room como Flow reactivo.
 *
 * ¿Por qué Instances y no Events?
 *  - CalendarContract.Instances expande eventos recurrentes correctamente.
 *  - Permite filtrar por rango de tiempo sin calcular manualmente las repeticiones.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CalendarEventDao
) : CalendarRepository {

    // ─── Columnas que pedimos al Content Provider ─────────────────────────────
    private val PROJECTION = arrayOf(
        Instances.EVENT_ID,         // 0  ID único del evento
        Instances.TITLE,            // 1  Título
        Instances.DESCRIPTION,      // 2  Descripción
        Instances.EVENT_LOCATION,   // 3  Ubicación
        Instances.BEGIN,            // 4  Inicio en milisegundos (UTC)
        Instances.END,              // 5  Fin en milisegundos (UTC)
        Instances.ALL_DAY,          // 6  1 = todo el día
        Instances.CALENDAR_DISPLAY_NAME, // 7  Nombre del calendario (ej. "Personal")
        Instances.CALENDAR_COLOR,   // 8  Color del calendario
        Instances.EVENT_COLOR,      // 9  Color del evento (puede ser null)
    )

    // Índices del array PROJECTION (evitamos números mágicos)
    private object Col {
        const val EVENT_ID       = 0
        const val TITLE          = 1
        const val DESCRIPTION    = 2
        const val LOCATION       = 3
        const val BEGIN          = 4
        const val END            = 5
        const val ALL_DAY        = 6
        const val CAL_NAME       = 7
        const val CAL_COLOR      = 8
        const val EVENT_COLOR    = 9
    }

    // ─── Lectura reactiva desde Room ──────────────────────────────────────────

    override fun getEvents(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> {
        val fromMillis = from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val toMillis   = to.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dao
            .getEventsBetween(fromMillis, toMillis)
            .map { entities -> entities.map { it.toCalendarEvent() } }
    }

    override fun getTodayEvents(): Flow<List<CalendarEvent>> {
        return getEvents(LocalDate.now(), LocalDate.now())
    }

    override fun getUpcomingEvents(limit: Int): Flow<List<CalendarEvent>> {
        val nowMillis = System.currentTimeMillis()
        return dao
            .getUpcomingEvents(nowMillis, limit)
            .map { entities -> entities.map { it.toCalendarEvent() } }
    }

    // ─── Sincronización con CalendarContract ──────────────────────────────────

    /**
     * Lee los eventos de los calendarios del dispositivo usando CalendarContract.
     *
     * Rango: desde hoy hasta 60 días en el futuro.
     * Se ejecuta en Dispatchers.IO para no bloquear el hilo principal.
     *
     * PERMISO REQUERIDO: android.permission.READ_CALENDAR
     */
    override suspend fun syncCalendarEvents(): Unit = withContext(Dispatchers.IO) {
        try {
            val now     = System.currentTimeMillis()
            val future  = now + TimeUnit.DAYS.toMillis(60)

            val entities = queryCalendarInstances(now, future)
            Log.d(TAG, "Eventos leídos de CalendarContract: ${entities.size}")

            // Limpiamos eventos muy antiguos (> 7 días en el pasado)
            val cutoff = now - TimeUnit.DAYS.toMillis(7)
            dao.deletePastEvents(cutoff)

            // Guardamos los nuevos (upsert para no duplicar)
            dao.upsertAll(entities)

        } catch (e: SecurityException) {
            // Permiso READ_CALENDAR no concedido — la UI debe solicitarlo
            Log.e(TAG, "Permiso READ_CALENDAR no concedido", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando calendario", e)
        }
    }

    // ─── Consulta real al Content Provider ───────────────────────────────────

    /**
     * Construye la URI de Instances con el rango de tiempo y ejecuta el cursor.
     *
     * La URI de Instances DEBE incluir el rango de tiempo como path segments:
     *   content://com.android.calendar/instances/when/{startMillis}/{endMillis}
     *
     * ContentUris.appendId hace esto de forma segura.
     */
    private fun queryCalendarInstances(
        startMillis: Long,
        endMillis: Long
    ): List<CalendarEventEntity> {
        val builder = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        val instanceUri = builder.build()

        val results = mutableListOf<CalendarEventEntity>()

        context.contentResolver.query(
            instanceUri,
            PROJECTION,
            null,   // sin filtro adicional (ya filtramos por rango en la URI)
            null,
            "${Instances.BEGIN} ASC"    // ordenados por hora de inicio
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val entity = mapCursorToEntity(cursor)
                if (entity != null) results.add(entity)
            }
        }

        return results
    }

    /**
     * Convierte una fila del cursor a [CalendarEventEntity].
     * Retorna null si el evento no tiene título (eventos fantasma del sistema).
     */
    private fun mapCursorToEntity(cursor: android.database.Cursor): CalendarEventEntity? {
        val title = cursor.getStringOrNull(Col.TITLE) ?: return null

        return CalendarEventEntity(
            id                   = cursor.getLong(Col.EVENT_ID),
            title                = title,
            description          = cursor.getStringOrNull(Col.DESCRIPTION),
            location             = cursor.getStringOrNull(Col.LOCATION),
            startTimeMillis      = cursor.getLong(Col.BEGIN),
            endTimeMillis        = cursor.getLong(Col.END),
            isAllDay             = cursor.getInt(Col.ALL_DAY) == 1,
            calendarDisplayName  = cursor.getStringOrNull(Col.CAL_NAME) ?: "Sin nombre",
            calendarColor        = cursor.getInt(Col.CAL_COLOR),
            eventColor           = if (cursor.isNull(Col.EVENT_COLOR)) null
                                   else cursor.getInt(Col.EVENT_COLOR)
        )
    }

    // ─── Extension helpers ────────────────────────────────────────────────────

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (isNull(columnIndex)) null else getString(columnIndex)
    }
}
