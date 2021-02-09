package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.ReminderApplication
//import com.github.jeremyt135.reminderapp.broadcast.cancelAlarm
import com.github.jeremyt135.reminderapp.broadcast.scheduleAlarmForReminder
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@ExperimentalCoroutinesApi
class ReminderEditorViewModelTest {
    @MockK
    lateinit var mockReminderRepo: ReminderRepository

    @MockK
    lateinit var mockAlarmRepo: AlarmRepository

    @MockK
    lateinit var mockApplication: ReminderApplication

    @MockK
    lateinit var mockContext: Context

    @MockK
    lateinit var mockSavedState: SavedStateHandle

    lateinit var viewModel: ReminderEditorViewModel

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun isEditing_withNoId_IsFalse() = runBlockingTest(mainCoroutineRule.dispatcher) {
        every { mockSavedState.get<Long?>("reminderId") } returns 0L

        viewModel = ReminderEditorViewModel(
            mockApplication,
            mockReminderRepo,
            mockAlarmRepo,
            mockSavedState
        )

        assertThat(viewModel.isEditing).isFalse()
    }

    @Test
    fun isEditing_withId_IsTrue() = runBlockingTest(mainCoroutineRule.dispatcher) {
        // Open with existing Reminder
        val reminder = Reminder("My Reminder", ReminderStatus.PENDING, id = 1)
        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
        every { mockSavedState.get<Long?>("reminderId") } returns 1L

        viewModel = ReminderEditorViewModel(
            mockApplication,
            mockReminderRepo,
            mockAlarmRepo,
            mockSavedState
        )

        assertThat(viewModel.isEditing).isTrue()
    }

    @Test
    fun reminderLiveData_isInitializedToEmptyReminder() =
        runBlockingTest(mainCoroutineRule.dispatcher) {
            // Simulate that the editor is opened with a value of 0L for reminderId argument
            every { mockSavedState.get<Long?>("reminderId") } returns 0L

            viewModel = ReminderEditorViewModel(
                mockApplication,
                mockReminderRepo,
                mockAlarmRepo,
                mockSavedState
            )

            assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(Reminder())
        }

    @Test
    fun reminderLiveData_withReminderId_loadsAssociatedReminder() =
        runBlockingTest(mainCoroutineRule.dispatcher) {
            // Simulate a reminder that has already been persisted so it can be found later
            val reminder = Reminder("My Reminder", ReminderStatus.PENDING, id = 1)
            coEvery { mockReminderRepo.findById(reminder.id) } returns reminder

            // Simulate editor is navigated to with the reminderId
            every { mockSavedState.get<Long?>("reminderId") } returns reminder.id

            viewModel = ReminderEditorViewModel(
                mockApplication,
                mockReminderRepo,
                mockAlarmRepo,
                mockSavedState
            )

            assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(reminder)
        }

    @Test
    fun saveReminder_savesToRepository() = runBlockingTest(mainCoroutineRule.dispatcher) {
        val reminder = Reminder()
        val alarm = Alarm(reminderId = reminder.id, triggerAt = 0)

        every { mockSavedState.get<Long?>("reminderId") } returns 0L
        coEvery { mockReminderRepo.save(reminder) } returns 0L
        coEvery { mockAlarmRepo.save(alarm) } returns 0L
        coEvery { mockAlarmRepo.findByReminderId(0) } returns null
        every { mockApplication.applicationContext } returns mockContext

        viewModel = ReminderEditorViewModel(
            mockApplication,
            mockReminderRepo,
            mockAlarmRepo,
            mockSavedState
        )

        val today = LocalDate.now().dayOfWeek
        mockkStatic(::scheduleAlarmForReminder)
        every { scheduleAlarmForReminder(mockContext, reminder, today) } returns null

        // Save the viewModel's reminder, which is empty
        viewModel.saveReminder()

        // Verify save was called
        coVerifyAll { mockReminderRepo.save(reminder) }
    }

    @Test
    fun saveReminder_cancelsExistingAlarm() = runBlockingTest(mainCoroutineRule.dispatcher) {
        val reminder = Reminder()
        val alarm = Alarm(reminderId = reminder.id, triggerAt = 0)

        every { mockSavedState.get<Long?>("reminderId") } returns 0L
        coEvery { mockReminderRepo.save(reminder) } returns 0L
        coEvery { mockAlarmRepo.save(alarm) } returns 0L
        coEvery { mockAlarmRepo.findByReminderId(0) } returns alarm
        coEvery { mockAlarmRepo.delete(alarm) } returns Unit
        every { mockApplication.applicationContext } returns mockContext

        viewModel = ReminderEditorViewModel(
            mockApplication,
            mockReminderRepo,
            mockAlarmRepo,
            mockSavedState
        )

        val today = LocalDate.now().dayOfWeek
        mockkStatic(::scheduleAlarmForReminder)
        every { scheduleAlarmForReminder(mockContext, reminder, today) } returns null
//        every { cancelAlarm(mockContext, alarm) } returns Unit

        // Save the viewModel's reminder, which is empty
        viewModel.saveReminder()

        // Verify cancel was called
//        coVerify { cancelAlarm(mockContext, alarm) }
        // Verify delete was called
        coVerify { mockAlarmRepo.delete(alarm) }
    }

    @Test
    fun saveReminder_schedulesAlarm() = runBlockingTest(mainCoroutineRule.dispatcher) {
        val reminder = Reminder()
        val alarm = Alarm(reminderId = reminder.id, triggerAt = 0)

        every { mockSavedState.get<Long?>("reminderId") } returns 0L
        coEvery { mockReminderRepo.save(reminder) } returns 0L
        coEvery { mockAlarmRepo.save(alarm) } returns 0L
        coEvery { mockAlarmRepo.findByReminderId(0) } returns null
        every { mockApplication.applicationContext } returns mockContext

        viewModel = ReminderEditorViewModel(
            mockApplication,
            mockReminderRepo,
            mockAlarmRepo,
            mockSavedState
        )

        val today = LocalDate.now().dayOfWeek
        mockkStatic(::scheduleAlarmForReminder)
        every { scheduleAlarmForReminder(mockContext, reminder, today) } returns null

        // Save the viewModel's reminder, which is empty
        viewModel.saveReminder()

        // Verify schedule was called
        coVerify { scheduleAlarmForReminder(mockContext, reminder, today) }
    }


    @Test
    fun saveReminder_emitsNavigateToReminders() = runBlockingTest(mainCoroutineRule.dispatcher) {
        val reminder = Reminder()
        val alarm = Alarm(reminderId = reminder.id, triggerAt = 0)

        every { mockSavedState.get<Long?>("reminderId") } returns 0L
        coEvery { mockReminderRepo.save(reminder) } returns 0L
        coEvery { mockAlarmRepo.save(alarm) } returns 0L
        coEvery { mockAlarmRepo.findByReminderId(0) } returns null
        every { mockApplication.applicationContext } returns mockContext

        viewModel = ReminderEditorViewModel(
            mockApplication,
            mockReminderRepo,
            mockAlarmRepo,
            mockSavedState
        )

        var eventEmitted = false
        launch {
            viewModel.navigateToReminders.collect {
                eventEmitted = true
                cancel()
            }
        }

        val today = LocalDate.now().dayOfWeek
        mockkStatic(::scheduleAlarmForReminder)
        every { scheduleAlarmForReminder(mockContext, reminder, today) } returns null

        // Trigger navigation
        viewModel.saveReminder()

        assertThat(eventEmitted).isTrue()
    }
}