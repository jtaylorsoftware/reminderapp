package app.timetoremind.mobile.screens.history

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.timetoremind.mobile.MainCoroutineRule
import app.timetoremind.mobile.data.FakeHistoryRepository
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.data.domain.History
import app.timetoremind.mobile.data.local.FakeHistoryLocalDataSource
import app.timetoremind.mobile.getOrAwaitValue
import app.timetoremind.mobile.screens.reminder.history.HistoryViewModel
import com.google.common.truth.Truth.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class HistoryViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: HistoryRepository
    private lateinit var viewModel: HistoryViewModel

    private val historyEntries = mutableListOf(
        History(),History(),History()
    )

    @Before
    fun setup() {
        val localDataSource = FakeHistoryLocalDataSource(historyEntries)
        repository = FakeHistoryRepository(localDataSource)
        runBlocking {
            repository.saveAll(historyEntries)
        }
        viewModel = HistoryViewModel(repository, mainCoroutineRule)
    }

    @Test
    fun historyEntries_returnsRepoEntriesLiveData() = mainCoroutineRule.runBlockingTest {
        assertThat(viewModel.historyEntries.getOrAwaitValue()).containsExactlyElementsIn(
            repository.getAll().first()
        )
    }

}