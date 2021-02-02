package com.github.jeremyt135.reminderapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.util.InMemoryDatabaseRule
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.domain.asEntity
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
@SmallTest
class ReminderDaoTest {
    private lateinit var reminderDao: ReminderDao

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var inMemoryDatabaseRule = InMemoryDatabaseRule()

    @Before
    fun setup() {
        reminderDao = inMemoryDatabaseRule.db.reminderDao()
    }

    @Test
    fun getAll_returnsInsertedReminders() = runBlockingTest {
        val reminders = (1..3).map { i ->
            Reminder(
                "Reminder $i",
                ReminderStatus.COMPLETED,
                i.toLong()
            ).asEntity()
        }
        reminders.forEach {
            reminderDao.insert(it)
        }

        val dbReminders = reminderDao.getAll().first()

        assertThat(dbReminders).containsExactlyElementsIn(reminders)
    }

    @Test
    fun findById_returnsInsertedReminder() = runBlockingTest {
        val reminder = Reminder("Reminder 1", ReminderStatus.SKIPPED, 1).asEntity()
         reminderDao.insert(reminder)

        val dbReminder = reminderDao.findById(reminder.id)

        assertThat(dbReminder).isEqualTo(reminder)
    }

    @Test
    fun findById_emptyDb_returnsNull() = runBlockingTest {
        val dbReminder = reminderDao.findById(1L)

        assertThat(dbReminder).isNull()
    }
}