package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.broadcast.cancelAlarmIntentsForReminder
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.FakeAlarmRepository
import com.github.jeremyt135.reminderapp.data.FakeReminderRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

@ExperimentalCoroutinesApi
class RemindersViewModelTest {
    private lateinit var fakeReminderRepository: ReminderRepository
    private lateinit var fakeAlarmRepository: AlarmRepository
    private lateinit var viewModel: RemindersViewModel
    private lateinit var reminders: List<Reminder>
    private lateinit var alarms: MutableList<Alarm>

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        reminders = listOf(
            Reminder("1"),
            Reminder("2")
        )
        alarms = mutableListOf()

        fakeReminderRepository =
            FakeReminderRepository(
                reminders.toMutableList(),
                alarms,
                mutableListOf()
            )
        fakeAlarmRepository = FakeAlarmRepository(alarms)
        val mockSharedPrefs = mockkClass(SharedPreferences::class, relaxed = true)

        every { mockSharedPrefs.edit() } returns mockk(relaxed = true)
        every {
            mockSharedPrefs.getString("FILTER_REMINDER_STATUS", any())
        } returns ReminderStatus.values().joinToString(",")
        every {
            mockSharedPrefs.getString("FILTER_REMINDER_DAYS", any())
        } returns DayOfWeek.values().joinToString(",")
        every {
            mockSharedPrefs.getBoolean("FILTER_REMINDER_TODAY", any())
        } returns true
        every { mockSharedPrefs.getString("SORT_REMINDER_TIME", "NONE") } returns "NONE"
        every { mockSharedPrefs.getString("SORT_REMINDER_DESCRIPTION", "NONE") } returns "NONE"

        mockkObject(AlarmService.Companion)
        justRun { AlarmService.requestStop(any()) }
        mockkObject(AlarmActionHandler.Companion)
        every { AlarmActionHandler.createForAction(any(), any()) } returns mockk(relaxed = true)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic(::cancelAlarmIntentsForReminder)
        justRun { cancelAlarmIntentsForReminder(any(), any()) }

        viewModel = RemindersViewModel(
            mockk(relaxed = true),
            fakeReminderRepository,
            fakeAlarmRepository,
            mockSharedPrefs,
            mainCoroutineRule.dispatcher
        )
    }

    @Test
    fun reminders_returnsRepoRemindersAsLiveData() = runBlockingTest(mainCoroutineRule.dispatcher)
    {
        val remindersWithNoHistory = reminders.map {
            ReminderAndHistory(
                reminder = it.copy(),
                history = null
            )
        }
        assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
            remindersWithNoHistory
        )
    }

    @Test
    fun addReminder_emitsNavigateToEditor() = runBlockingTest(mainCoroutineRule.dispatcher) {
        viewModel.addReminder()
        assertThat(viewModel.navigateToEditor.peek()).isNotNull()
    }

    @Test
    fun editReminder_emitsNavigateToEditorWithSameReminder() =
        runBlockingTest(mainCoroutineRule.dispatcher) {
            val reminder = reminders[0]
            viewModel.editReminder(reminder)
            assertThat(viewModel.navigateToEditor.peek()).isNotNull()
        }

    @Test
    fun `deleteReminder deletes reminder and reschedules`() = mainCoroutineRule.runBlockingTest {
        val reminder = reminders[0]
        val id = reminder.id
        val alarm = Alarm(id = 1, reminderId = id, scheduled = true)
        fakeAlarmRepository.save(alarm)

        viewModel.deleteReminder(reminder)

        assertThat(fakeReminderRepository.findById(id)).isNull()
        assertThat(fakeAlarmRepository.findById(1)).isNull()
        verify {
            AlarmService.requestStop(any())
            AlarmActionHandler.createForAction(any(), any())
            cancelAlarmIntentsForReminder(any(), reminder)
        }
    }

    @Test
    fun `deleteAllReminders removes all reminders and their alarms`() = mainCoroutineRule.runBlockingTest {
        val alarm = Alarm(id = 1, reminderId = reminders[0].id, scheduled = true)
        fakeAlarmRepository.save(alarm)

        viewModel.deleteAllReminders()

        fakeReminderRepository.getAll().collect {
            assertThat(it).isEmpty()
        }
        fakeAlarmRepository.getAll().collect {
            assertThat(it).isEmpty()
        }
    }
}
