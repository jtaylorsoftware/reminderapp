package com.github.jeremyt135.reminderapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.data.local.HistoryEntity as History
import com.github.jeremyt135.reminderapp.util.InMemoryDatabaseRule
import com.google.common.truth.Truth.*
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test


@ExperimentalCoroutinesApi
@HiltAndroidTest
@SmallTest
class HistoryDaoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val inMemoryDb = InMemoryDatabaseRule()

    private lateinit var historyDao: HistoryDao

    private val historyEntries = mutableListOf(
        History(reminderId = 0),
        History(reminderId = 1),
        History(reminderId = 2),
        History(reminderId = 2)
    )

    @Before
    fun setup() = runBlockingTest {
        historyDao = inMemoryDb.db.historyDao()
        historyDao.insertAll(historyEntries).forEachIndexed { i, id ->
            historyEntries[i] = historyEntries[i].copy(id = id)
        }
    }

    @Test
    fun getAll_returnsAllHistory() = runBlockingTest {
        assertThat(historyDao.getAll().first()).containsExactlyElementsIn(historyEntries)
    }

    @Test
    fun findById_zeroId_returnsNull() = runBlockingTest {
        assertThat(historyDao.findById(0)).isNull()
    }

    @Test
    fun findById_matchingId_returnsEntity() = runBlockingTest {
        assertThat(historyDao.findById(historyEntries[0].id)).isEqualTo(historyEntries[0])
    }

    @Test
    fun findByReminderId_returnsAllMatchingEntities() = runBlockingTest {
        assertThat(historyDao.findByReminderId(2).first()).containsExactlyElementsIn(
            historyEntries.filter { it.reminderId == 2L }
        )
    }

    @Test
    fun insert_existingId_updatesHistoryEntity() = runBlockingTest {
        assertThat(historyDao.insert(historyEntries[0])).isEqualTo(historyEntries[0].id)
    }

    @Test
    fun clearTable_removesAllEntities() = runBlockingTest {
        historyDao.clearTable()
        assertThat(historyDao.getAll().first()).hasSize(0)
    }
}