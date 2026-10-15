package com.lifedashboard.data.local.dao

import androidx.room.*
import com.lifedashboard.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    @Upsert
    suspend fun upsert(entry: WeightEntryEntity): Long

    @Delete
    suspend fun delete(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_entries ORDER BY dateMillis DESC")
    fun observeAllWeights(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries ORDER BY dateMillis DESC LIMIT 1")
    suspend fun getLatestWeight(): WeightEntryEntity?

    @Query("SELECT * FROM weight_entries ORDER BY dateMillis DESC LIMIT 30")
    suspend fun getRecentWeights(): List<WeightEntryEntity>

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
