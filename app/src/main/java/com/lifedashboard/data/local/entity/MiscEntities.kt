package com.lifedashboard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_intake")
data class WaterIntakeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long,           // Día al que pertenece
    val timestampMillis: Long,      // Momento exacto del registro
    val amountMl: Int               // Cantidad en mililitros
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,                 // Ej. "STEPS_7K_STREAK_7"
    val title: String,
    val description: String,
    val category: String,           // "HEALTH" | "WORKOUT" | "SLEEP"
    val iconName: String,
    val unlockedAt: Long?,          // null = bloqueado
    val progress: Float = 0f,       // 0f..1f
    val targetValue: Float = 1f
)
