package com.lifedashboard.data.local.dao

import androidx.room.*
import com.lifedashboard.data.local.entity.HealthDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {

    @Query("SELECT * FROM health_data WHERE dateMillis = :dateMillis")
    suspend fun getHealthForDay(dateMillis: Long): HealthDataEntity?

    @Query("""
        SELECT * FROM health_data
        WHERE dateMillis >= :fromMillis AND dateMillis <= :toMillis
        ORDER BY dateMillis ASC
    """)
    fun getHealthRange(fromMillis: Long, toMillis: Long): Flow<List<HealthDataEntity>>

    /** Últimos N días — para el DataAggregatorUseCase de Gemini */
    @Query("""
        SELECT * FROM health_data
        ORDER BY dateMillis DESC
        LIMIT :days
    """)
    suspend fun getLastNDays(days: Int): List<HealthDataEntity>

    @Upsert
    suspend fun upsert(entity: HealthDataEntity)

    @Upsert
    suspend fun upsertAll(entities: List<HealthDataEntity>)

    @Query("DELETE FROM health_data WHERE dateMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
