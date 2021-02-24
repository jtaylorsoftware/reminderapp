package com.github.jeremyt135.reminderapp.screens.alarm

import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.service.ReminderAlarmManager
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
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

    private lateinit var reminderAlarmManager: ReminderAlarmManager.LocalBinder
    private var bound = false
    private val serviceConnection = object : ServiceConnection {
        private lateinit var snoozeTimeoutJob: Job
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            reminderAlarmManager = service as ReminderAlarmManager.LocalBinder
            bound = true
            snoozeTimeoutJob = viewModelScope.launch {
                reminderAlarmManager.snoozeTimeout.collect {
                    _finished.emit(Unit)
                }
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            bound = false
            snoozeTimeoutJob.cancel()
        }
    }

    init {
        val reminderId = requireNotNull(savedStateHandle.get<Long>(EXTRA_REMINDER_ID))
        loadReminderData(reminderId)

        val context = getApplication<Application>().applicationContext
        ReminderAlarmManager.bind(context, serviceConnection, reminderId)
    }

    override fun onCleared() {
        super.onCleared()

        if (bound) {
            getApplication<Application>().applicationContext.unbindService(serviceConnection)
            bound = false
        }
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

        require(bound)
        reminderAlarmManager.snooze()
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

        require(bound)
        reminderAlarmManager.skip()
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

        require(bound)
        reminderAlarmManager.complete()
        viewModelScope.launch {
            onAction()
        }
    }

    private suspend fun onAction() {
        tookAction = true
        _finished.emit(Unit)
    }
}