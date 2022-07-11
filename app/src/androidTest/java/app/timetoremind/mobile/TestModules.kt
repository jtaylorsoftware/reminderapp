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
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockk
import io.mockk.spyk
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
    fun provideHistoryRepository(): HistoryRepository = spyk(
        FakeHistoryRepository(
            FakeHistoryLocalDataSource(historyCache)
        )
    )
}

/**
 * https://dagger.dev/hilt/testing.html
 *
 * "Note: @UninstallModules can only uninstall @InstallIn modules, not @TestInstallIn modules.
 * If a @TestInstallIn module needs to be uninstalled the module must be split into two separate
 * modules: a @TestInstallIn module that replaces the production module with no bindings
 * (i.e. only removes the production module), and a @InstallIn module that provides the standard fake
 * so that @UninstallModules can uninstall the provided fake."
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AlarmTimerModule::class])
object AndroidTestTimerModuleTestInstallIn

@Module
@InstallIn(SingletonComponent::class)
object AndroidTestTimerModule {
    @Provides
    @Singleton
    fun provideAlarmTimerManager(): AlarmTimerManager = mockk(relaxed = true)
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DispatcherModule::class])
@ExperimentalCoroutinesApi
object AndroidTestDispatcherModule {
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