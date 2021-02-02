package com.github.jeremyt135.reminderapp.screens.reminder.editor

import androidx.lifecycle.*
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.emptyReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ReminderEditorViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    savedStateHandle: SavedStateHandle
) :
    ViewModel() {

    private val _reminder = MutableStateFlow(emptyReminder())
    val reminder: LiveData<Reminder> = _reminder.asLiveData(viewModelScope.coroutineContext)

    private val reminderId: Long = savedStateHandle.get("reminderId")!!
    private val isEditing: Boolean = reminderId != 0L

    init {
        Timber.d("reminderId = $reminderId")
        if (isEditing) {
            viewModelScope.launch {
                reminderRepository.findById(reminderId)?.let {
                    _reminder.value = it
                }
            }
        }
    }
}

