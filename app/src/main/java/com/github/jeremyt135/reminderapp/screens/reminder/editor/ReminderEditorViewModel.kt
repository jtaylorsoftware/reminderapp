package com.github.jeremyt135.reminderapp.screens.reminder.editor

import androidx.lifecycle.*
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.emptyReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class ReminderEditorViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    savedStateHandle: SavedStateHandle
) :
    ViewModel() {
    private val _reminder = MutableStateFlow(emptyReminder())
    val reminder: LiveData<Reminder> = _reminder.asLiveData(viewModelScope.coroutineContext)

    private val _reminderDatesDisplay = MutableStateFlow(_reminder.value.getDaysDisplayString())
    val reminderDaysDisplayString: LiveData<String> =
        _reminderDatesDisplay.asLiveData(viewModelScope.coroutineContext)

    private val _navigateToReminders = MutableSharedFlow<Unit>(replay = 0)
    val navigateToReminders = _navigateToReminders

    private val reminderId: Long = savedStateHandle.get("reminderId")!!
    val isEditing: Boolean = reminderId != 0L

    init {
        Timber.d("reminderId = $reminderId")
        if (isEditing) {
            viewModelScope.launch {
                reminderRepository.findById(reminderId)?.let {
                    _reminder.value = it
                    _reminderDatesDisplay.value = it.getDaysDisplayString()
                }
            }
        }
    }

    fun toggleDay(dayString: String) {
        Timber.d("Clicked $dayString")
        val day = DayOfWeek.valueOf(dayString)
        _reminder.value.apply {
            if (daysOfWeek.contains(day)) {
                daysOfWeek.remove(day)
            } else {
                daysOfWeek.add(day)
            }
        }.also {
            Timber.d("datesDisplay is now ${it.getDaysDisplayString()}")
            _reminderDatesDisplay.value = it.getDaysDisplayString()
        }
    }

    fun saveReminder() {
        viewModelScope.launch {
            reminderRepository.save(_reminder.value)
            navigateToReminders.emit(Unit)
        }
    }

    fun onTimeChanged(newHour: Int, newMinute: Int) {
        _reminder.value.apply {
            hour = newHour
            minute = newMinute
        }
    }
}

