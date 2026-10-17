package com.lifedashboard.data.local.dao

import androidx.room.*
import com.lifedashboard.data.local.entity.MealEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeals(meals: List<MealEntryEntity>)

    @Update
    suspend fun updateMeal(meal: MealEntryEntity)

    @Delete
    suspend fun deleteMeal(meal: MealEntryEntity)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun deleteMealById(id: Long)

    @Query("SELECT * FROM meals WHERE dateMillis >= :startMillis AND dateMillis <= :endMillis ORDER BY createdAt ASC")
    fun observeMealsForDay(startMillis: Long, endMillis: Long): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meals WHERE dateMillis >= :startMillis AND dateMillis <= :endMillis ORDER BY createdAt ASC")
    suspend fun getMealsForDay(startMillis: Long, endMillis: Long): List<MealEntryEntity>

    @Query("SELECT * FROM meals ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentMeals(limit: Int = 50): Flow<List<MealEntryEntity>>
}
