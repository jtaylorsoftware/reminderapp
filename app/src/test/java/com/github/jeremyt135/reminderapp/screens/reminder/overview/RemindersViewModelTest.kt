package com.github.jeremyt135.reminderapp.screens.reminder.overview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.FakeReminderRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class RemindersViewModelTest {
    private lateinit var fakeReminderRepository: ReminderRepository
    private lateinit var viewModel: RemindersViewModel
    private val reminders = listOf(
        Reminder("1"),
        Reminder("2")
    )
    private val alarms = listOf<Alarm>()
    private val history = listOf<History>()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        fakeReminderRepository =
            FakeReminderRepository(
                reminders.toMutableList(),
                alarms.toMutableList(),
                history.toMutableList()
            )
        viewModel = RemindersViewModel(fakeReminderRepository)
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
    fun addReminder_emitsNavigateToEditor() = runBlockingTest {
        viewModel.addReminder()
        assertThat(viewModel.navigateToEditor.peek()).isNotNull()
    }

    @Test
    fun editReminder_emitsNavigateToEditorWithSameReminder() = runBlockingTest {
        val reminder = reminders[0]
        viewModel.editReminder(reminder)
        assertThat(viewModel.navigateToEditor.peek()).isNotNull()
    }
}
