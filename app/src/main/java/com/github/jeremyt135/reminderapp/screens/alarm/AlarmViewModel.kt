package com.github.jeremyt135.reminderapp.screens.alarm

import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.util.EventFlow
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.asEventFlow
import com.github.jeremyt135.reminderapp.util.getShortLocalTimeDisplay
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    application: Application,
    private val reminderRepository: ReminderRepository,
    savedStateHandle: SavedStateHandle,
    @AppCoroutineScope private val applicationScope: CoroutineScope
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

    private var autoSnoozeHandler: AlarmAutoSnoozeHandler? = null

    // --- Service binding ---
    private lateinit var alarmService: AlarmService.AlarmServiceBinder
    private var bound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Timber.d("AlarmViewModel bound to service")
            alarmService = service as AlarmService.AlarmServiceBinder
            bound = true

        }

        override fun onServiceDisconnected(className: ComponentName) {
            Timber.d("AlarmViewModel unbound from service")
            bound = false
        }
    }
    // -------

    /**
     * Flag indicating that the user clicked a button
     */
    private var handled: Boolean = false

    init {
        savedStateHandle.get<String>(EXTRA_REMINDER_ID)?.let { id ->
            viewModelScope.launch {
                loadReminderData(id)?.let { (reminder, alarm) ->
                    startAutoSnooze(reminder.id, alarm.id, applicationScope)
                    bindToAlarmService()
                } ?: run {
                    Timber.e("Couldn't load reminder $id")
                    tryStartWorkerForAction(ACTION_ALARM_SCHEDULE_NEXT)
                }
            }
        } ?: run {
            Timber.e("savedStateHandle had no value for $EXTRA_REMINDER_ID")
            tryStartWorkerForAction(ACTION_ALARM_SCHEDULE_NEXT)
        }
    }

    override fun onCleared() {
        super.onCleared()

        tryStartWorkerForAction(ACTION_ALARM_SNOOZE)

        if (bound) {
            alarmService.stop()
        }
    }

    /**
     * Loads the reminder and alarm data for the associated reminder
     */
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


    private fun bindToAlarmService() {
        with(getApplication<Application>()) {
            AlarmService.bind(this, serviceConnection)
        }
    }

    private fun startAutoSnooze(
        reminderId: String,
        alarmId: Long,
        scope: CoroutineScope,
    ) {
        autoSnoozeHandler =
            AlarmAutoSnoozeHandler(reminderId, alarmId, scope)
        autoSnoozeHandler!!.start {
            tryHandleAlarm { startWorkerForAction(ACTION_ALARM_SNOOZE) }
        }
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
            if (autoSnoozeHandler?.stop() == true) {
                startWorkerForAction(action)
            } else {
                Timber.d("AutoSnoozeHandler already snoozed Alarm")
            }
        }
    }

    private fun startWorkerForAction(action: String) {
        Timber.d("AlarmViewModel starting worker")
        AlarmActionHandler.createForAction(action, _reminder.value.id).let {
            WorkManager.getInstance(getApplication()).enqueue(it)
        }
    }

    /**
     * Performs action if the Alarm is currently unhandled.
     */
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
        handler()
        _finished.value = Unit
    }
}

private class AlarmAutoSnoozeHandler(
    val reminderId: String,
    val alarmId: Long,
    val applicationScope: CoroutineScope,
) {
    private val snoozeHandled: AtomicBoolean = AtomicBoolean(false)
    private lateinit var job: Job
    private val snoozeTimeoutEvent = MutableEventFlow<Unit>()
    val snoozeTimeout: EventFlow<Unit> = snoozeTimeoutEvent

    /**
     * Starts the countdown to automatic snooze
     */
    inline fun start(
        timeUntilSnoozeMilli: Long = DEFAULT_SNOOZE_DELAY_MILLI,
        crossinline onSnooze: suspend () -> Unit
    ): EventFlow<Unit> {
        job = applicationScope.launch {
            Timber.d("Started command auto-snooze job")
            delay(timeUntilSnoozeMilli)
            if (snoozeHandled.compareAndSet(false, true)) {
                snoozeTimeoutEvent.value = Unit
                Timber.d("Reminder $reminderId alarm $alarmId auto-snoozing (startId:$alarmId)")
                onSnooze()
            }
        }
        return snoozeTimeout
    }

    /**
     * Stops the automatic snooze if it has not begun.
     * Returns true if the snooze was stopped or if it was already snoozed.
     */
    fun stop(): Boolean =
        if (snoozeHandled.compareAndSet(false, true)) {
            job.cancel()
            true
        } else {
            false
        }

    companion object {
        /**
         * Default value for how long an Alarm should wait until auto-snoozing in milliseconds.
         */
        const val DEFAULT_SNOOZE_DELAY_MILLI: Long = 30L * 1000
    }
}