package com.lifedashboard.app.di

import android.content.Context
import androidx.room.Room
import com.lifedashboard.data.local.dao.*
import com.lifedashboard.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "life_dashboard.db"
        )
            .fallbackToDestructiveMigration()   // En desarrollo; usar migraciones en prod
            .build()

    @Provides fun provideHealthDao(db: AppDatabase): HealthDao = db.healthDao()
    @Provides fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideCalendarEventDao(db: AppDatabase): CalendarEventDao = db.calendarEventDao()
    @Provides fun provideWaterIntakeDao(db: AppDatabase): WaterIntakeDao = db.waterIntakeDao()
    @Provides fun provideAchievementDao(db: AppDatabase): AchievementDao = db.achievementDao()
}
