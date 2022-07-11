package app.timetoremind.mobile.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.service.AlarmMediaService
import app.timetoremind.mobile.service.AlarmSchedulingService
import app.timetoremind.mobile.service.AlarmTimerManager
import app.timetoremind.mobile.util.currentLocalDateTime
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
class AlarmStartReceiverTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    lateinit var receiver: AlarmStartReceiver

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var timerManager: AlarmTimerManager

    private val context: Context = ApplicationProvider.getApplicationContext()

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
    fun intentAction_notAlarmStart_returns() = runTest {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_SNOOZE

        receiver.onReceive(context, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun getsNullReminder_schedulesNext() = runTest(UnconfinedTestDispatcher()) {
        val reminder = Reminder()

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id
        receiver.onReceive(context, intent)
        advanceUntilIdle()
        verify {
            AlarmSchedulingService.startScheduleNext(any())
        }
    }

    @Test
    fun getsReminder_startsTimerAndMediaService() = runTest(UnconfinedTestDispatcher()) {
        val reminder = Reminder()
        reminderRepository.save(reminder)
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())
        alarmRepository.save(alarm)

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        coEvery { timerManager.startTimer(any(), any(), any()) } returns true

        receiver.onReceive(context, intent)
        advanceUntilIdle()

        val savedData = reminderRepository.getReminderAndAlarm(reminder.id)!!

        coVerify {
            AlarmMediaService.start(context, savedData.reminder)
            timerManager.startTimer(any(), any(), any())
        }
    }

    @Test
    fun whenStartTimerReturnsFalse_callsScheduleNext() = runTest(UnconfinedTestDispatcher()) {
        val reminder = Reminder()
        reminderRepository.save(reminder)
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())
        alarmRepository.save(alarm)

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        coEvery { timerManager.startTimer(any(), any(), any()) } returns false

        receiver.onReceive(context, intent)
        advanceUntilIdle()
        coVerifyOrder {
            timerManager.startTimer(any(), any(), any())
            AlarmSchedulingService.startScheduleNext(any())
        }
    }
}