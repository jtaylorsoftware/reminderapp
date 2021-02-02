package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.local.FakeReminderLocalDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ReminderRepositoryTest {
    private lateinit var repository: ReminderRepository
    private lateinit var fakeLocalDataSource: ReminderDataSource

    private val localReminders = listOf(
        Reminder("Reminder 1", ReminderStatus.COMPLETED, 1),
        Reminder("Reminder 2", ReminderStatus.COMPLETED, 2)
    )

    @Before
    fun setup() {
        fakeLocalDataSource = FakeReminderLocalDataSource(localReminders.toMutableList())
        repository = ReminderRepositoryImpl(
            fakeLocalDataSource
        )
    }

    @Test
    fun getAll_returnsRemindersFromLocalDataSource() = runBlockingTest {
        val repoReminders = repository.getAll().first()

        assertThat(repoReminders).containsExactlyElementsIn(localReminders)
    }

    @Test
    fun findById_returnsReminderInLocalDataSource() = runBlockingTest {
        val reminder = localReminders[1]
        val foundReminder = repository.findById(reminder.id)

        assertThat(foundReminder).isEqualTo(reminder)
    }

    @Test
    fun save_addsReminderToLocalDataSource() = runBlockingTest {
        val reminder = Reminder("Reminder 3",  ReminderStatus.PENDING, 3)

        repository.save(reminder)

        val savedReminder = fakeLocalDataSource.findById(reminder.id)

        assertThat(savedReminder).isNotNull()
    }
}