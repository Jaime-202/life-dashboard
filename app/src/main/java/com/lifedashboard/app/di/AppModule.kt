package com.lifedashboard.app.di

import android.content.Context
import com.lifedashboard.core.worker.WorkManagerScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWorkManagerScheduler(
        @ApplicationContext context: Context
    ): WorkManagerScheduler = WorkManagerScheduler(context)
}
