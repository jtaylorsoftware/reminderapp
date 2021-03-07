package com.github.jeremyt135.reminderapp.screens.reminder.overview

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.asEventFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(private val reminderRepository: ReminderRepository) :
    ViewModel() {

    val reminders: LiveData<List<ReminderAndHistory>> =
        reminderRepository.getAllWithTodayHistory().asLiveData(viewModelScope.coroutineContext)

    private val _navigateToEditor = MutableEventFlow<Reminder>()
    val navigateToEditor = _navigateToEditor.asEventFlow()

    fun addReminder() {
        viewModelScope.launch {
            _navigateToEditor.value = Reminder()
        }
    }

    fun editReminder(reminder: Reminder) {
        viewModelScope.launch {
            _navigateToEditor.value = reminder
        }
    }
}