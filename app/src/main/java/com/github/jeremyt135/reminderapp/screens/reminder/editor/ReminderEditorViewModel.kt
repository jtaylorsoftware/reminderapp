package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.broadcast.cancelNotification
import com.github.jeremyt135.reminderapp.broadcast.cancelReminder
import com.github.jeremyt135.reminderapp.broadcast.scheduleNextAlarm
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.asSingleEvent
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _navigateToReminders = MutableEventFlow<Unit>()
    val navigateToReminders = _navigateToReminders.asSingleEvent()

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

    fun cancel() {
        viewModelScope.launch { _navigateToReminders.value = Unit }
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
            _reminder.value.copy().let { reminder ->
                reminder.id = reminderRepository.save(reminder)

                val context = getApplication<Application>().applicationContext

                cancelReminder(context, reminder)

                // Schedule reminder for time closest to today
                val today = LocalDate.now().dayOfWeek
                reminder.getNextTimeForAlarm(today)?.let { time ->
                    val nextAlarm = Alarm(reminderId = reminder.id, triggerAt = time.toEpochMilli())
                    nextAlarm.id = alarmRepository.save(nextAlarm)
                    scheduleNextAlarm(context, alarmRepository, reminderRepository)
                }
            }

            _navigateToReminders.value = Unit
        }
    }

    fun deleteReminder() {
        if (!isEditing) {
            return
        }

        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            _reminder.value.copy().let { reminder ->
                cancelReminder(context, reminder)

                reminderRepository.delete(reminder)
            }
            _navigateToReminders.value = Unit
        }
    }

    fun onTimeChanged(newHour: Int, newMinute: Int) {
        _reminder.value.apply {
            hour = newHour
            minute = newMinute
        }
    }

    private suspend fun cancelReminder(context: Context, reminder: Reminder) {
        alarmRepository.findByReminderId(savedReminderId)?.let {
            Timber.d("Cancelling previous alarm")
            cancelNotification(context, it.id)
            cancelReminder(context, it, reminder)
            alarmRepository.delete(it)
        }
    }
}
