package com.github.jeremyt135.reminderapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.broadcast.ACTION_ALARM_COMPLETE
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.util.*
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@ExperimentalCoroutinesApi
@HiltAndroidTest
@SmallTest
class ReminderDaoTest {
    private lateinit var reminderDao: ReminderDao
    private lateinit var alarmDao: AlarmDao
    private lateinit var historyDao: HistoryDao

    @get:Rule(order = 0)
    var hiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule(order = 2)
    var inMemoryDatabaseRule = InMemoryDatabaseRule()

    @Before
    fun setup() {
        reminderDao = inMemoryDatabaseRule.db.reminderDao()
        alarmDao = inMemoryDatabaseRule.db.alarmDao()
        historyDao = inMemoryDatabaseRule.db.historyDao()
        hiltAndroidRule.inject()
    }

    @Test
    fun getAllWithHistory_returnsAllRemindersWithLatestHistory() = runBlockingTest {
        val reminders = (1..5).map { i ->
            Reminder(
                "Reminder $i",
            ).asDataModel()
        }
        reminders.forEach {
            reminderDao.insert(it)
        }
        val historyEntries = (1..3).map { i ->
            History(
                reminderId = reminders[i - 1].id,
                description = reminders[i - 1].description,
                completionStatus = ReminderStatus.COMPLETED,
                completedAt = currentEpochMilli()
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
    fun getAllInRangeWithHistoryOnDate_returnsOnlyRemindersInDateRange() = runBlockingTest {
        // Beginning of expected date range
        val startDate = LocalDate.now().minusDays(3)
        // End of exected date range
        val endDate = LocalDate.now().plusDays(7)
        val expectedReminderCount = 3
        val reminders = (1..5).map { i ->
            when {
                // Save some in correct range of start..end dates
                i <= expectedReminderCount -> Reminder(
                    "Reminder $i",
                    schedulingRules = SchedulingRules(
                        type = SchedulingType.SINGLE,
                        startDate = startDate,
                    )
                ).asDataModel()
                // Save rest outside the date range
                else -> Reminder(
                    "Reminder $i",
                    schedulingRules = SchedulingRules(
                        type = SchedulingType.SINGLE,
                        startDate = endDate.plusDays(1),
                    )
                ).asDataModel()
            }

        }
        reminders.forEach {
            reminderDao.insert(it)
        }

        // Get reminders within start..end, should be 3
        val dbReminders = reminderDao.getAllInRangeWithHistoryOnDate(
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            historyDate = LocalDate.now().toString() // not testing for history
        ).first()

        // Expect that only reminders within date range are found
        assertThat(dbReminders).hasSize(expectedReminderCount)
    }

    @Test
    fun getAllInRangeWithHistoryOnDate_returnsOnlyHistoryOnGivenDate() = runBlockingTest {
        // Beginning of expected date range
        val startDate = LocalDate.now().minusDays(3)
        // End of exected date range
        val endDate = LocalDate.now().plusDays(7)
        val expectedReminderCount = 3
        val reminders = (1..5).map { i ->
            when {
                // Save some in correct range of start..end dates
                i <= expectedReminderCount -> Reminder(
                    "Reminder $i",
                    schedulingRules = SchedulingRules(
                        type = SchedulingType.SINGLE,
                        startDate = startDate,
                    )
                ).asDataModel()
                // Save rest outside the date range
                else -> Reminder(
                    "Reminder $i",
                    schedulingRules = SchedulingRules(
                        type = SchedulingType.SINGLE,
                        startDate = endDate.plusDays(1),
                    )
                ).asDataModel()
            }

        }
        reminders.forEach {
            reminderDao.insert(it)
        }

        // History date to test for
        val expectedHistoryDate = startDate.plusDays(1)

        val expectedHistoryCount = 2
        // Save only one history with correct date, rest incorrect
        val historyEntries = reminders.indices.map { i ->
            History(
                reminderId = reminders[i].id,
                completionStatus = ReminderStatus.COMPLETED,
                completedAt = when {
                    i < expectedHistoryCount -> expectedHistoryDate
                    else -> expectedHistoryDate.minusDays(1)
                }.atTime(LocalTime.MIN).toEpochMilli()
            ).asDataModel()
        }
        historyDao.insertAll(historyEntries)

        // Get reminders within start..end, should be 3
        val dbReminders = reminderDao.getAllInRangeWithHistoryOnDate(
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            historyDate = expectedHistoryDate.toString()
        ).first()

        // Expect that only reminders within date range are found
        assertThat(dbReminders).hasSize(expectedReminderCount)

        // Expect that only 2 of these have COMPLETED
        assertThat(dbReminders.count { it.history != null && it.history!!.completionStatus == ReminderStatus.COMPLETED.toString() }).isEqualTo(
            expectedHistoryCount
        )
    }


    @Test
    fun getAll_returnsAllReminders() = runBlockingTest {
        val reminders = (1..5).map { i ->
            Reminder(
                "Reminder $i",
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
        val reminder = Reminder("Reminder 1").asDataModel()

        reminderDao.insert(reminder)

        val dbReminder = reminderDao.findById(reminder.id)

        assertThat(dbReminder).isEqualTo(reminder)
    }

    @Test
    fun findById_emptyDb_returnsNull() = runBlockingTest {
        val dbReminder = reminderDao.findById(uuid())

        assertThat(dbReminder).isNull()
    }

    @Test
    fun delete_then_findReturnsNull() = runBlockingTest {
        val reminder = Reminder().asDataModel()
        reminderDao.insert(reminder)

        assertThat(reminderDao.findById(reminder.id)).isNotNull()

        reminderDao.delete(reminder)

        assertThat(reminderDao.findById(reminder.id)).isNull()
    }

    @Test
    fun getReminderAndAlarm_bothInserted_returnsValue() = runBlockingTest {
        val reminder = Reminder().asDataModel()
        reminderDao.insert(reminder)

        val alarm =
            AlarmEntity(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())
        alarmDao.insert(alarm)

        val reminderAndAlarm = reminderDao.getReminderAndAlarm(reminder.id)
        assertThat(reminderAndAlarm).isNotNull()
        reminderAndAlarm!!
        assertThat(reminderAndAlarm.alarm).isNotNull()
        assertThat(reminderAndAlarm.reminder.id).isEqualTo(reminder.id)
        assertThat(reminderAndAlarm.alarm!!.id).isEqualTo(alarm.id)
        assertThat(reminderAndAlarm.alarm!!.reminderId).isEqualTo(reminder.id)
    }

    @Test
    fun getReminderAndAlarm_noAlarm_returnsNullAlarm() = runBlockingTest {
        val reminder = Reminder().asDataModel()
        reminderDao.insert(reminder)

        val reminderAndAlarm = reminderDao.getReminderAndAlarm(reminder.id)
        assertThat(reminderAndAlarm).isNotNull()
        assertThat(reminderAndAlarm?.alarm).isNull()
    }
}