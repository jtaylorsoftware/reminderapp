package com.github.jeremyt135.reminderapp.screens.reminder.overview

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReminderOverviewViewModel @Inject constructor(private val reminderRepository: ReminderRepository) :
    ViewModel() {

    val reminders: LiveData<List<Reminder>> =
        reminderRepository.getAll().asLiveData(viewModelScope.coroutineContext)

    private val _navigateToEditor = MutableSharedFlow<Reminder?>(replay = 0)
    val navigateToEditor: SharedFlow<Reminder?> = _navigateToEditor

    fun addReminder() {
        viewModelScope.launch {
            _navigateToEditor.emit(null)
        }
    }

    fun editReminder(reminder: Reminder) {
        viewModelScope.launch {
            _navigateToEditor.emit(reminder)
        }
    }
}