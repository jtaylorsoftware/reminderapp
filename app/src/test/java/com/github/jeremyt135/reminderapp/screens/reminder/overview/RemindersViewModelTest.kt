package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.FakeReminderRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

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

        viewModel = RemindersViewModel(
            fakeReminderRepository,
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
}
