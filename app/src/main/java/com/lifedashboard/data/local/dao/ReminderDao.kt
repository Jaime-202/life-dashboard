package com.lifedashboard.data.local.dao

import androidx.room.*
import com.lifedashboard.data.local.entity.ReminderEntity
import com.lifedashboard.data.local.entity.ReminderLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun observeEnabled(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminder_logs WHERE reminderId = :reminderId")
    suspend fun deleteLogsForReminder(reminderId: Long)

    @Transaction
    suspend fun deleteReminderAndLogs(reminderId: Long) {
        deleteLogsForReminder(reminderId)
        deleteById(reminderId)
    }

    @Query("UPDATE reminders SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    // Logs de cumplimiento diario
    @Query("SELECT * FROM reminder_logs WHERE dateMillis = :dateMillis")
    fun observeLogsForDate(dateMillis: Long): Flow<List<ReminderLogEntity>>

    @Query("SELECT * FROM reminder_logs WHERE reminderId = :reminderId AND dateMillis = :dateMillis LIMIT 1")
    suspend fun getLog(reminderId: Long, dateMillis: Long): ReminderLogEntity?

    @Upsert
    suspend fun upsertLog(log: ReminderLogEntity)

    @Transaction
    suspend fun toggleCompletion(reminderId: Long, dateMillis: Long, isCompleted: Boolean) {
        val existing = getLog(reminderId, dateMillis)
        if (existing != null) {
            upsertLog(
                existing.copy(
                    isCompleted = isCompleted,
                    completedAt = if (isCompleted) System.currentTimeMillis() else 0L
                )
            )
        } else {
            upsertLog(
                ReminderLogEntity(
                    reminderId = reminderId,
                    dateMillis = dateMillis,
                    isCompleted = isCompleted,
                    completedAt = if (isCompleted) System.currentTimeMillis() else 0L
                )
            )
        }
    }
}
