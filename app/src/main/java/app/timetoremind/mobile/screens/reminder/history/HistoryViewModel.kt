package app.timetoremind.mobile.screens.reminder.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.di.AppCoroutineScope
import app.timetoremind.mobile.di.DefaultDispatcher
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