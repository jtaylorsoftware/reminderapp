package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.broadcast.cancelAlarm
import com.github.jeremyt135.reminderapp.broadcast.cancelNotification
import com.github.jeremyt135.reminderapp.broadcast.scheduleAlarmForReminder
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ReminderEditorViewModel @Inject constructor(
    application: Application,
    private val reminderRepository: ReminderRepository,
    private val alarmRepository: AlarmRepository,
    savedStateHandle: SavedStateHandle
) :
    AndroidViewModel(application) {
    private val _reminder = MutableStateFlow(Reminder())
    val reminder: LiveData<Reminder> = _reminder.asLiveData(viewModelScope.coroutineContext)

    private val _reminderDatesDisplay = MutableStateFlow(_reminder.value.getDaysDisplayString())
    val reminderDaysDisplayString: LiveData<String> =
        _reminderDatesDisplay.asLiveData(viewModelScope.coroutineContext)

    private val _navigateToReminders = MutableSharedFlow<Unit>(replay = 0)
    val navigateToReminders: SharedFlow<Unit> = _navigateToReminders

    private val savedReminderId: Long = requireNotNull(savedStateHandle.get(EXTRA_REMINDER_ID))
    val isEditing: Boolean = savedReminderId != 0L

    init {
        Timber.d("reminderId = $savedReminderId")
        if (isEditing) {
            viewModelScope.launch {
                reminderRepository.findById(savedReminderId)?.let {
                    _reminder.value = it
                    _reminderDatesDisplay.value = it.getDaysDisplayString()
                }
            }
        }
    }

    fun cancel(){
        viewModelScope.launch{ _navigateToReminders.emit(Unit) }
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
            val id = _reminder.value.let {
                reminderRepository.save(it)
            }
            if (!isEditing) {
                _reminder.value.id = id
            }

            val context = getApplication<Application>().applicationContext

            // Cancel previous alarm
            alarmRepository.findByReminderId(id)?.let {
                removeAlarm(context, it)
            }

            // Schedule reminder for time closest to today
            val today = LocalDate.now().dayOfWeek
            scheduleAlarmForReminder(
                context,
                _reminder.value.copy(),
                today
            )?.let {
                alarmRepository.save(it)
            }

            _navigateToReminders.emit(Unit)
        }
    }

    fun deleteReminder() {
        if (!isEditing) {
            return
        }

        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext

            alarmRepository.findByReminderId(savedReminderId)?.let {
                removeAlarm(context, it)
            }

            reminderRepository.delete(_reminder.value.copy())

            _navigateToReminders.emit(Unit)
        }
    }

    fun onTimeChanged(newHour: Int, newMinute: Int) {
        _reminder.value.apply {
            hour = newHour
            minute = newMinute
        }
    }

    private suspend fun removeAlarm(context: Context, alarm: Alarm) {
        Timber.d("Cancelling previous alarm")
        cancelNotification(context, alarm.reminderId)
        cancelAlarm(context, alarm)
        alarmRepository.delete(alarm)
    }

}

