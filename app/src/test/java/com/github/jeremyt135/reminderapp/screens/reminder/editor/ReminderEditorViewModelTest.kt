package com.github.jeremyt135.reminderapp.screens.reminder.editor

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.domain.emptyReminder
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ReminderEditorViewModelTest {
    @MockK
    lateinit var mockRepository: ReminderRepository

    @MockK
    lateinit var mockSavedState: SavedStateHandle

    lateinit var viewModel: ReminderEditorViewModel

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun reminderLiveData_isInitializedToEmptyReminder() = runBlockingTest(mainCoroutineRule.dispatcher) {
        // Simulate that the editor is opened with a value of 0L for reminderId argument
        every { mockSavedState.get<Long?>("reminderId") } returns 0L

        viewModel = ReminderEditorViewModel(mockRepository, mockSavedState)

        assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(emptyReminder())
    }

    @Test
    fun reminderLiveData_withReminderId_loadsAssociatedReminder() = runBlockingTest(mainCoroutineRule.dispatcher) {
        // Simulate a reminder that has already been persisted so it can be found later
        val reminder = Reminder("My Reminder", ReminderStatus.PENDING, 1)
        coEvery { mockRepository.findById(reminder.id) } returns reminder

        // Simulate editor is navigated to with the reminderId
        every { mockSavedState.get<Long?>("reminderId") } returns reminder.id

        viewModel = ReminderEditorViewModel(mockRepository, mockSavedState)

        assertThat(viewModel.reminder.getOrAwaitValue()).isEqualTo(reminder)
    }
}