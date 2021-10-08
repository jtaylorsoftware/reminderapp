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
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockkClass
import io.mockk.spyk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class, AlarmTimerModule::class]
)
object AndroidTestDataModule {
    private val reminderCache: MutableList<Reminder> = mutableListOf()
    private val alarmCache: MutableList<Alarm> = mutableListOf()
    private val historyCache = mutableListOf<History>()

    @Provides
    @Singleton
    fun provideReminderRepository(): ReminderRepository =
        spyk(FakeReminderRepository(reminderCache, alarmCache, historyCache))

    @Provides
    @Singleton
    fun provideAlarmRepository(): AlarmRepository = spyk(FakeAlarmRepository(alarmCache))

    @Provides
    @Singleton
    fun provideHistoryRepository(): HistoryRepository = spyk(FakeHistoryRepository(
        FakeHistoryLocalDataSource(historyCache))
    )
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AlarmTimerModule::class])
object AndroidTestTimerModuleTestInstallIn {

}

@Module
@InstallIn(SingletonComponent::class)
object AndroidTestTimerModule {
    @Provides
    @Singleton
    fun provideAlarmTimerManager(): AlarmTimerManager =
        mockkClass(AlarmTimerManager::class)
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DispatcherModule::class])
@ExperimentalCoroutinesApi
object AndroidTestDispatcherModule {
    private val mainScope = MainCoroutineRule()
    private val dispatcher = mainScope.dispatcher

    @Provides
    @Singleton
    fun provideTestScope(): MainCoroutineRule = mainScope

    @DefaultDispatcher
    @Provides
    @Singleton
    fun provideDefaultDispatcher(): CoroutineDispatcher = dispatcher

    @AppCoroutineScope
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = mainScope

    @IoDispatcher
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = dispatcher
}