package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
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
        Reminder("Reminder 1", id = 1),
        Reminder("Reminder 2", id = 2)
    )

    private val alarms = localReminders.mapIndexed { i, reminder ->
        Alarm(id = i.toLong() + 1, reminderId = reminder.id, triggerAt = 0)
    }

    @Before
    fun setup() {
        fakeLocalDataSource = FakeReminderLocalDataSource(localReminders.toMutableList(), alarms)
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
        val reminder = Reminder("Reminder 3", id =3)

        repository.save(reminder)

        val savedReminder = fakeLocalDataSource.findById(reminder.id)

        assertThat(savedReminder).isNotNull()
    }

    @Test
    fun getReminderAndAlarm_returnsReminderWithAlarm() = runBlockingTest {
        val (reminder, alarm) = repository.getReminderAndAlarm(localReminders[0].id)!!
        assertThat(reminder).isEqualTo(localReminders[0])
        assertThat(alarm).isEqualTo(alarms[0])
    }

    @Test
    fun delete_removesReminder() = runBlockingTest {
        val reminder = localReminders[0]

        repository.delete(reminder)

        assertThat(repository.findById(reminder.id)).isNull()
    }
}