package com.github.jeremyt135.reminderapp.screens.alarm

import android.content.Context
import android.content.ServiceConnection
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.ReminderApplication
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.github.jeremyt135.reminderapp.service.ReminderAlarmManager
import com.github.jeremyt135.reminderapp.util.EventFlow
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.getShortLocalTimeDisplay
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

    private lateinit var fakeBinder: ReminderAlarmManager.LocalBinder

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

        mockkObject(ReminderAlarmManager.Companion)
        serviceConnection = slot()
        every {
            ReminderAlarmManager.bind(
                any(),
                capture(serviceConnection),
                any(),
                any()
            )
        } returns Unit

        val snoozeTimeout = EventFlow<Unit>()
        fakeBinder = spyk(object : ReminderAlarmManager.LocalBinder(snoozeTimeout) {
            override fun snooze() {}
            override fun skip() {}
            override fun complete() {}
        })
    }

    @Test
    fun loadReminderData_loadsReminderAndAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle)

        coVerify {
            mockReminderRepo.getReminderAndAlarm(reminder.id)
        }
    }

    @Test
    fun liveData_matchesLoadedReminderData() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle)

        assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(reminder)
        assertThat(viewModel.alarm.getOrAwaitValue()).isEqualTo(alarm)

        assertThat(viewModel.time.getOrAwaitValue()).isEqualTo(time.getShortLocalTimeDisplay())
    }

    @Test
    fun snooze_callsScheduleSnoozeAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle)
        serviceConnection.captured.onServiceConnected(mockk(), fakeBinder)

        viewModel.snooze()
        assertThat(viewModel.finished.peek()).isNotNull()

        // Verify the snooze happened
        verify {
            fakeBinder.snooze()
        }
    }

    @Test
    fun skip_callsScheduleSkipAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle)
        serviceConnection.captured.onServiceConnected(mockk(), fakeBinder)

        viewModel.skip()
        assertThat(viewModel.finished.peek()).isNotNull()

        verify {
            fakeBinder.skip()
        }
    }

    @Test
    fun complete_callsScheduleCompleteAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID) } returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle)
        serviceConnection.captured.onServiceConnected(mockk(), fakeBinder)

        viewModel.complete()
        assertThat(viewModel.finished.peek()).isNotNull()

        verify {
            fakeBinder.complete()
        }
    }

}