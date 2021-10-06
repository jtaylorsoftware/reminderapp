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
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.di.AlarmTimerModule
import com.github.jeremyt135.reminderapp.di.DatabaseModule
import com.github.jeremyt135.reminderapp.di.RepositoryModule
import com.github.jeremyt135.reminderapp.service.AlarmMediaService
import com.github.jeremyt135.reminderapp.service.AlarmSchedulingService
import com.github.jeremyt135.reminderapp.service.AlarmTimerManager
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import javax.inject.Singleton

@UninstallModules(DatabaseModule::class, RepositoryModule::class, AlarmTimerModule::class)
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AlarmStartReceiverTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    lateinit var receiver: AlarmStartReceiver

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var timerManager: AlarmTimerManager

    val context = ApplicationProvider.getApplicationContext<Context>()

    @InstallIn(SingletonComponent::class)
    @Module
    object TestModule {
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
            mockkClass(AlarmTimerManager::class, relaxed = true)
    }

    @Before
    fun setup() {
        hiltRule.inject()

        mockkObject(AlarmMediaService.Companion)
        justRun { AlarmMediaService.start(any(), any()) }

        mockkObject(AlarmSchedulingService.Companion)
        justRun { AlarmSchedulingService.startScheduleNext(any()) }

        receiver = spyk(AlarmStartReceiver())

        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun intentAction_notAlarmStart_returns() = mainCoroutineRule.runBlockingTest {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_SNOOZE

        receiver.onReceive(context, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun getsNullReminder_schedulesNext() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id
        receiver.onReceive(context, intent)

        verify {
            AlarmSchedulingService.startScheduleNext(any())
        }
    }

    @Test
    fun getsReminder_startsTimerAndMediaService() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())
        alarmRepository.save(alarm)

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        coEvery { timerManager.startTimer(any(), any(), any()) } returns true

        receiver.onReceive(context, intent)

        val savedData = reminderRepository.getReminderAndAlarm(reminder.id)!!

        coVerify {
            AlarmMediaService.start(context, savedData.reminder)
            timerManager.startTimer(any(), any(), any())
        }
    }

    @Test
    fun whenStartTimerReturnsFalse_callsScheduleNext() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())
        alarmRepository.save(alarm)

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        coEvery { timerManager.startTimer(any(), any(), any()) } returns false

        receiver.onReceive(context, intent)

        coVerifyOrder {
            timerManager.startTimer(any(), any(), any())
            AlarmSchedulingService.startScheduleNext(any())
        }
    }
}