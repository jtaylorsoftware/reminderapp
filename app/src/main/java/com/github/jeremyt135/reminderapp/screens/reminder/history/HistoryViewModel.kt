package com.github.jeremyt135.reminderapp.screens.reminder.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    @AppCoroutineScope private val applicationScope: CoroutineScope,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) :
    ViewModel() {

    val historyEntries = historyRepository.getAll().map { historyList ->
        historyList.sortedByDescending { it.completedAt }
    }.flowOn(dispatcher).asLiveData(viewModelScope.coroutineContext)

    fun clearHistory() {
        applicationScope.launch {
            historyRepository.deleteAll()
        }
    }
}