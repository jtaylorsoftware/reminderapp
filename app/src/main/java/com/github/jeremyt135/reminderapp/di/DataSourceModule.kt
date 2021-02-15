package com.github.jeremyt135.reminderapp.di

import com.github.jeremyt135.reminderapp.data.HistoryDataSource
import com.github.jeremyt135.reminderapp.data.ReminderDataSource
import com.github.jeremyt135.reminderapp.data.local.HistoryLocalDataSource
import com.github.jeremyt135.reminderapp.data.local.ReminderLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
annotation class ReminderDatabaseSource

@Qualifier
annotation class HistoryDatabaseSource

@InstallIn(SingletonComponent::class)
@Module
abstract class LocalDataSourceModule {
    @ReminderDatabaseSource
    @Binds
    @Singleton
    abstract fun bindDatabaseReminderSource(impl: ReminderLocalDataSource): ReminderDataSource

    @HistoryDatabaseSource
    @Binds
    @Singleton
    abstract fun bindDatabaseHistorySource(impl: HistoryLocalDataSource): HistoryDataSource
}