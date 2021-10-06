package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.di.*
import com.github.jeremyt135.reminderapp.service.*
import com.google.common.truth.Truth.assertThat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import javax.inject.Singleton

@UninstallModules(
    DispatcherModule::class,
    DatabaseModule::class,
    RepositoryModule::class,
    AlarmTimerModule::class,
)
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AlarmActionReceiverTest {
    @InstallIn(SingletonComponent::class)
    @Module
    inner class TestModule {
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

        @Provides
        @Singleton
        fun provideAlarmTimerManager(): AlarmTimerManager =
            mockkClass(AlarmTimerManager::class)

        @DefaultDispatcher
        @Provides
        @Singleton
        fun provideDefaultDispatcher(): CoroutineDispatcher = mainCoroutineRule.dispatcher

        @AppCoroutineScope
        @Provides
        @Singleton
        fun provideApplicationScope(): CoroutineScope = mainCoroutineRule

        @IoDispatcher
        @Provides
        @Singleton
        fun provideIoDispatcher(): CoroutineDispatcher = mainCoroutineRule.dispatcher
    }

    @get:Rule(order = 1)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 0)
    var mainCoroutineRule = MainCoroutineRule()

    @Inject
    lateinit var timerManager: AlarmTimerManager

    @Inject
    lateinit var reminderRepository: ReminderRepository

    lateinit var receiver: AlarmActionReceiver

    val context: Context = ApplicationProvider.getApplicationContext()

    lateinit var mockTimer: AlarmTimer

    @Before
    fun setup() {
        hiltRule.inject()

        mockkObject(AlarmMediaService.Companion)
        justRun { AlarmMediaService.requestStop(any()) }
        justRun { AlarmMediaService.start(any(), any()) }

        mockkObject(AlarmHistoryService.Companion)
        justRun { AlarmHistoryService.start(any(), any(), any()) }

        mockkObject(AlarmSchedulingService.Companion)
        justRun { AlarmSchedulingService.startScheduleNewAlarm(any(), any()) }
        justRun { AlarmSchedulingService.startScheduleNext(any()) }

        mockkObject(AlarmSnoozeService.Companion)
        justRun { AlarmSnoozeService.start(any(), any()) }

        mockTimer = mockkClass(AlarmTimer::class)
        every { timerManager.timer } returns mockTimer
        receiver = spyk(AlarmActionReceiver())

        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun intentAction_null_returns() = mainCoroutineRule.runBlockingTest {
        val intent = mockk<Intent>()
        every { intent.action } returns null

        receiver.onReceive(context, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun intentAction_notExpected_returns() = mainCoroutineRule.runBlockingTest {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START

        receiver.onReceive(context, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun stopsAlarmMediaService() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val action = ACTION_ALARM_SNOOZE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        every { mockTimer.stop() } returns true

        receiver.onReceive(context, intent)

        verify {
            AlarmMediaService.requestStop(context)
        }
    }

    @Test
    fun actionSnooze_startsSnoozeService() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val action = ACTION_ALARM_SNOOZE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        every { mockTimer.stop() } returns true

        receiver.onReceive(context, intent)

        verify {
            AlarmSnoozeService.start(any(), any())
        }
    }

    @Test
    fun actionSkip_startsHistoryAndSchedulingService() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val action = ACTION_ALARM_SKIP
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        every { mockTimer.stop() } returns true

        receiver.onReceive(context, intent)

        verify {
            AlarmHistoryService.start(any(), reminder, ReminderStatus.SKIPPED)
            AlarmSchedulingService.startScheduleNewAlarm(any(), reminder)
        }
    }

    @Test
    fun actionComplete_startsHistoryAndSchedulingService() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val action = ACTION_ALARM_COMPLETE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id
        every { mockTimer.stop() } returns true

        receiver.onReceive(context, intent)

        verify {
            AlarmHistoryService.start(any(), any(), ReminderStatus.COMPLETED)
            AlarmSchedulingService.startScheduleNewAlarm(any(), reminder)
        }
    }

    @Test
    fun stopsAlarmManagerTimerInstance() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val action = ACTION_ALARM_SNOOZE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        every { mockTimer.stop() } returns true
        receiver.onReceive(context, intent)

        verify {
            mockTimer.stop()
            AlarmMediaService.requestStop(context)
            AlarmSnoozeService.start(any(), any())
        }
    }

    @Test
    fun whenTimerStopFalse_returnsWithoutAction() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val action = ACTION_ALARM_SNOOZE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        every { mockTimer.stop() } returns false
        receiver.onReceive(context, intent)

        verify {
            mockTimer.stop()
        }

        verify(exactly = 0) {
            AlarmSnoozeService.start(any(), any())
        }
    }
}