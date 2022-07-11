package app.timetoremind.mobile.data

import app.timetoremind.mobile.data.domain.History
import app.timetoremind.sharedtest.MainCoroutineRule
import app.timetoremind.sharedtest.data.local.FakeHistoryLocalDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class HistoryRepositoryTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var repository: HistoryRepository
    private lateinit var localDataSource: HistoryDataSource

    private val historyEntries = mutableListOf(
        History(), History(), History()
    )

    @Before
    fun setup() {
        localDataSource = FakeHistoryLocalDataSource(historyEntries)
        repository = HistoryRepositoryImpl(localDataSource)
        runBlocking {
            repository.saveAll(historyEntries)
        }
    }

    @Test
    fun getAll_returnsAllLocalHistory() = runTest {
        assertThat(repository.getAll().first()).isEqualTo(historyEntries)
    }

    @Test
    fun findById_invalidId_returnsNull() = runTest {
        assertThat(repository.findById("")).isNull()
    }

    @Test
    fun findById_matchingId_returnsSameHistory() = runTest {
        assertThat(repository.findById(historyEntries[0].id)).isEqualTo(historyEntries[0])
    }

    @Test
    fun findByReminderId_returnsOnlyMatchingHistory() = runTest {
        val id = historyEntries[0].reminderId
        val history = repository.findByReminderId(id).first()
        assertThat(history).containsExactlyElementsIn(historyEntries.filter { it.reminderId == id })
    }

    @Test
    fun deleteAll_clearsLocalEntries() = runTest {
        repository.deleteAll()
        assertThat(repository.getAll().first()).hasSize(0)
    }
}