package com.github.jeremyt135.reminderapp.screens.alarm

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.ReminderApplication
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.service.AlarmTimer
import com.github.jeremyt135.reminderapp.service.AlarmTimerManager
import com.github.jeremyt135.reminderapp.util.EventFlow
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.getShortLocalTimeDisplay
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
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

    lateinit var actionSlot: CapturingSlot<String>
    lateinit var reminderIdSlot: CapturingSlot<String>

    private lateinit var fakeBinder: AlarmService.AlarmServiceBinder

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
        reminder = Reminder(time = time.toLocalTime())
        alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = time)
        reminderAndAlarm = ReminderAndAlarm(reminder, alarm)
        timerManager = mockTimerManager
        val mockTimer = mockk<AlarmTimer>()

        every { mockTimerManager.timer } returns mockTimer
        every { mockTimer.stop() } returns true
        every { mockTimer.timerFinished } returns EventFlow()
        every { mockTimerManager.startTimer(any(), any(), any()) } returns true
        coEvery { mockReminderRepo.getReminderAndAlarm(reminder.id) } returns reminderAndAlarm
        every { mockApplication.applicationContext } returns mockContext

        val mockWorkManager = mockkClass(WorkManager::class)
        every { mockWorkManager.enqueue(any<WorkRequest>()) } returns mockkClass(Operation::class)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkStatic(::showToast)
        justRun { showToast(any(), any(), any()) }

        mockkObject(AlarmActionHandler.Companion)
        actionSlot = slot()
        reminderIdSlot = slot()
        every {
            AlarmActionHandler.createForAction(
                capture(actionSlot),
                capture(reminderIdSlot)
            )
        } returns OneTimeWorkRequestBuilder<AlarmActionHandler>().build()

        mockkObject(AlarmService.Companion)

        fakeBinder = spyk(object : AlarmService.AlarmServiceBinder() {
            override fun stop() {}
        })
    }

    @Test
    fun loadReminderData_loadsReminderAndAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, timerManager)

        coVerify {
            mockReminderRepo.getReminderAndAlarm(reminder.id)
        }
    }

    @Test
    fun liveData_matchesLoadedReminderData() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, timerManager)

        assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(reminder)
        assertThat(viewModel.alarm.getOrAwaitValue()).isEqualTo(alarm)

        assertThat(viewModel.time.getOrAwaitValue()).isEqualTo(time.getShortLocalTimeDisplay())
    }

    @Test
    fun snooze_createsWorkerForSnooze() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, timerManager)

        viewModel.snooze()
        assertThat(viewModel.finished.peek()).isNotNull()
        assertThat(actionSlot.isCaptured).isTrue()
        assertThat(actionSlot.captured).isEqualTo(ACTION_ALARM_SNOOZE)
        assertThat(reminderIdSlot.captured).isEqualTo(reminder.id)
    }

    @Test
    fun skip_createsWorkerForSkip() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, timerManager)

        viewModel.skip()
        assertThat(viewModel.finished.peek()).isNotNull()
        assertThat(actionSlot.isCaptured).isTrue()
        assertThat(actionSlot.captured).isEqualTo(ACTION_ALARM_SKIP)
        assertThat(reminderIdSlot.captured).isEqualTo(reminder.id)
    }

    @Test
    fun complete_createsWorkerForComplete() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, timerManager)

        viewModel.complete()
        assertThat(viewModel.finished.peek()).isNotNull()
        assertThat(actionSlot.isCaptured).isTrue()
        assertThat(actionSlot.captured).isEqualTo(ACTION_ALARM_COMPLETE)
        assertThat(reminderIdSlot.captured).isEqualTo(reminder.id)
    }
}