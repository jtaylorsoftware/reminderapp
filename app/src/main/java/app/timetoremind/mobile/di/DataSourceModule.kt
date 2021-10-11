package app.timetoremind.mobile.di

import app.timetoremind.mobile.data.HistoryDataSource
import app.timetoremind.mobile.data.ReminderDataSource
import app.timetoremind.mobile.data.local.HistoryLocalDataSource
import app.timetoremind.mobile.data.local.ReminderLocalDataSource
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