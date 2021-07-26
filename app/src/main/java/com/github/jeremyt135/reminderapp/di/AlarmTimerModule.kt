package com.github.jeremyt135.reminderapp.di

import com.github.jeremyt135.reminderapp.service.AlarmTimerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@InstallIn(SingletonComponent::class)
@Module
object AlarmTimerModule {
    @Provides
    @Singleton
    fun provideAlarmTimerManager(): AlarmTimerManager = AlarmTimerManager()
}