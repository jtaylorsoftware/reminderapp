package app.timetoremind.mobile.di

import android.content.Context
import androidx.room.Room
import app.timetoremind.mobile.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object DatabaseModule {
    @Singleton
    @Provides
    fun provideReminderDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ReminderApp.db"
        )
            .build()
    }

    @Provides
    fun provideReminderDao(appDatabase: AppDatabase) = appDatabase.reminderDao()

    @Provides
    fun provideAlarmDao(appDatabase: AppDatabase) = appDatabase.alarmDao()

    @Provides
    fun provideHistoryDao(appDatabase: AppDatabase) = appDatabase.historyDao()
}