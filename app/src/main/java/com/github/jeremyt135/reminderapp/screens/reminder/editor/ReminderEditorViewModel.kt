package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.app.Application
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.lifecycle.*
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.SchedulingType
import com.github.jeremyt135.reminderapp.data.domain.SnoozeRules
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.asEventFlow
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.getShortLocalDateDisplay
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import javax.inject.Inject
import kotlin.properties.Delegates

@HiltViewModel
class ReminderEditorViewModel @Inject constructor(
    application: Application,
    private val reminderRepository: ReminderRepository,
    private val alarmRepository: AlarmRepository,
    savedStateHandle: SavedStateHandle,
    @AppCoroutineScope private val applicationScope: CoroutineScope
) : AndroidViewModel(application) {
    private val _reminder =
        MutableStateFlow(Reminder())

    /**
     * The Reminder being edited
     */
    val reminder = _reminder.asStateFlow()

    /**
     * Error state for days of week
     */
    private val _daysOfWeekState = MutableStateFlow(TextFieldState())
    val daysOfWeekState = _daysOfWeekState.asLiveData(viewModelScope.coroutineContext)

    /**
     * Comma-separated of selected days of week
     */
    val reminderDaysDisplayString: LiveData<String> =
        _daysOfWeekState.combine(reminder) { state, reminder ->
            if (!isEditing && !state.dirty) {
                ""
            } else reminder.getDaysDisplayString()
        }.asLiveData(viewModelScope.coroutineContext)

    /**
     * Error state for start date
     */
    private val _startDateState = MutableStateFlow(TextFieldState())
    val startDateState = _startDateState.asLiveData(viewModelScope.coroutineContext)

    /**
     * String containing the selected start date
     */
    val startDateString: LiveData<String> =
        _startDateState.combine(_reminder) { state, reminder ->
            if (!isEditing && !state.dirty) {
                ""
            } else reminder.schedulingRules.startDateDisplayString()
        }.asLiveData(viewModelScope.coroutineContext)

    /**
     * Error state for end date
     */
    private val _endDateState = MutableStateFlow(TextFieldState())
    val endDateState = _endDateState.asLiveData(viewModelScope.coroutineContext)

    /**
     * String containing the selected end date
     */
    val endDateString: LiveData<String> =
        _endDateState.combine(_reminder) { state, reminder ->
            if (!isEditing && !state.dirty) {
                ""
            } else reminder.schedulingRules.endDate.let {
                if (it == LocalDate.now()) {
                    "Today"
                } else {
                    it.getShortLocalDateDisplay()
                }
            }
        }.asLiveData(viewModelScope.coroutineContext)

    /**
     * Error state for time
     */
    private val _timeState = MutableStateFlow(TextFieldState())
    val timeState = _timeState.asLiveData(viewModelScope.coroutineContext)

    /**
     * Display string for selected time
     */
    val timeDisplayString: LiveData<String> =
        _timeState.combine(_reminder) { state, reminder ->
            if (!isEditing && !state.dirty) {
                ""
            } else reminder.getTimeDisplayString()
        }.asLiveData(viewModelScope.coroutineContext)


    /**
     * Error state for snooze options
     */
    private val _snoozeRulesState = MutableStateFlow(TextFieldState())
    val snoozeRulesState = _snoozeRulesState.asLiveData(viewModelScope.coroutineContext)

    /**
     * Display string for selected snooze options
     */
    val snoozeDisplayString: LiveData<String> =
        _snoozeRulesState.combine(_reminder) { state, reminder ->
            if (!isEditing && !state.dirty) "" else reminder.snoozeRules.getDisplayString()
        }.asLiveData(viewModelScope.coroutineContext)


    /**
     * Error state for sound & vibrate options
     */
    private val _soundState = MutableStateFlow(TextFieldState())
    val soundState = _soundState.asLiveData(viewModelScope.coroutineContext)

    /**
     * String containing current selected vibrate and sound options
     */
    val vibrateAndSoundDisplayString: LiveData<String> =
        _soundState.combine(_reminder) { state, reminder ->
            if (!isEditing && !state.dirty) {
                ""
            } else (if (_reminder.value.vibrate) "Vibrate" else "No vibrate") + ", ${
                getSoundTitle(
                    reminder.notifSoundUri
                )
            }"
        }.asLiveData(viewModelScope.coroutineContext)

    /**
     * "Repeats" Checkbox state
     */
    private val _repeats = MutableStateFlow(false)
    val repeats = _repeats.asLiveData()

    /**
     * "End date" Checkbox state
     */
    private val _hasEndDate = MutableStateFlow(false)
    val hasEndDate = _hasEndDate.asLiveData()

    private val _navigateToReminders = MutableEventFlow<Unit>()
    val navigateToReminders = _navigateToReminders.asEventFlow()

    var isEditing by Delegates.notNull<Boolean>()
        private set

    init {
        val savedReminderId = savedStateHandle.get<String>(EXTRA_REMINDER_ID).orEmpty()
        isEditing = savedReminderId.isNotEmpty()

        if (isEditing) {
            viewModelScope.launch {
                reminderRepository.findById(savedReminderId)?.let {
                    _reminder.value = it
                    _repeats.value = _reminder.value.schedulingRules.type != SchedulingType.SINGLE
                    _hasEndDate.value =
                        _reminder.value.schedulingRules.type == SchedulingType.REPEATING
                }
            }
        } else {
            // If navigated to from calendar, startDate should be a stringified LocalDate to fill in
            // as the new reminder startDate
            val startDate = savedStateHandle.get<String>(EXTRA_REMINDER_START_DATE).orEmpty()
            if (startDate.isNotBlank()) {
                _reminder.value = _reminder.value.copy(
                    schedulingRules = _reminder.value.schedulingRules.copy(
                        startDate = LocalDate.parse(startDate)
                    )
                )
                _startDateState.value = _startDateState.value.copy(dirty = true)
            }
        }
    }

    fun cancel() {
        viewModelScope.launch { _navigateToReminders.value = Unit }
    }

    fun onStartDateChanged(newDate: LocalDate) {
        _reminder.value = _reminder.value.let {
            it.copy(schedulingRules = it.schedulingRules.copy(startDate = newDate))
        }
        _startDateState.value = _startDateState.value.copy(dirty = true)
        checkDateErrors()
        checkTimeErrors()
    }

    fun onEndDateChanged(newDate: LocalDate) {
        _reminder.value = _reminder.value.let {
            it.copy(schedulingRules = it.schedulingRules.copy(endDate = newDate))
        }
        _endDateState.value = _endDateState.value.copy(dirty = true)
        checkDateErrors()
    }

    private fun checkDateErrors() {
        with(_reminder.value.schedulingRules) {
            _startDateState.value =
                _startDateState.value.copy(
                    errorEnabled = startDate < LocalDate.now() || startDate > endDate && _hasEndDate.value
                )
            if (type == SchedulingType.REPEATING) {
                _endDateState.value =
                    _endDateState.value.copy(
                        errorEnabled = endDate <= _reminder.value.schedulingRules.startDate
                    )
            }
        }

    }

    fun setRepeat(repeat: Boolean) {
        if (_repeats.value == repeat) {
            return
        }

        _repeats.value = repeat
        _daysOfWeekState.value = _daysOfWeekState.value.copy(dirty = false, errorEnabled = false)
        _reminder.value = _reminder.value.let { reminder ->
            reminder.copy(
                schedulingRules = if (repeat) {
                    // Repeats some amount, so check hasEndDate to retain endDate state
                    if (_hasEndDate.value) {
                        reminder.schedulingRules.copy(
                            type = SchedulingType.REPEATING,
                        )
                    } else {
                        reminder.schedulingRules.copy(
                            type = SchedulingType.REPEAT_FOREVER,
                            daysOfWeek = DayOfWeek.values().toSortedSet()
                        )
                    }.also {
                        onDaysOfWeekChanged(it.daysOfWeek)
                    }
                } else {
                    reminder.schedulingRules.copy(
                        type = SchedulingType.SINGLE,
                    )
                }
            )
        }
    }

    fun setHasEndDate(hasEndDate: Boolean) {
        if (_hasEndDate.value == hasEndDate) {
            return
        }

        _hasEndDate.value = hasEndDate
        if (hasEndDate) {
            _reminder.value = _reminder.value.let {
                it.copy(
                    schedulingRules = it.schedulingRules.copy(
                        type = SchedulingType.REPEATING
                    )
                )
            }
            _endDateState.value = _endDateState.value.copy(errorEnabled = false)
        } else {
            _reminder.value = _reminder.value.let {
                it.copy(
                    schedulingRules = it.schedulingRules.copy(type = SchedulingType.REPEAT_FOREVER)
                )
            }
        }
    }

    fun onDaysOfWeekChanged(newValue: SortedSet<DayOfWeek>) {
        _reminder.value = _reminder.value.let {
            it.copy(schedulingRules = it.schedulingRules.copy(daysOfWeek = newValue))
        }
        _daysOfWeekState.value =
            _daysOfWeekState.value.copy(dirty = true, errorEnabled = newValue.isEmpty())
    }

    fun onTimeChanged(newTime: LocalTime) {
        _reminder.value = _reminder.value.let {
            it.copy(schedulingRules = it.schedulingRules.copy(time = newTime))
        }
        _timeState.value = _timeState.value.copy(dirty = true)
        checkTimeErrors()
    }

    private fun checkTimeErrors() {
        val errorEnabled = with(_reminder.value) {
            val startDateTime = schedulingRules.startDate.atTime(schedulingRules.time)
            schedulingRules.type == SchedulingType.SINGLE && startDateTime < currentLocalDateTime()
        }
        _timeState.value = _timeState.value.copy(errorEnabled = errorEnabled)
    }

    fun onSnoozeRulesChanged(newRules: SnoozeRules) {
        _snoozeRulesState.value = _snoozeRulesState.value.copy(dirty = true)
        _reminder.value = _reminder.value.copy(snoozeRules = newRules)
    }

    fun onVibrateChanged(newValue: Boolean) {
        _soundState.value = _soundState.value.copy(dirty = true)
        _reminder.value = _reminder.value.copy(vibrate = newValue)
    }

    fun onSoundUriChanged(newValue: String) {
        _soundState.value = _soundState.value.copy(dirty = true)
        _reminder.value = _reminder.value.copy(notifSoundUri = newValue)
    }

    private fun formHasErrors(): Boolean =
        _daysOfWeekState.value.errorEnabled || _startDateState.value.errorEnabled ||
                _endDateState.value.errorEnabled || _timeState.value.errorEnabled ||
                _snoozeRulesState.value.errorEnabled || _soundState.value.errorEnabled

    fun saveReminder() {
        // Check for errors again before saving
        checkDateErrors()
        checkTimeErrors()

        // Do not save if there are errors
        if (formHasErrors()) {
            showToast(
                getApplication(),
                "Please fix any errors before saving",
            )
            return
        }

        // If description is empty, save a generic description for clarity
        if (_reminder.value.description.isBlank()) {
            _reminder.value =
                _reminder.value.copy(
                    description = "Reminder saved on ${
                        LocalDate.now().getShortLocalDateDisplay()
                    }"
                )
        }

        applicationScope.launch {
            _reminder.value.copy().let {
                // get the "sanitized" reminder
                getReminderToSave(it)
            }.let { reminder ->
                reminderRepository.save(reminder)

                val wasScheduled = stopAlarmsForReminder(getApplication(), reminder)

                var nextAlarmId = AlarmActionHandler.NO_ALARM_ID
                reminder.schedulingRules.getNextTimeForAlarm(
                    currentLocalDateTime(),
                    allowSameDay = true
                )?.let { time ->
                    val nextAlarm = Alarm(
                        reminderId = reminder.id,
                        triggerAt = time,
                        maxSnoozeRepeats = reminder.maxSnoozeRepeats,
                        snoozeDuration = reminder.snoozeDuration
                    )
                    nextAlarm.id = alarmRepository.save(nextAlarm)
                    nextAlarmId = nextAlarm.id
                }

                // stop currently running AlarmService if saved reminder is the one scheduled
                if (wasScheduled) {
                    AlarmService.requestStop(getApplication())
                }

                scheduleNextAlarm(nextAlarmId)
            }
        }

        _navigateToReminders.value = Unit
    }

    // Get a reminder with "initial" values for its type to save, so old data won't show up later
    private fun getReminderToSave(reminder: Reminder) = when (reminder.schedulingRules.type) {
        SchedulingType.SINGLE -> reminder.schedulingRules.let { oldSchedule ->
            reminder.copy(
                schedulingRules = oldSchedule.copy(
                    endDate = oldSchedule.startDate.plusDays(1),
                    daysOfWeek = sortedSetOf()
                )
            )
        }
        SchedulingType.REPEATING -> reminder // do nothing, all values are used
        SchedulingType.REPEAT_FOREVER -> reminder.schedulingRules.let { oldSchedule ->
            reminder.copy(
                schedulingRules = oldSchedule.copy(
                    endDate = oldSchedule.startDate.plusDays(1),
                )
            )
        }
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
                        scheduleNextAlarm(AlarmActionHandler.NO_ALARM_ID)
                    }
                }

                reminderRepository.delete(reminder)
            }
        }

        _navigateToReminders.value = Unit
    }

    private fun getSoundTitle(soundUri: String) = if (soundUri.isEmpty()) "No sound" else
        RingtoneManager.getRingtone(getApplication(), Uri.parse(soundUri))
            .getTitle(getApplication())


    private fun scheduleNextAlarm(nextAlarmId: Long) {
        AlarmActionHandler.createForScheduleNext("", nextAlarmId).let {
            WorkManager.getInstance(getApplication()).enqueue(it)
        }
    }

    // Cancels & deletes Reminder, returning value of alarm.scheduled
    private suspend fun stopAlarmsForReminder(context: Context, reminder: Reminder): Boolean =
        alarmRepository.findByReminderId(reminder.id)?.let {
            cancelAlarmIntentsForReminder(context, reminder)
            alarmRepository.delete(it)
            it.scheduled
        } == true
}

data class TextFieldState(val errorEnabled: Boolean = false, val dirty: Boolean = false)