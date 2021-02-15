package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.google.common.truth.Truth.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class HistoryRepositoryTest {
    private lateinit var repository: HistoryRepository
    private lateinit var localDataSource: HistoryDataSource

    private val historyEntries = mutableListOf(
        History(reminderId = 1), History(reminderId = 0), History(reminderId = 2)
    )

    @Before
    fun setup() {
        localDataSource = FakeHistoryLocalDataSource(historyEntries)
        repository = HistoryRepositoryImpl(localDataSource)
        runBlocking {
            repository.saveAll(historyEntries).forEachIndexed { i, id ->
                historyEntries[i] = historyEntries[i].copy(id = id)
            }
        }
    }

    @Test
    fun getAll_returnsAllLocalHistory() = runBlockingTest {
        assertThat(repository.getAll().first()).isEqualTo(historyEntries)
    }

    @Test
    fun findById_invalidId_returnsNull() = runBlockingTest {
        assertThat(repository.findById(historyEntries.size * 2L)).isNull()
    }

    @Test
    fun findById_matchingId_returnsSameHistory() = runBlockingTest {
        assertThat(repository.findById(historyEntries[0].id)).isEqualTo(historyEntries[0])
    }

    @Test
    fun findByReminderId_returnsOnlyMatchingHistory() = runBlockingTest {
        val id = 1L
        val history = repository.findByReminderId(id).first()
        assertThat(history).containsExactlyElementsIn(historyEntries.filter { it.reminderId == id })
    }

    @Test
    fun deleteAll_clearsLocalEntries() = runBlockingTest {
        repository.deleteAll()
        assertThat(repository.getAll().first()).hasSize(0)
    }
}