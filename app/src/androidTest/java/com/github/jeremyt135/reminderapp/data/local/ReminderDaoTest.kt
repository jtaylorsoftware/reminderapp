package com.github.jeremyt135.reminderapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.util.InMemoryDatabaseRule
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@ExperimentalCoroutinesApi
@HiltAndroidTest
@SmallTest
class ReminderDaoTest {
    private lateinit var reminderDao: ReminderDao
    private lateinit var alarmDao: AlarmDao
    private lateinit var historyDao: HistoryDao

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var inMemoryDatabaseRule = InMemoryDatabaseRule()

    @Before
    fun setup() {
        reminderDao = inMemoryDatabaseRule.db.reminderDao()
        alarmDao = inMemoryDatabaseRule.db.alarmDao()
        historyDao = inMemoryDatabaseRule.db.historyDao()
    }

    @Test
    fun getAllWithHistory_calculatesCompletionStatus_returnsAllReminders() = runBlockingTest {
        val reminders = (1..5).map { i ->
            Reminder(
                "Reminder $i",
                status = if (i >= 2) ReminderStatus.COMPLETED else ReminderStatus.PENDING,
                id = i.toLong()
            ).asDataModel()
        }
        reminders.forEach {
            reminderDao.insert(it)
        }
        val historyEntries = (1..3).map { i ->
            History(
                id = i.toLong(),
                reminderId = reminders[i - 1].id,
                description = reminders[i - 1].description,
                completionStatus = ReminderStatus.COMPLETED,
                completedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
            ).asDataModel()
        }
        historyEntries.forEach {
            historyDao.insert(it)
        }
        val dbReminders = reminderDao.getAllWithTodayHistory().first()

        assertThat(dbReminders).hasSize(reminders.size)

        dbReminders.forEach {
            val history =
                historyEntries.firstOrNull { history -> history.reminderId == it.history?.reminderId }
            if (history != null) {
                assertThat(it.history?.completionStatus).isEqualTo(history.completionStatus)
            }
        }
    }

    @Test
    fun getAll_returnsAllReminders() = runBlockingTest {
        val reminders = (1..5).map { i ->
            Reminder(
                "Reminder $i",
                status = if (i >= 2) ReminderStatus.COMPLETED else ReminderStatus.PENDING,
                id = i.toLong()
            ).asDataModel()
        }
        reminders.forEach {
            reminderDao.insert(it)
        }

        val dbReminders = reminderDao.getAll().first()

        assertThat(dbReminders).hasSize(reminders.size)
        assertThat(dbReminders).containsExactlyElementsIn(reminders)
    }

    @Test
    fun findById_returnsInsertedReminder() = runBlockingTest {
        val reminder = Reminder("Reminder 1", ReminderStatus.SKIPPED).asDataModel()

        val id = reminderDao.insert(reminder)

        val dbReminder = reminderDao.findById(id)

        assertThat(dbReminder).isEqualTo(reminder.copy(id = id))
    }

    @Test
    fun findById_emptyDb_returnsNull() = runBlockingTest {
        val dbReminder = reminderDao.findById(1L)

        assertThat(dbReminder).isNull()
    }

    @Test
    fun delete_then_findReturnsNull() = runBlockingTest {
        val reminder = Reminder(id = 1).asDataModel()
        reminderDao.insert(reminder)

        assertThat(reminderDao.findById(reminder.id)).isNotNull()

        reminderDao.delete(reminder)

        assertThat(reminderDao.findById(reminder.id)).isNull()
    }

    @Test
    fun getReminderAndAlarm() = runBlockingTest {
        val reminder = Reminder(id = 1).asDataModel()
        reminderDao.insert(reminder)

        val alarm = AlarmEntity(id = 1, reminderId = 1, triggerAt = 0)
        alarmDao.insert(alarm)

        val reminderAndAlarm = reminderDao.getReminderAndAlarm(reminder.id)
        assertThat(reminderAndAlarm).isNotNull()
        reminderAndAlarm!!
        assertThat(reminderAndAlarm.reminder.id).isEqualTo(reminder.id)
        assertThat(reminderAndAlarm.alarm.id).isEqualTo(alarm.id)
        assertThat(reminderAndAlarm.alarm.reminderId).isEqualTo(reminder.id)
    }
}