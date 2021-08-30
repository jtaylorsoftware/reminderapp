package com.github.jeremyt135.reminderapp.screens.alarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.service.AlarmTimerManager
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.asEventFlow
import com.github.jeremyt135.reminderapp.util.getShortLocalTimeDisplay
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    application: Application,
    private val reminderRepository: ReminderRepository,
    savedStateHandle: SavedStateHandle,
    private val timerManager: AlarmTimerManager,
) : AndroidViewModel(application) {
    private val _reminder = MutableStateFlow(Reminder())
    val reminder = _reminder.asLiveData(viewModelScope.coroutineContext)

    private val _alarm = MutableStateFlow(Alarm(0, ""))
    val alarm = _alarm.asLiveData(viewModelScope.coroutineContext)

    private val _time = MutableStateFlow("")
    val time = _time.asLiveData(viewModelScope.coroutineContext)

    private val _finished = MutableEventFlow<Unit>()

    /**
     * Event indicating the Alarm has been handled and the Fragment/Activity
     * should stop.
     */
    val finished = _finished.asEventFlow()

    private var timerJob: Job? = null

    // Flag indicating that the user clicked a button
    private var handled: Boolean = false

    init {
        // Load the reminder so it can be displayed
        savedStateHandle.get<String>(EXTRA_REMINDER_ID)?.let { id ->
            viewModelScope.launch {
                loadReminderData(id) ?: run {
                    Timber.e("Couldn't load reminder $id")
                    finish()
                }
            }
        } ?: run {
            Timber.e("savedStateHandle had no value for $EXTRA_REMINDER_ID")
            finish()
        }

        // Finish the activity when the timer ends
        timerManager.timer?.let { timer ->
            timerJob = viewModelScope.launch {
                timer.timerFinished.flow.collect { finished ->
                    finished.peek()?.let {
                        finish()
                    }
                }
            }
        } ?: run {
            Timber.w("AlarmTimerManager.timer was null when creating AlarmViewModel")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("AlarmViewModel cleared")
        // stop AlarmService (sounds and notif)
        AlarmService.requestStop(getApplication())
        // try to snooze
        snooze()
    }

    // Loads the reminder and alarm data for the associated reminder
    private suspend fun loadReminderData(id: String): Pair<Reminder, Alarm>? =
        reminderRepository.getReminderAndAlarm(id)?.let { (reminder, alarm) ->
            _reminder.value = reminder
            alarm?.let {
                _alarm.value = alarm
                _time.value = alarm.triggerAt.getShortLocalTimeDisplay()
            } ?: run {
                return@let null
            }
           reminder to alarm
        }

    /**
     * Snoozes the alarm
     */
    fun snooze() {
        showToast(getApplication(), "Snoozed Reminder")

        tryStartWorkerForAction(ACTION_ALARM_SNOOZE)
    }

    /**
     * Skips the alarm
     */
    fun skip() {
        showToast(getApplication(), "Skipped Reminder")

        tryStartWorkerForAction(ACTION_ALARM_SKIP)
    }

    /**
     * Completes the alarm
     */
    fun complete() {
        showToast(getApplication(), "Completed Reminder")

        tryStartWorkerForAction(ACTION_ALARM_COMPLETE)
    }

    /**
     * Starts a worker for action if the Alarm is currently unhandled
     */
    private fun tryStartWorkerForAction(action: String) {
        tryHandleAlarm {
            if (timerManager.timer?.stop() == true) {
                startWorkerForAction(action)
            } else {
                Timber.d("AutoSnoozeHandler already snoozed Alarm")
            }
        }
    }

    // Performs action if the Alarm is currently unhandled.
    private inline fun tryHandleAlarm(crossinline handler: () -> Unit) {
        if (handled || _finished.peek() != null) {
            Timber.w("Already handled Alarm")
            return
        }
        Timber.d("Handling Alarm")
        handleAlarm(handler)
    }

    private inline fun handleAlarm(crossinline handler: () -> Unit) {
        handled = true
        timerJob?.cancel()
        handler()
        finish()
    }

    private fun startWorkerForAction(action: String) {
        Timber.d("AlarmViewModel starting worker")
        AlarmActionHandler.createForAction(action, _reminder.value.id).let {
            WorkManager.getInstance(getApplication()).enqueue(it)
        }
    }

    private fun finish() {
        _finished.value = Unit
    }
}
