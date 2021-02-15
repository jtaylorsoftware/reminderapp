package com.github.jeremyt135.reminderapp.screens.history

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.FakeHistoryRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.github.jeremyt135.reminderapp.screens.reminder.history.HistoryViewModel
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
        History(reminderId = 1), History(reminderId = 0), History(reminderId = 2)
    )

    @Before
    fun setup() {
        val localDataSource = FakeHistoryLocalDataSource(historyEntries)
        repository = FakeHistoryRepository(localDataSource)
        runBlocking {
            repository.saveAll(historyEntries).forEachIndexed { i, id ->
                historyEntries[i] = historyEntries[i].copy(id = id)
            }
        }
        viewModel = HistoryViewModel(repository)
    }

    @Test
    fun historyEntries_returnsRepoEntriesLiveData() = mainCoroutineRule.runBlockingTest {
        assertThat(viewModel.historyEntries.getOrAwaitValue()).containsExactlyElementsIn(
            repository.getAll().first()
        )
    }

}