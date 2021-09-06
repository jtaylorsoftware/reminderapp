package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.ReminderApplication
import com.github.jeremyt135.reminderapp.broadcast.ACTION_ALARM_SCHEDULE_NEXT
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.broadcast.cancelAlarmIntentsForReminder
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.SchedulingRules
import com.github.jeremyt135.reminderapp.data.domain.SchedulingType
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

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
            mockSavedState,
            mainCoroutineRule
        )
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val mockWorkManager = mockkClass(WorkManager::class)
        every { mockWorkManager.enqueue(any<WorkRequest>()) } returns mockkClass(Operation::class)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkObject(AlarmService.Companion)
        justRun { AlarmService.requestStop(any()) }

        mockkObject(AlarmActionHandler.Companion)
        every {
            AlarmActionHandler.createForAction(any(), any())
        } returns OneTimeWorkRequestBuilder<AlarmActionHandler>().build()
    }

    @Test
    fun isEditing_withNoId_IsFalse() = mainCoroutineRule.runBlockingTest {
        every { mockSavedState.get<String>(EXTRA_REMINDER_ID) } returns ""

        createViewModel()

        assertThat(viewModel.isEditing).isFalse()
    }

    @Test
    fun isEditing_withId_IsTrue() = mainCoroutineRule.runBlockingTest {
        // Open with existing Reminder
        val reminder = Reminder("My Reminder")
        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
        every { mockSavedState.get<String>(EXTRA_REMINDER_ID) } returns reminder.id

        createViewModel()

        assertThat(viewModel.isEditing).isTrue()
    }

    @Test
    fun reminderLiveData_isInitializedToEmptyReminder() = mainCoroutineRule.runBlockingTest {
        // Simulate that the editor is opened with a value of 0L for reminderId argument
        every { mockSavedState.get<String>(EXTRA_REMINDER_ID) } returns ""

        createViewModel()

        assertThat(viewModel.reminder.value.copy(id = "")).isEqualTo(
            Reminder(
                id = "",
                schedulingRules = SchedulingRules(type = SchedulingType.REPEAT_FOREVER)
            )
        )
    }

    @Test
    fun reminderLiveData_withReminderId_loadsReminder() = mainCoroutineRule.runBlockingTest {
        // Simulate a reminder that has already been persisted so it can be found later
        val reminder = Reminder("My Reminder")
        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder

        // Simulate editor is navigated to with the reminderId
        every { mockSavedState.get<String>(EXTRA_REMINDER_ID) } returns reminder.id

        createViewModel()

        assertThat(viewModel.reminder.value).isEqualTo(reminder)
    }

    @Test
    fun saveReminder_savesToRepository_schedulesAlarm() = mainCoroutineRule.runBlockingTest {
        val reminder =
            Reminder(schedulingRules = SchedulingRules(time = LocalTime.now().plusMinutes(1)))
        val savedReminder = slot<Reminder>()

        val alarm = Alarm(id = 1, reminderId = reminder.id, scheduled = true)
        val nextAlarm = slot<Alarm>()

        every { mockSavedState.get<String>(EXTRA_REMINDER_ID) } returns reminder.id

        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
        coJustRun { mockReminderRepo.save(capture(savedReminder)) }
        coEvery { mockAlarmRepo.findByReminderId(reminder.id) } returns alarm
        coJustRun { mockAlarmRepo.delete(alarm) }
        coEvery { mockAlarmRepo.save(capture(nextAlarm)) } returns alarm.id + 1
        every { mockApplication.applicationContext } returns mockContext

        mockkStatic(::cancelAlarmIntentsForReminder)
        every { cancelAlarmIntentsForReminder(mockApplication, any()) } returns Unit

        createViewModel()

        // Save the viewModel's reminder, which is empty
        viewModel.saveReminder()

        coVerify {
            // Verify reminder is saved
            mockReminderRepo.save(any())

            // Should cancel previous by finding old alarm, cancelling, and deleting
            mockAlarmRepo.findByReminderId(reminder.id)
            cancelAlarmIntentsForReminder(mockApplication, reminder)
            mockAlarmRepo.delete(alarm)

            // Should save and schedule the next available alarm
            mockAlarmRepo.save(any())
            AlarmService.requestStop(mockApplication)
            AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, "")
        }

        assertThat(savedReminder.captured.id).isEqualTo(reminder.id)

        assertThat(nextAlarm.captured.reminderId).isEqualTo(reminder.id)
        assertThat(nextAlarm.captured.triggerAt).isEqualTo(
            currentLocalDateTime()
                .withHour(reminder.schedulingRules.hour)
                .withMinute(reminder.schedulingRules.minute)
        )
    }

    @Test
    fun saveReminder_emitsNavigateToReminders() = mainCoroutineRule.runBlockingTest {
        val reminder =
            Reminder(schedulingRules = SchedulingRules(time = LocalTime.now().minusMinutes(1)))

        every { mockSavedState.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
        coJustRun { mockReminderRepo.save(reminder) }
        coEvery { mockAlarmRepo.save(any()) } returns 0L
        coEvery { mockAlarmRepo.findByReminderId(reminder.id) } returns null
        every { mockApplication.applicationContext } returns mockContext
        mockkStatic(::cancelAlarmIntentsForReminder)
        every { cancelAlarmIntentsForReminder(mockApplication, any()) } returns Unit

        createViewModel()

        // Trigger navigation
        viewModel.saveReminder()

        // Verify
        assertThat(viewModel.navigateToReminders.value).isNotNull()
    }

    @Test
    fun deleteReminder_notEditing_returns() = mainCoroutineRule.runBlockingTest {
        every { mockSavedState.get<String>(EXTRA_REMINDER_ID) } returns ""
        coEvery { mockReminderRepo.delete(any()) } returns Unit

        createViewModel()
        viewModel.deleteReminder()

        coVerify(exactly = 0) {
            mockReminderRepo.delete(any())
        }
    }

    @Test
    fun deleteReminder_cancelsAlarms_andEmitsNavigateToReminders() =
        mainCoroutineRule.runBlockingTest {
            val reminder = Reminder()
            val alarm = Alarm(id = 1, reminderId = reminder.id, scheduled = true)

            every { mockSavedState.get<String>(EXTRA_REMINDER_ID) } returns reminder.id
            coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
            coEvery { mockAlarmRepo.findByReminderId(reminder.id) } returns alarm
            coEvery { mockReminderRepo.delete(reminder) } returns Unit
            coEvery { mockAlarmRepo.delete(alarm) } returns Unit
            every { mockApplication.applicationContext } returns mockContext

            mockkStatic(::cancelAlarmIntentsForReminder)
            every { cancelAlarmIntentsForReminder(mockApplication, any()) } returns Unit

            createViewModel()

            // Trigger navigation
            viewModel.deleteReminder()
            assertThat(viewModel.navigateToReminders.value).isNotNull()

            coVerify {
                mockAlarmRepo.findByReminderId(reminder.id)
                cancelAlarmIntentsForReminder(mockApplication, any())
                mockAlarmRepo.delete(alarm)
                AlarmService.requestStop(mockApplication)
                mockReminderRepo.delete(reminder)
                AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, "")
            }
        }
}