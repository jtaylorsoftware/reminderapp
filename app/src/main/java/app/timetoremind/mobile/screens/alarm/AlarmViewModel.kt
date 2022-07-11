package app.timetoremind.mobile.screens.alarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import app.timetoremind.mobile.broadcast.*
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.ReminderStatus
import app.timetoremind.mobile.service.*
import app.timetoremind.mobile.util.MutableEventFlow
import app.timetoremind.mobile.util.asEventFlow
import app.timetoremind.mobile.util.getShortLocalTimeDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
        val reminderId = savedStateHandle.get<String>(EXTRA_REMINDER_ID)
        if (reminderId != null) {
            viewModelScope.launch {
                if (!loadReminderData(reminderId)) {
                    Timber.e("Couldn't load reminder $reminderId")
                    finish()
                }
            }
        } else {
            Timber.e("savedStateHandle had no value for $EXTRA_REMINDER_ID")
            finish()
        }

        if (timerManager.timer != null) {
            // Finish the activity when the timer ends
            timerJob = viewModelScope.launch {
                timerManager.timer?.timerFinished?.flow?.collect { finished ->
                    finished.peek()?.let {
                        finish()
                    }
                }
            }
        } else {
            Timber.e("AlarmTimerManager.timer was null when creating AlarmViewModel")
            finish()
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("AlarmViewModel cleared")
        // stop AlarmService (sounds and notif)
        AlarmMediaService.requestStop(getApplication())
        // try to snooze
        snooze(showToast = false)
    }

    // Loads the reminder and alarm data for the associated reminder
    private suspend fun loadReminderData(id: String): Boolean {
        val reminderAndAlarm = reminderRepository.getReminderAndAlarm(id) ?: return false
        val (reminder, alarm) = reminderAndAlarm
        if (alarm == null) {
            return false
        }
        _reminder.value = reminder
        _alarm.value = alarm
        _time.value = alarm.triggerAt.getShortLocalTimeDisplay()
        return true
    }


    /**
     * Snoozes the alarm
     */
    fun snooze(showToast: Boolean = true) {
        if (showToast) {
            showToast(getApplication(), "Snoozed Reminder")
        }

        tryStartWorkerForAction(ACTION_ALARM_SNOOZE, _reminder.value)
    }

    /**
     * Skips the alarm
     */
    fun skip(showToast: Boolean = true) {
        if (showToast) {
            showToast(getApplication(), "Skipped Reminder")
        }

        tryStartWorkerForAction(ACTION_ALARM_SKIP, _reminder.value)
    }

    /**
     * Completes the alarm
     */
    fun complete(showToast: Boolean = true) {
        if (showToast) {
            showToast(getApplication(), "Completed Reminder")
        }

        tryStartWorkerForAction(ACTION_ALARM_COMPLETE, _reminder.value)
    }

    /**
     * Starts a worker for action if the Alarm is currently unhandled
     */
    private fun tryStartWorkerForAction(action: String, reminder: Reminder) {
        tryHandleAlarm {
            if (timerManager.timer?.stop() == true) {
                startWorkerForAction(action, reminder)
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

    private fun startWorkerForAction(action: String, reminder: Reminder) {
        Timber.d("AlarmViewModel starting worker")
        when (action) {
            ACTION_ALARM_SKIP -> {
                AlarmHistoryService.start(getApplication(), reminder, ReminderStatus.SKIPPED)
                AlarmSchedulingService.startScheduleNewAlarm(getApplication(), reminder)
            }
            ACTION_ALARM_COMPLETE -> {
                AlarmHistoryService.start(getApplication(), reminder, ReminderStatus.COMPLETED)
                AlarmSchedulingService.startScheduleNewAlarm(getApplication(), reminder)
            }
            ACTION_ALARM_SNOOZE -> {
                AlarmSnoozeService.start(getApplication(), reminder)
            }
            else -> throw IllegalArgumentException("Unsupported action $action")
        }
    }

    private fun finish() {
        _finished.value = Unit
    }
}
