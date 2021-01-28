package com.example.reminderapp.di

import com.example.reminderapp.data.ReminderDataSource
import com.example.reminderapp.data.local.ReminderLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
annotation class ReminderDatabaseSource

@InstallIn(ApplicationComponent::class)
@Module
abstract class LocalDataSourceModule {
    @ReminderDatabaseSource
    @Singleton
    @Binds
    abstract fun bindDatabaseReminderSource(impl: ReminderLocalDataSource): ReminderDataSource
}