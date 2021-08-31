package com.github.jeremyt135.reminderapp.screens.reminder.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    @AppCoroutineScope private val applicationScope: CoroutineScope
) :
    ViewModel() {

    val historyEntries = historyRepository.getAll().map { historyList ->
        historyList.sortedByDescending { it.completedAt }
    }.asLiveData(viewModelScope.coroutineContext)

    fun clearHistory() {
        applicationScope.launch {
            historyRepository.deleteAll()
        }
    }
}