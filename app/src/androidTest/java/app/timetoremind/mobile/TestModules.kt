package app.timetoremind.mobile

import app.timetoremind.mobile.data.*
import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.data.domain.History
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.local.FakeHistoryLocalDataSource
import app.timetoremind.mobile.di.*
import app.timetoremind.mobile.service.AlarmTimerManager
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