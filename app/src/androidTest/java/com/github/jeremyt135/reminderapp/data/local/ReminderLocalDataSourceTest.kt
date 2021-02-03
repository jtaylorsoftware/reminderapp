package com.github.jeremyt135.reminderapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.MediumTest
import com.github.jeremyt135.reminderapp.util.InMemoryDatabaseRule
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
@HiltAndroidTest
@MediumTest
class ReminderLocalDataSourceTest {
    private lateinit var localDataSource: ReminderLocalDataSource

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var inMemoryDatabaseRule = InMemoryDatabaseRule()

    @Before
    fun setup() {
        val reminderDao = inMemoryDatabaseRule.db.reminderDao()
        localDataSource = ReminderLocalDataSource(reminderDao)
    }

    @Test
    fun getAll_returnsSavedReminders() = runBlockingTest {
        val reminders = (1..3).map { i ->
            Reminder(
                "Reminder $i",
                ReminderStatus.COMPLETED,
                id = i.toLong()
            )
        }
        reminders.forEach {
            localDataSource.save(it)
        }

        val savedReminders = localDataSource.getAll().first()

        assertThat(savedReminders).containsExactlyElementsIn(reminders)
    }

    @Test
    fun findById_returnsSavedReminder() = runBlockingTest{
        val reminder = Reminder("My reminder", ReminderStatus.COMPLETED, id =1)

        localDataSource.save(reminder)

        val savedReminder = localDataSource.findById(reminder.id)

        assertThat(savedReminder).isEqualTo(reminder)
    }
}