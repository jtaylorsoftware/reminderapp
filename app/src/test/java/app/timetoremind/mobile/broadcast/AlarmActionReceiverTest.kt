package app.timetoremind.mobile.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.ReminderStatus
import app.timetoremind.mobile.service.*
import app.timetoremind.sharedtest.MainCoroutineRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AlarmActionReceiverTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var timerManager: AlarmTimerManager

    @Inject
    lateinit var reminderRepository: ReminderRepository

    lateinit var receiver: AlarmActionReceiver

    private val context: Context = ApplicationProvider.getApplicationContext()

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
    fun intentAction_null_returns() = runTest {
        val intent = mockk<Intent>()
        every { intent.action } returns null

        receiver.onReceive(context, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun intentAction_notExpected_returns() = runTest {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START

        receiver.onReceive(context, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun stopsAlarmMediaService() = runTest {
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
    fun actionSnooze_startsSnoozeService() = runTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val action = ACTION_ALARM_SNOOZE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        every { mockTimer.stop() } returns true

        receiver.onReceive(context, intent)
        advanceUntilIdle()
        verify {
            AlarmSnoozeService.start(any(), any())
        }
    }

    @Test
    fun actionSkip_startsHistoryAndSchedulingService() = runTest(UnconfinedTestDispatcher()) {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val action = ACTION_ALARM_SKIP
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        every { mockTimer.stop() } returns true

        receiver.onReceive(context, intent)
        advanceUntilIdle()
        verify {
            AlarmHistoryService.start(any(), reminder, ReminderStatus.SKIPPED)
            AlarmSchedulingService.startScheduleNewAlarm(any(), reminder)
        }
    }

    @Test
    fun actionComplete_startsHistoryAndSchedulingService() = runTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val action = ACTION_ALARM_COMPLETE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id
        every { mockTimer.stop() } returns true

        receiver.onReceive(context, intent)
        advanceUntilIdle()
        verify {
            AlarmHistoryService.start(any(), any(), ReminderStatus.COMPLETED)
            AlarmSchedulingService.startScheduleNewAlarm(any(), reminder)
        }
    }

    @Test
    fun stopsAlarmManagerTimerInstance() = runTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val action = ACTION_ALARM_SNOOZE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        every { mockTimer.stop() } returns true
        receiver.onReceive(context, intent)
        advanceUntilIdle()
        verify {
            mockTimer.stop()
            AlarmMediaService.requestStop(context)
            AlarmSnoozeService.start(any(), any())
        }
    }

    @Test
    fun whenTimerStopFalse_returnsWithoutAction() = runTest {
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