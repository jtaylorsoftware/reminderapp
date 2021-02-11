package com.github.jeremyt135.reminderapp.screens.alarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    application: Application,
    private val reminderRepository: ReminderRepository,
    private val alarmRepository: AlarmRepository
) : AndroidViewModel(application) {
    private val _reminder = MutableStateFlow(Reminder())
    val reminder = _reminder.asLiveData(viewModelScope.coroutineContext)

    private val _alarm = MutableStateFlow(Alarm(0, 0, 0))
    val alarm = _alarm.asLiveData(viewModelScope.coroutineContext)

    private val _time = MutableStateFlow("")
    val time = _time.asLiveData(viewModelScope.coroutineContext)

    private val _finished = MutableSharedFlow<Unit>(replay = 0)
    val finished: SharedFlow<Unit> = _finished

    /**
     * Loads the reminder and alarm data for the associated reminder
     */
    fun loadReminderData(id: Long) {
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
        val context = getApplication<Application>().applicationContext

        val prevAlarm = _alarm.value
        cancelAlarm(context, prevAlarm)

        val nextAlarm = prevAlarm.copy(
            triggerAt = prevAlarm.triggerAt.toLocalDateTime().plusMinutes(SNOOZE_TIME_MIN)
                .toEpochMilli()
        )

        viewModelScope.launch {
            alarmRepository.save(nextAlarm)
            scheduleAlarm(context, nextAlarm)
            makeSnoozeToast(context)
            _finished.emit(Unit)
        }
    }

    /**
     * Skips the alarm and emits finished
     */
    fun skip() {
        viewModelScope.launch {
            reminderRepository.save(_reminder.value.copy(status = ReminderStatus.SKIPPED))
            _finished.emit(Unit)
        }
    }

    /**
     * Completes the alarm and emits finished
     */
    fun complete() {
        viewModelScope.launch {
            reminderRepository.save(_reminder.value.copy(status = ReminderStatus.COMPLETED))
            _finished.emit(Unit)
        }
    }
}