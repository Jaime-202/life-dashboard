package com.lifedashboard.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Se recrea la tabla porque DROP COLUMN no existe en el SQLite de Android anterior a 14
            db.execSQL("""
                CREATE TABLE `health_data_new` (
                    `dateMillis` INTEGER NOT NULL,
                    `steps` INTEGER NOT NULL,
                    `distanceMeters` REAL NOT NULL,
                    `activeCalories` REAL NOT NULL,
                    `sleepHours` REAL NOT NULL,
                    `sleepDeepHours` REAL NOT NULL,
                    `sleepRemHours` REAL NOT NULL,
                    `syncedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`dateMillis`)
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO `health_data_new`
                SELECT `dateMillis`, `steps`, `distanceMeters`, `activeCalories`,
                       `sleepHours`, `sleepDeepHours`, `sleepRemHours`, `syncedAt`
                FROM `health_data`
            """.trimIndent())
            db.execSQL("DROP TABLE `health_data`")
            db.execSQL("ALTER TABLE `health_data_new` RENAME TO `health_data`")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `meals` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dateMillis` INTEGER NOT NULL,
                    `mealType` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `brand` TEXT NOT NULL,
                    `grams` REAL NOT NULL,
                    `calories` REAL NOT NULL,
                    `protein` REAL NOT NULL,
                    `carbs` REAL NOT NULL,
                    `fat` REAL NOT NULL,
                    `notes` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_dateMillis` ON `meals` (`dateMillis`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_mealType` ON `meals` (`mealType`)")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `meals` ADD COLUMN `imageUrl` TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `reminders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `daysOfWeek` TEXT NOT NULL,
                    `timeOfDay` TEXT NOT NULL,
                    `iconEmoji` TEXT NOT NULL,
                    `isEnabled` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `reminder_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `reminderId` INTEGER NOT NULL,
                    `dateMillis` INTEGER NOT NULL,
                    `isCompleted` INTEGER NOT NULL,
                    `completedAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_logs_reminderId_dateMillis` ON `reminder_logs` (`reminderId`, `dateMillis`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_logs_dateMillis` ON `reminder_logs` (`dateMillis`)")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val hasColumn = db.query("PRAGMA table_info(`meals`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                    .any { it == "isFavorite" }
            }
            if (!hasColumn) {
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_isFavorite` ON `meals` (`isFavorite`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "life_dashboard.db"
        )
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            // Solo versiones sin ruta de migración. Una migración que falte en el futuro debe
            // detectarse al desarrollar, no borrar en silencio el historial de salud y comidas.
            .fallbackToDestructiveMigrationFrom(1, 2, 3)
            .build()

    @Provides fun provideHealthDao(db: AppDatabase): HealthDao = db.healthDao()
    @Provides fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideCalendarEventDao(db: AppDatabase): CalendarEventDao = db.calendarEventDao()
    @Provides fun provideWaterIntakeDao(db: AppDatabase): WaterIntakeDao = db.waterIntakeDao()
    @Provides fun provideAchievementDao(db: AppDatabase): AchievementDao = db.achievementDao()
    @Provides fun provideWeightDao(db: AppDatabase): WeightDao = db.weightDao()
    @Provides fun provideMealDao(db: AppDatabase): MealDao = db.mealDao()
    @Provides fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
}
