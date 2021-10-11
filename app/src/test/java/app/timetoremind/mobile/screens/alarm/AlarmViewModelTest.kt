package app.timetoremind.mobile.screens.alarm

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.timetoremind.mobile.MainCoroutineRule
import app.timetoremind.mobile.ReminderApplication
import app.timetoremind.mobile.broadcast.*
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.*
import app.timetoremind.mobile.getOrAwaitValue
import app.timetoremind.mobile.service.*
import app.timetoremind.mobile.util.EventFlow
import app.timetoremind.mobile.util.currentLocalDateTime
import app.timetoremind.mobile.util.getShortLocalTimeDisplay
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class AlarmViewModelTest {
    @MockK
    lateinit var mockReminderRepo: ReminderRepository

    @MockK
    lateinit var mockSavedStateHandle: SavedStateHandle

    @MockK
    lateinit var mockApplication: ReminderApplication

    @MockK
    lateinit var mockContext: Context

    @MockK
    lateinit var mockTimerManager: AlarmTimerManager

    private lateinit var fakeBinder: AlarmMediaService.AlarmServiceBinder

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var time: LocalDateTime
    private lateinit var reminder: Reminder
    private lateinit var alarm: Alarm
    private lateinit var reminderAndAlarm: ReminderAndAlarm

    private lateinit var viewModel: AlarmViewModel

    private lateinit var timerManager: AlarmTimerManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        time = currentLocalDateTime().plusHours(2)
        reminder = Reminder(schedulingRules = SchedulingRules(time = time.toLocalTime()))
        alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = time)
        reminderAndAlarm = ReminderAndAlarm(reminder, alarm)
        timerManager = mockTimerManager
        val mockTimer = mockk<AlarmTimer>()

        every { mockTimerManager.timer } returns mockTimer
        every { mockTimer.stop() } returns true
        every { mockTimer.timerFinished } returns EventFlow()
        coEvery { mockReminderRepo.getReminderAndAlarm(reminder.id) } returns reminderAndAlarm
        every { mockApplication.applicationContext } returns mockContext

        mockkStatic(::showToast)
        justRun { showToast(any(), any(), any()) }

        mockkObject(AlarmSchedulingService.Companion)
        justRun { AlarmSchedulingService.startScheduleNext(any()) }
        justRun { AlarmSchedulingService.startScheduleNewAlarm(any(), any()) }
        mockkObject(AlarmSnoozeService.Companion)
        justRun { AlarmSnoozeService.start(any(), any()) }
        mockkObject(AlarmHistoryService.Companion)
        justRun { AlarmHistoryService.start(any(), any(), any()) }

        mockkObject(AlarmMediaService.Companion)

        mockSavedStateHandle = spyk(SavedStateHandle())

        fakeBinder = spyk(object : AlarmMediaService.AlarmServiceBinder() {
            override fun stop() {}
        })
    }

    @Test
    fun loadReminderData_loadsReminderAndAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        coEvery { mockTimerManager.startTimer(any(), any(), any()) } returns true

        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, timerManager)

        coVerify {
            mockReminderRepo.getReminderAndAlarm(reminder.id)
        }
    }

    @Test
    fun liveData_matchesLoadedReminderData() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        coEvery { mockTimerManager.startTimer(any(), any(), any()) } returns true

        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, timerManager)

        assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(reminder)
        assertThat(viewModel.alarm.getOrAwaitValue()).isEqualTo(alarm)

        assertThat(viewModel.time.getOrAwaitValue()).isEqualTo(time.getShortLocalTimeDisplay())
    }

    @Test
    fun snooze_startsServiceForSnooze() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, timerManager)

        viewModel.snooze()
        assertThat(viewModel.finished.peek()).isNotNull()
        verify {
            AlarmSnoozeService.start(any(), reminder)
        }
    }

    @Test
    fun skip_startsServiceForSkip() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, timerManager)

        viewModel.skip()
        assertThat(viewModel.finished.peek()).isNotNull()
        verify {
            AlarmHistoryService.start(any(), any(), ReminderStatus.SKIPPED)
            AlarmSchedulingService.startScheduleNewAlarm(any(), reminder)
        }
    }

    @Test
    fun complete_startsServiceForComplete() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, timerManager)

        viewModel.complete()
        assertThat(viewModel.finished.peek()).isNotNull()
        verify {
            AlarmHistoryService.start(any(), any(), ReminderStatus.COMPLETED)
            AlarmSchedulingService.startScheduleNewAlarm(any(), reminder)
        }
    }
}