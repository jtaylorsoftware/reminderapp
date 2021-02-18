package com.github.jeremyt135.reminderapp.screens.alarm

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.BuildConfig
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.broadcast.scheduleCompleteAlarm
import com.github.jeremyt135.reminderapp.broadcast.scheduleSkipAlarm
import com.github.jeremyt135.reminderapp.broadcast.scheduleSnoozeAlarm
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    application: Application,
    private val reminderRepository: ReminderRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val _reminder = MutableStateFlow(Reminder())
    val reminder = _reminder.asLiveData(viewModelScope.coroutineContext)

    private val _alarm = MutableStateFlow(Alarm(0, 0, 0))
    val alarm = _alarm.asLiveData(viewModelScope.coroutineContext)

    private val _time = MutableStateFlow("")
    val time = _time.asLiveData(viewModelScope.coroutineContext)

    private var tookAction: Boolean = false

    private val _finished = MutableSharedFlow<Unit>(replay = 0)
    val finished: SharedFlow<Unit> = _finished

    init {
        val reminderId = requireNotNull(savedStateHandle.get<Long>(EXTRA_REMINDER_ID))
        loadReminderData(reminderId)

        viewModelScope.launch {
            val delayMilli: Long = if (BuildConfig.DEBUG) {
                5 * 1000
            } else {
                30 * 1000
            }
            delay(delayMilli)

            Timber.d("VM delay done -> snooze")
            snooze()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Try to snooze when activity is finished
        snooze()
        NotificationManagerCompat.from(getApplication<Application>().applicationContext)
            .cancel(_reminder.value.id.toInt())
    }

    /**
     * Loads the reminder and alarm data for the associated reminder
     */
    private fun loadReminderData(id: Long) {
        viewModelScope.launch {
            val reminderAndAlarm = reminderRepository.getReminderAndAlarm(id)
            if (reminderAndAlarm == null) {
                Timber.d("Couldn't load reminder $id")
            } else {
                val (reminder, alarm) = reminderAndAlarm
                _reminder.value = reminder
                _alarm.value = alarm

                val alarmTime = alarm.triggerAt.toLocalDateTime()
                _time.value =
                    alarmTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            }
        }
    }

    /**
     * Snoozes the alarm and emits finished
     */
    fun snooze() {
        if (tookAction) {
            return
        }
        Timber.d("Snoozing alarm")

        val context = getApplication<Application>().applicationContext
        scheduleSnoozeAlarm(context, _alarm.value.copy())
        Timber.d("Snoozed alarm")

        viewModelScope.launch {
            onAction()
        }
    }

    /**
     * Skips the alarm and emits finished
     */
    fun skip() {
        if (tookAction) {
            return
        }
        Timber.d("Skipping alarm")

        val context = getApplication<Application>().applicationContext
        scheduleSkipAlarm(context, _alarm.value.copy())

        viewModelScope.launch {
            onAction()
        }
    }

    /**
     * Completes the alarm and emits finished
     */
    fun complete() {
        if (tookAction) {
            return
        }
        Timber.d("Completing alarm")

        val context = getApplication<Application>().applicationContext
        scheduleCompleteAlarm(context, _alarm.value.copy())

        viewModelScope.launch {
            onAction()
        }
    }

    private suspend fun onAction() {
        tookAction = true
        _finished.emit(Unit)
    }
}