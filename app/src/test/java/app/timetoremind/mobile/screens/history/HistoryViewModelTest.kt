package app.timetoremind.mobile.screens.history

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.data.domain.History
import app.timetoremind.mobile.getOrAwaitValue
import app.timetoremind.mobile.screens.reminder.history.HistoryViewModel
import app.timetoremind.sharedtest.MainCoroutineRule
import app.timetoremind.sharedtest.data.FakeHistoryRepository
import app.timetoremind.sharedtest.data.local.FakeHistoryLocalDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class HistoryViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private lateinit var repository: HistoryRepository
    private lateinit var viewModel: HistoryViewModel

    private val historyEntries = mutableListOf(
        History(), History(), History()
    )

    @Before
    fun setup() {
        val localDataSource = FakeHistoryLocalDataSource(historyEntries)
        repository = FakeHistoryRepository(localDataSource)
        runBlocking {
            repository.saveAll(historyEntries)
        }
        viewModel = HistoryViewModel(repository, CoroutineScope(mainCoroutineRule.dispatcher), mainCoroutineRule.dispatcher)
    }

    @Test
    fun historyEntries_returnsRepoEntriesLiveData() = runTest {
        assertThat(viewModel.historyEntries.getOrAwaitValue()).containsExactlyElementsIn(
            repository.getAll().first()
        )
    }

}