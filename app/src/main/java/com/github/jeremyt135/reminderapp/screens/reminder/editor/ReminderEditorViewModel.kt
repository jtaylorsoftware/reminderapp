package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.*
import androidx.work.WorkManager
import com.github.jeremyt135.BuildConfig
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.asEventFlow
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.properties.Delegates

@HiltViewModel
class ReminderEditorViewModel @Inject constructor(
    application: Application,
    private val reminderRepository: ReminderRepository,
    private val alarmRepository: AlarmRepository,
    savedStateHandle: SavedStateHandle,
    @AppCoroutineScope private val applicationScope: CoroutineScope = CoroutineScope(
        EmptyCoroutineContext
    )
) :
    AndroidViewModel(application) {
    private val _reminder = MutableStateFlow(Reminder())
    val reminder: LiveData<Reminder> = _reminder.asLiveData(viewModelScope.coroutineContext)

    private val _reminderDatesDisplay = MutableStateFlow(_reminder.value.getDaysDisplayString())
    val reminderDaysDisplayString: LiveData<String> =
        _reminderDatesDisplay.asLiveData(viewModelScope.coroutineContext)

    private val _navigateToReminders = MutableEventFlow<Unit>()
    val navigateToReminders = _navigateToReminders.asEventFlow()

    private var savedReminderId by Delegates.notNull<Long>()
    var isEditing by Delegates.notNull<Boolean>()

    init {
        savedReminderId = savedStateHandle.get(EXTRA_REMINDER_ID) ?: run {
            if (BuildConfig.DEBUG) {
                showToast(
                    getApplication<Application>().applicationContext,
                    "ReminderEditorViewModel: savedStateHandle.get(REMINDER_ID) was null",
                    Toast.LENGTH_LONG
                )
            }
            0L
        }
        isEditing = savedReminderId != 0L

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
        applicationScope.launch {
            _reminder.value.copy().let { reminder ->
                reminder.id = reminderRepository.save(reminder)

                val wasScheduled = stopAlarmsForReminder(getApplication(), reminder)

                val today = LocalDate.now().dayOfWeek
                reminder.getNextTimeForAlarm(today)?.let { time ->
                    val nextAlarm = Alarm(reminderId = reminder.id, triggerAt = time)
                    nextAlarm.id = alarmRepository.save(nextAlarm)
                }

                if (wasScheduled) {
                    AlarmService.requestStop(getApplication())
                }

                scheduleNextAlarm()
            }
        }

        _navigateToReminders.value = Unit
    }

    fun deleteReminder() {
        if (!isEditing) {
            return
        }

        applicationScope.launch {
            _reminder.value.copy().let { reminder ->
                with(getApplication<Application>()) {
                    if (stopAlarmsForReminder(this, reminder)) {
                        // Was currently scheduled, so try to schedule next
                        AlarmService.requestStop(this)
                        scheduleNextAlarm()
                    }
                }

                reminderRepository.delete(reminder)
            }
        }

        _navigateToReminders.value = Unit
    }

    fun onTimeChanged(newHour: Int, newMinute: Int) {
        _reminder.value.apply {
            hour = newHour
            minute = newMinute
        }
    }

    private fun scheduleNextAlarm() {
        AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, 0).let {
            WorkManager.getInstance(getApplication()).enqueue(it)
        }
    }

    // Cancels & deletes Reminder, returning value of alarm.scheduled
    private suspend fun stopAlarmsForReminder(context: Context, reminder: Reminder): Boolean =
        alarmRepository.findByReminderId(savedReminderId)?.let {
            Timber.d("Cancelling previous alarm")
            cancelAlarmIntentsForReminder(context, reminder)
            alarmRepository.delete(it)
            it.scheduled
        } == true
}
