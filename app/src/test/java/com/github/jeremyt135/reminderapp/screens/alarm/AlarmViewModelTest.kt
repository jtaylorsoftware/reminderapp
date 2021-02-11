package com.github.jeremyt135.reminderapp.screens.alarm

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.ReminderApplication
import com.github.jeremyt135.reminderapp.broadcast.SNOOZE_TIME_MIN
import com.github.jeremyt135.reminderapp.broadcast.cancelAlarm
import com.github.jeremyt135.reminderapp.broadcast.makeSnoozeToast
import com.github.jeremyt135.reminderapp.broadcast.scheduleAlarm
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
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
    lateinit var mockAlarmRepo: AlarmRepository

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

    private fun <T> verifyFlowEmitted(flow: SharedFlow<T>, coroutineScope: CoroutineScope, action: () -> Unit) {
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

        viewModel = AlarmViewModel(mockApplication, mockReminderRepo, mockAlarmRepo)

        coEvery { mockReminderRepo.getReminderAndAlarm(reminder.id) } returns reminderAndAlarm
        every { mockApplication.applicationContext } returns mockContext
    }

    @Test
    fun loadReminderData_loadsReminderAndAlarm() = mainCoroutineRule.runBlockingTest {
        viewModel.loadReminderData(reminder.id)

        coVerify {
            mockReminderRepo.getReminderAndAlarm(reminder.id)
        }
    }

    @Test
    fun liveData_matchesLoadedReminderData() = mainCoroutineRule.runBlockingTest {
        viewModel.loadReminderData(reminder.id)

        assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(reminder)
        assertThat(viewModel.alarm.getOrAwaitValue()).isEqualTo(alarm)

        assertThat(viewModel.time.getOrAwaitValue()).isEqualTo(
            time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        )
    }

    @Test
    fun snooze_cancelsAndSchedulesNewAlarm() = mainCoroutineRule.runBlockingTest {
        viewModel.loadReminderData(reminder.id)


        val nextAlarmTime =
            alarm.triggerAt.toLocalDateTime().plusMinutes(SNOOZE_TIME_MIN).toEpochMilli()
        val nextAlarm = alarm.copy(triggerAt = nextAlarmTime)

        coEvery { mockAlarmRepo.save(nextAlarm) } returns nextAlarm.id

        mockkStatic(::cancelAlarm, ::scheduleAlarm, ::makeSnoozeToast)
        every { makeSnoozeToast(mockContext) } returns Unit
        every { cancelAlarm(mockContext, alarm) } returns Unit
        every { scheduleAlarm(mockContext, nextAlarm) } returns Unit

        verifyFlowEmitted(viewModel.finished, this) {
            viewModel.snooze()
        }

        // Verify the snooze happened
        coVerifyOrder {
            cancelAlarm(mockContext, alarm)
            mockAlarmRepo.save(nextAlarm)
            scheduleAlarm(mockContext, nextAlarm)
            makeSnoozeToast(mockContext)
        }

    }

    @Test
    fun skip_savesReminderWithStatusSkipped() = mainCoroutineRule.runBlockingTest {
        viewModel.loadReminderData(reminder.id)

        val skippedReminder = reminder.copy(status = ReminderStatus.SKIPPED)

        coEvery { mockReminderRepo.save(skippedReminder) } returns skippedReminder.id

        verifyFlowEmitted(viewModel.finished, this) {
            viewModel.skip()
        }

        coVerify {
            mockReminderRepo.save(skippedReminder)
        }

    }

    @Test
    fun complete_savesReminderWithStatusCompleted() = mainCoroutineRule.runBlockingTest {
        viewModel.loadReminderData(reminder.id)

        val completedReminder = reminder.copy(status = ReminderStatus.COMPLETED)

        coEvery { mockReminderRepo.save(completedReminder) } returns completedReminder.id

        verifyFlowEmitted(viewModel.finished, this) {
            viewModel.complete()
        }

        coVerify {
            mockReminderRepo.save(completedReminder)
        }
    }

}