package com.github.jeremyt135.reminderapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.util.InMemoryDatabaseRule
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.github.jeremyt135.reminderapp.data.local.HistoryEntity as History


@ExperimentalCoroutinesApi
@HiltAndroidTest
@SmallTest
class HistoryDaoTest {
    @get:Rule(order = 0)
    var hiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule(order = 2)
    val inMemoryDb = InMemoryDatabaseRule()

    private lateinit var historyDao: HistoryDao

    private val reminders = (1..4).map {
        Reminder()
    }

    private val historyEntries =
        reminders.map { History(reminderId = it.id) } + listOf(History(reminderId = reminders[1].id))

    @Before
    fun setup() = runBlockingTest {
        historyDao = inMemoryDb.db.historyDao()
        hiltAndroidRule.inject()
        historyDao.insertAll(historyEntries)
    }

    @Test
    fun getAll_returnsAllHistory() = runBlockingTest {
        assertThat(historyDao.getAll().first()).containsExactlyElementsIn(historyEntries)
    }

    @Test
    fun getLatestForReminder_returnsSingleLatestHistory() = runBlockingTest {
        val histories = (0..4).map {
            History(
                reminderId = "id",
                completedAt = currentLocalDateTime().plusMinutes(it * 10L).toEpochMilli()
            )
        }
        historyDao.insertAll(histories)
        assertThat(historyDao.getLatestForReminder("id")!!).isEqualTo(histories.last())
    }

    @Test
    fun findById_emptyId_returnsNull() = runBlockingTest {
        assertThat(historyDao.findById("")).isNull()
    }

    @Test
    fun findById_matchingId_returnsEntity() = runBlockingTest {
        assertThat(historyDao.findById(historyEntries[0].id)).isEqualTo(historyEntries[0])
    }

    @Test
    fun findByReminderId_returnsAllMatchingEntities() = runBlockingTest {
        assertThat(historyDao.findByReminderId(reminders[1].id).first()).containsExactlyElementsIn(
            historyEntries.filter { it.reminderId == reminders[1].id }
        )
    }

    @Test
    fun insert_existingId_updatesHistoryEntity() = runBlockingTest {
        val updated = historyEntries[0].copy(completionStatus = ReminderStatus.SKIPPED.name)
        historyDao.insert(updated)
        assertThat(historyDao.findById(historyEntries[0].id)).isEqualTo(updated)
    }

    @Test
    fun clearTable_removesAllEntities() = runBlockingTest {
        historyDao.clearTable()
        assertThat(historyDao.getAll().first()).hasSize(0)
    }
}