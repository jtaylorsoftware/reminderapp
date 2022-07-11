package app.timetoremind.mobile

import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.data.domain.History
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.di.*
import app.timetoremind.mobile.service.AlarmTimerManager
import app.timetoremind.sharedtest.data.FakeAlarmRepository
import app.timetoremind.sharedtest.data.FakeHistoryRepository
import app.timetoremind.sharedtest.data.FakeReminderRepository
import app.timetoremind.sharedtest.data.local.FakeHistoryLocalDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockkClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    @DefaultDispatcher
    @Provides
    @Singleton
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @AppCoroutineScope
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(Dispatchers.Main)

    @IoDispatcher
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.Main
}