package com.github.jeremyt135.reminderapp.screens.alarm

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.ReminderApplication
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.broadcast.scheduleCompleteAlarm
import com.github.jeremyt135.reminderapp.broadcast.scheduleSkipAlarm
import com.github.jeremyt135.reminderapp.broadcast.scheduleSnoozeAlarm
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

    lateinit var viewModel: AlarmViewModel

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    lateinit var time: LocalDateTime
    lateinit var reminder: Reminder
    lateinit var alarm: Alarm
    lateinit var reminderAndAlarm: ReminderAndAlarm

    private fun <T> verifyFlowEmitted(
        flow: SharedFlow<T>,
        coroutineScope: CoroutineScope,
        action: () -> Unit
    ) {
        var eventEmitted = false
        coroutineScope.launch {
            flow.collect {
                eventEmitted = true
                cancel()
            }
        }
        // Do action that emits event
        action()
        assertThat(eventEmitted).isTrue()
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        time = LocalDateTime.now().plusHours(2)
        reminder = Reminder(id = 1, time = time.toLocalTime())
        alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = time.toEpochMilli())
        reminderAndAlarm = ReminderAndAlarm(reminder, alarm)

        coEvery { mockReminderRepo.getReminderAndAlarm(reminder.id) } returns reminderAndAlarm
        every { mockApplication.applicationContext } returns mockContext
    }

    @Test
    fun loadReminderData_loadsReminderAndAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID)} returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle)

        coVerify {
            mockReminderRepo.getReminderAndAlarm(reminder.id)
        }
    }

    @Test
    fun liveData_matchesLoadedReminderData() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID)} returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle)

        assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(reminder)
        assertThat(viewModel.alarm.getOrAwaitValue()).isEqualTo(alarm)

        assertThat(viewModel.time.getOrAwaitValue()).isEqualTo(
            time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        )
    }

    @Test
    fun snooze_callsScheduleSnoozeAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID)} returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle)

        mockkStatic(::scheduleSnoozeAlarm)
        every { scheduleSnoozeAlarm(mockContext, any())} returns Unit

        verifyFlowEmitted(viewModel.finished, this) {
            viewModel.snooze()
        }

        // Verify the snooze happened
        verify {
            scheduleSnoozeAlarm(mockContext, any())
        }
    }

    @Test
    fun skip_callsScheduleSkipAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID)} returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle)

        mockkStatic(::scheduleSkipAlarm)
        every { scheduleSkipAlarm(mockContext, any()) } returns Unit

        verifyFlowEmitted(viewModel.finished, this) {
            viewModel.skip()
        }

        verify {
            scheduleSkipAlarm(mockContext, any())
        }
    }

    @Test
    fun complete_callsScheduleCompleteAlarm() = mainCoroutineRule.runBlockingTest {
        every { mockSavedStateHandle.get<Long>(EXTRA_REMINDER_ID)} returns reminder.id
        viewModel =
            AlarmViewModel(mockApplication, mockReminderRepo, mockSavedStateHandle)

        mockkStatic(::scheduleCompleteAlarm)
        every { scheduleCompleteAlarm(mockContext, any()) } returns Unit

        verifyFlowEmitted(viewModel.finished, this) {
            viewModel.complete()
        }

        verify {
            scheduleCompleteAlarm(mockContext, any())
        }
    }

}