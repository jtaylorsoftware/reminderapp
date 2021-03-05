package com.github.jeremyt135.reminderapp.screens.alarm

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.BuildConfig
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.service.ReminderAlarmManager
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.asSingleEvent
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _finished = MutableEventFlow<Unit>()
    val finished = _finished.asSingleEvent()

    private lateinit var reminderAlarmManager: ReminderAlarmManager.LocalBinder
    private var bound = false
    private val serviceConnection = object : ServiceConnection {
        private lateinit var snoozeTimeoutJob: Job
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Timber.d("AlarmViewModel bound to service")
            reminderAlarmManager = service as ReminderAlarmManager.LocalBinder
            bound = true
            snoozeTimeoutJob = viewModelScope.launch {
                reminderAlarmManager.snoozeTimeout.onEvent { timeout ->
                    timeout?.let { _finished.value = Unit }
                }
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Timber.d("AlarmViewModel unbound from service")
            bound = false
            snoozeTimeoutJob.cancel()
        }
    }

    init {
        val reminderId = requireNotNull(savedStateHandle.get<Long>(EXTRA_REMINDER_ID))
        loadReminderData(reminderId)

        val context = getApplication<Application>().applicationContext
        ReminderAlarmManager.bind(context, serviceConnection, reminderId, Context.BIND_IMPORTANT)
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

        if (bound) {
            reminderAlarmManager.snooze()
            onAction()
        } else if (BuildConfig.DEBUG) {
            val msg = "ReminderAlarmManager is not bound but AlarmViewModel requested action"
            Timber.d(msg)
            Toast
                .makeText(getApplication<Application>().applicationContext, msg, Toast.LENGTH_LONG)
                .show()
        }
    }

    /**
     * Skips the alarm and emits finished
     */
    fun skip() {
        if (tookAction) {
            return
        }

        if (bound) {
            reminderAlarmManager.skip()
            onAction()
        } else if (BuildConfig.DEBUG) {
            val msg = "ReminderAlarmManager is not bound but AlarmViewModel requested action"
            Timber.d(msg)
            Toast
                .makeText(getApplication<Application>().applicationContext, msg, Toast.LENGTH_LONG)
                .show()
        }
    }

    /**
     * Completes the alarm and emits finished
     */
    fun complete() {
        if (tookAction) {
            return
        }

        if (bound) {
            reminderAlarmManager.complete()
            onAction()
        } else if (BuildConfig.DEBUG) {
            val msg = "ReminderAlarmManager is not bound but AlarmViewModel requested action"
            Timber.d(msg)
            Toast
                .makeText(getApplication<Application>().applicationContext, msg, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun onAction() {
        tookAction = true
        _finished.value = Unit
    }
}