package com.github.jeremyt135.reminderapp.screens.alarm

import android.content.Context
import android.content.ServiceConnection
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.work.*
import androidx.work.impl.WorkManagerImpl
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.ReminderApplication
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.github.jeremyt135.reminderapp.service.AlarmService
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

    lateinit var actionSlot: CapturingSlot<String>
    lateinit var reminderIdSlot: CapturingSlot<Long>

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
    private lateinit var serviceConnection: CapturingSlot<ServiceConnection>

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        time = currentLocalDateTime().plusHours(2)
        reminder = Reminder(id = 1, time = time.toLocalTime())
        alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = time)
        reminderAndAlarm = ReminderAndAlarm(reminder, alarm)

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
        serviceConnection = slot()
        justRun { AlarmService.bind(any(), capture(serviceConnection)) }

        fakeBinder = spyk(object : AlarmService.AlarmServiceBinder() {
            override fun stop() {}
        })
    }

    @Test
    fun loadReminderData_loadsReminderAndAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, this)

        coVerify {
            mockReminderRepo.getReminderAndAlarm(reminder.id)
        }
    }

    @Test
    fun liveData_matchesLoadedReminderData() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, this)

        assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(reminder)
        assertThat(viewModel.alarm.getOrAwaitValue()).isEqualTo(alarm)

        assertThat(viewModel.time.getOrAwaitValue()).isEqualTo(time.getShortLocalTimeDisplay())
    }

    @Test
    fun snooze_createsWorkerForSnooze() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, this)
        serviceConnection.captured.onServiceConnected(mockk(), fakeBinder)

        viewModel.snooze()
        assertThat(viewModel.finished.peek()).isNotNull()
        assertThat(actionSlot.isCaptured).isTrue()
        assertThat(actionSlot.captured).isEqualTo(ACTION_ALARM_SNOOZE)
        assertThat(reminderIdSlot.captured).isEqualTo(reminder.id)
    }

    @Test
    fun skip_createsWorkerForSkip() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, this)
        serviceConnection.captured.onServiceConnected(mockk(), fakeBinder)

        viewModel.skip()
        assertThat(viewModel.finished.peek()).isNotNull()
        assertThat(actionSlot.isCaptured).isTrue()
        assertThat(actionSlot.captured).isEqualTo(ACTION_ALARM_SKIP)
        assertThat(reminderIdSlot.captured).isEqualTo(reminder.id)
    }

    @Test
    fun complete_createsWorkerForComplete() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, this)
        serviceConnection.captured.onServiceConnected(mockk(), fakeBinder)

        viewModel.complete()
        assertThat(viewModel.finished.peek()).isNotNull()
        assertThat(actionSlot.isCaptured).isTrue()
        assertThat(actionSlot.captured).isEqualTo(ACTION_ALARM_COMPLETE)
        assertThat(reminderIdSlot.captured).isEqualTo(reminder.id)
    }

    @Test
    fun autoSnoozesAfterDelay() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle, this)
        serviceConnection.captured.onServiceConnected(mockk(), fakeBinder)

        advanceTimeBy(DELAY_UNTIL_SNOOZE_MILLI + 1)

        assertThat(viewModel.finished.peek()).isNotNull()
        assertThat(actionSlot.isCaptured).isTrue()
        assertThat(actionSlot.captured).isEqualTo(ACTION_ALARM_SNOOZE)
        assertThat(reminderIdSlot.captured).isEqualTo(reminder.id)
    }
}