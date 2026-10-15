package com.lifedashboard.app.di

import com.lifedashboard.data.repository.CalendarRepositoryImpl
import com.lifedashboard.domain.repository.CalendarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: CalendarRepositoryImpl
    ): CalendarRepository

    // Los demás repositories se añadirán aquí:
    // @Binds @Singleton abstract fun bindHealthRepository(...)
    // @Binds @Singleton abstract fun bindWorkoutRepository(...)
}
