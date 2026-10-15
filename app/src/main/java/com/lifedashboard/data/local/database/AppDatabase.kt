package com.lifedashboard.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lifedashboard.data.local.dao.*
import com.lifedashboard.data.local.entity.*

@Database(
    entities = [
        HealthDataEntity::class,
        WorkoutSessionEntity::class,
        ExerciseSetEntity::class,
        CalendarEventEntity::class,
        WaterIntakeEntity::class,
        AchievementEntity::class,
        WeightEntryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun waterIntakeDao(): WaterIntakeDao
    abstract fun achievementDao(): AchievementDao
    abstract fun weightDao(): WeightDao
}
