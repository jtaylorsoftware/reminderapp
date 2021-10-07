package com.github.jeremyt135.reminderapp

import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.di.*
import com.github.jeremyt135.reminderapp.service.AlarmTimerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockkClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class, AlarmTimerModule::class]
)
object TestDataModule {
    private val reminderCache: MutableList<Reminder> = mutableListOf()
    private val alarmCache: MutableList<Alarm> = mutableListOf()
    private val historyCache: MutableList<History> = mutableListOf()

    @Provides
    @Singleton
    fun provideReminderRepository(): ReminderRepository =
        FakeReminderRepository(reminderCache, alarmCache, historyCache)


    @Provides
    @Singleton
    fun provideAlarmRepository(): AlarmRepository = FakeAlarmRepository(alarmCache)

    @Provides
    @Singleton
    fun provideHistoryRepository(): HistoryRepository = FakeHistoryRepository(
        FakeHistoryLocalDataSource(historyCache)
    )
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AlarmTimerModule::class])
object TestTimerModule {
    @Provides
    @Singleton
    fun provideAlarmTimerManager(): AlarmTimerManager =
        mockkClass(AlarmTimerManager::class)
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DispatcherModule::class])
@ExperimentalCoroutinesApi
object TestDispatcherModule {
    @Provides
    @Singleton
    fun provideMainCoroutineRule(): MainCoroutineRule = MainCoroutineRule()

    @DefaultDispatcher
    @Provides
    @Singleton
    fun provideDefaultDispatcher(mainCoroutineRule: MainCoroutineRule): CoroutineDispatcher =
        mainCoroutineRule.dispatcher

    @AppCoroutineScope
    @Provides
    @Singleton
    fun provideApplicationScope(mainCoroutineRule: MainCoroutineRule): CoroutineScope =
        mainCoroutineRule

    @IoDispatcher
    @Provides
    @Singleton
    fun provideIoDispatcher(mainCoroutineRule: MainCoroutineRule): CoroutineDispatcher =
        mainCoroutineRule.dispatcher
}