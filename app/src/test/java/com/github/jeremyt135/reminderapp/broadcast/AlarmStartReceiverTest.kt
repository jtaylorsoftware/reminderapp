package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.di.DatabaseModule
import com.github.jeremyt135.reminderapp.di.RepositoryModule
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
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

@UninstallModules(DatabaseModule::class, RepositoryModule::class)
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
    }

    @Before
    fun setup() {
        hiltRule.inject()

        val mockWorkManager = mockkClass(WorkManager::class)
        every { mockWorkManager.enqueue(any<WorkRequest>()) } returns mockkClass(Operation::class)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkObject(AlarmService.Companion)
        justRun { AlarmService.start(any(), any()) }

        mockkObject(AlarmActionHandler.Companion)
        every { AlarmActionHandler.createForAction(any(), any()) } returns
                OneTimeWorkRequestBuilder<AlarmActionHandler>().build()

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
            AlarmActionHandler.createForScheduleNext("",0)
        }
    }

    @Test
    fun getsReminder_startsService() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())
        alarmRepository.save(alarm)

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        receiver.onReceive(context, intent)

        val savedData = reminderRepository.getReminderAndAlarm(reminder.id)!!

        verify {
            AlarmService.start(context, savedData.reminder)
        }
    }
}