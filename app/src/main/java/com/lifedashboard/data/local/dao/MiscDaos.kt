package com.lifedashboard.data.local.dao

import androidx.room.*
import com.lifedashboard.data.local.entity.AchievementEntity
import com.lifedashboard.data.local.entity.WaterIntakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterIntakeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WaterIntakeEntity)

    @Query("""
        SELECT SUM(amountMl) FROM water_intake
        WHERE dateMillis = :dateMillis
    """)
    fun getTotalForDay(dateMillis: Long): Flow<Int?>

    @Query("""
        SELECT * FROM water_intake
        WHERE dateMillis >= :fromMillis AND dateMillis <= :toMillis
        ORDER BY timestampMillis DESC
    """)
    fun getEntriesInRange(fromMillis: Long, toMillis: Long): Flow<List<WaterIntakeEntity>>

    @Delete
    suspend fun delete(entry: WaterIntakeEntity)

    @Query("DELETE FROM water_intake WHERE dateMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}

@Dao
interface AchievementDao {

    @Upsert
    suspend fun upsertAll(achievements: List<AchievementEntity>)

    @Upsert
    suspend fun upsert(achievement: AchievementEntity)

    @Query("SELECT * FROM achievements ORDER BY unlockedAt DESC")
    fun getAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE unlockedAt IS NOT NULL ORDER BY unlockedAt DESC")
    fun getUnlocked(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE id = :id")
    suspend fun getById(id: String): AchievementEntity?
}
