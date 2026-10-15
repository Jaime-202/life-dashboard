package com.lifedashboard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro histórico de pesaje del usuario.
 */
@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long,
    val weightKg: Float,
    val note: String? = null
)
