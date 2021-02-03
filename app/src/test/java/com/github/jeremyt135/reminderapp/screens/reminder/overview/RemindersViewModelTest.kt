package com.github.jeremyt135.reminderapp.screens.reminder.overview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.FakeReminderRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class RemindersViewModelTest {
    private lateinit var fakeReminderRepository: ReminderRepository
    private lateinit var viewModel: RemindersViewModel
    private val reminders = listOf(
        Reminder("1", ReminderStatus.PENDING, id = 1),
        Reminder("2", ReminderStatus.COMPLETED, id = 2)
    )

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        fakeReminderRepository =
            FakeReminderRepository(reminders.toMutableList())
        viewModel = RemindersViewModel(fakeReminderRepository)
    }

    @Test
    fun reminders_returnsRepoRemindersAsLiveData() = runBlockingTest(mainCoroutineRule.dispatcher)
    {
        assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(reminders)
    }

    @Test
    fun addReminder_emitsNavigateToEditor() = runBlockingTest {
        val events = mutableListOf<Reminder?>()
        launch {
            viewModel.navigateToEditor.collect {
                events.add(it)
                cancel()
            }
        }
        viewModel.addReminder()
        assertThat(events).hasSize(1)
    }

    @Test
    fun editReminder_emitsNavigateToEditorWithSameReminder() = runBlockingTest {
        val events = mutableListOf<Reminder?>()
        launch {
            viewModel.navigateToEditor.collect {
                events.add(it)
                cancel()
            }
        }
        val reminder = reminders[0]
        viewModel.editReminder(reminder)
        assertThat(events).hasSize(1)
        assertThat(events[0]).isEqualTo(reminder)
    }
}
