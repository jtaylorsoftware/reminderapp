package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.ReminderApplication
import com.github.jeremyt135.reminderapp.broadcast.cancelAlarm
import com.github.jeremyt135.reminderapp.broadcast.cancelNotification
import com.github.jeremyt135.reminderapp.broadcast.scheduleAlarmForReminder
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.github.jeremyt135.reminderapp.util.verifyFlowEmitted
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    // Helper method to create VM after unique test setup
    private fun createViewModel() {
        viewModel = ReminderEditorViewModel(
            mockApplication,
            mockReminderRepo,
            mockAlarmRepo,
            mockSavedState
        )
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun isEditing_withNoId_IsFalse() = mainCoroutineRule.runBlockingTest {
        every { mockSavedState.get<Long?>("reminderId") } returns 0L

        createViewModel()

        assertThat(viewModel.isEditing).isFalse()
    }

    @Test
    fun isEditing_withId_IsTrue() = mainCoroutineRule.runBlockingTest {
        // Open with existing Reminder
        val reminder = Reminder("My Reminder", ReminderStatus.PENDING, id = 1)
        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
        every { mockSavedState.get<Long?>("reminderId") } returns 1L

        createViewModel()

        assertThat(viewModel.isEditing).isTrue()
    }

    @Test
    fun reminderLiveData_isInitializedToEmptyReminder() = mainCoroutineRule.runBlockingTest {
        // Simulate that the editor is opened with a value of 0L for reminderId argument
        every { mockSavedState.get<Long?>("reminderId") } returns 0L

        createViewModel()

        assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(Reminder())
    }

    @Test
    fun reminderLiveData_withReminderId_loadsReminder() = mainCoroutineRule.runBlockingTest {
        // Simulate a reminder that has already been persisted so it can be found later
        val reminder = Reminder("My Reminder", ReminderStatus.PENDING, id = 1)
        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder

        // Simulate editor is navigated to with the reminderId
        every { mockSavedState.get<Long?>("reminderId") } returns reminder.id

        createViewModel()

        assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(reminder)
    }

    @Test
    fun saveReminder_savesToRepository() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        val alarm = Alarm(reminderId = reminder.id, triggerAt = 0)

        every { mockSavedState.get<Long?>("reminderId") } returns 0L
        coEvery { mockReminderRepo.save(reminder) } returns 0L
        coEvery { mockAlarmRepo.save(alarm) } returns 0L
        coEvery { mockAlarmRepo.findByReminderId(0) } returns null
        every { mockApplication.applicationContext } returns mockContext

        val today = LocalDate.now().dayOfWeek
        mockkStatic(::scheduleAlarmForReminder)
        every { scheduleAlarmForReminder(mockContext, reminder, today) } returns null

        createViewModel()

        // Save the viewModel's reminder, which is empty
        viewModel.saveReminder()

        // Verify save was called
        coVerify { mockReminderRepo.save(reminder) }
    }

    @Test
    fun saveReminder_cancelsExistingAlarm() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        val alarm = Alarm(reminderId = reminder.id, triggerAt = 0)

        every { mockSavedState.get<Long?>("reminderId") } returns 0L
        coEvery { mockReminderRepo.save(reminder) } returns 0L
        coEvery { mockAlarmRepo.save(alarm) } returns 0L
        coEvery { mockAlarmRepo.findByReminderId(0) } returns alarm
        coEvery { mockAlarmRepo.delete(alarm) } returns Unit
        every { mockApplication.applicationContext } returns mockContext

        val today = LocalDate.now().dayOfWeek
        mockkStatic(::scheduleAlarmForReminder, ::cancelAlarm)
        every { scheduleAlarmForReminder(mockContext, reminder, today) } returns null
        every { cancelAlarm(mockContext, alarm) } returns Unit
        every { cancelNotification(mockContext, reminder.id) } returns Unit


        createViewModel()

        // Save the viewModel's reminder, which is empty
        viewModel.saveReminder()

        // Verify operations
        coVerify {
            cancelNotification(mockContext, reminder.id)
            cancelAlarm(mockContext, alarm)
            mockAlarmRepo.delete(alarm)
        }
    }

    @Test
    fun saveReminder_schedulesAlarm() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        val alarm = Alarm(reminderId = reminder.id, triggerAt = 0)

        every { mockSavedState.get<Long?>("reminderId") } returns 0L
        coEvery { mockReminderRepo.save(reminder) } returns 0L
        coEvery { mockAlarmRepo.save(alarm) } returns 0L
        coEvery { mockAlarmRepo.findByReminderId(0) } returns null
        every { mockApplication.applicationContext } returns mockContext

        val today = LocalDate.now().dayOfWeek
        mockkStatic(::scheduleAlarmForReminder)
        every { scheduleAlarmForReminder(mockContext, reminder, today) } returns null

        createViewModel()

        // Save the viewModel's reminder, which is empty
        viewModel.saveReminder()

        // Verify schedule was called
        coVerify { scheduleAlarmForReminder(mockContext, reminder, today) }
    }


    @Test
    fun saveReminder_emitsNavigateToReminders() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()
        val alarm = Alarm(reminderId = reminder.id, triggerAt = 0)

        every { mockSavedState.get<Long?>("reminderId") } returns 0L
        coEvery { mockReminderRepo.save(reminder) } returns 0L
        coEvery { mockAlarmRepo.save(alarm) } returns 0L
        coEvery { mockAlarmRepo.findByReminderId(0) } returns null
        every { mockApplication.applicationContext } returns mockContext

        val today = LocalDate.now().dayOfWeek
        mockkStatic(::scheduleAlarmForReminder)
        every { scheduleAlarmForReminder(mockContext, reminder, today) } returns null

        createViewModel()

        // Trigger navigation
        verifyFlowEmitted(viewModel.navigateToReminders, this) {
            viewModel.saveReminder()
        }
    }

    @Test
    fun deleteReminder_notEditing_returns() = mainCoroutineRule.runBlockingTest {
        every { mockSavedState.get<Long?>("reminderId") } returns 0L
        coEvery { mockReminderRepo.delete(any()) } returns Unit

        createViewModel()
        viewModel.deleteReminder()

        coVerify(exactly = 0){
            mockReminderRepo.delete(any())
        }
    }

    @Test
    fun deleteReminder_cancelsAlarms_andEmitsNavigateToReminders() =
        mainCoroutineRule.runBlockingTest {
            val reminder = Reminder(id = 1)
            val alarm = Alarm(id = 1, reminderId = 1, triggerAt = 0)

            every { mockSavedState.get<Long?>("reminderId") } returns reminder.id
            coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
            coEvery { mockAlarmRepo.findByReminderId(reminder.id) } returns alarm
            coEvery { mockReminderRepo.delete(reminder) } returns Unit
            coEvery { mockAlarmRepo.delete(alarm) } returns Unit
            coEvery { mockApplication.applicationContext } returns mockContext

            mockkStatic(::cancelAlarm, ::cancelNotification)
            every { cancelAlarm(mockContext, alarm) } returns Unit
            every { cancelNotification(mockContext, reminder.id) } returns Unit

            createViewModel()

            verifyFlowEmitted(viewModel.navigateToReminders, this) {
                viewModel.deleteReminder()
            }

            coVerifyOrder {
                cancelNotification(mockContext, reminder.id)
                cancelAlarm(mockContext, alarm)
                mockAlarmRepo.delete(alarm)
                mockReminderRepo.delete(reminder)
            }
        }
}