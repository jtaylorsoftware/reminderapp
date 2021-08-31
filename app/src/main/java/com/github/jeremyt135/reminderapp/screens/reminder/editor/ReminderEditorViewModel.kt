package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.app.Application
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.*
import androidx.work.WorkManager
import com.github.jeremyt135.BuildConfig
import com.github.jeremyt135.reminderapp.broadcast.ACTION_ALARM_SCHEDULE_NEXT
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.broadcast.cancelAlarmIntentsForReminder
import com.github.jeremyt135.reminderapp.broadcast.showToast
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.SnoozeRepetitionOption
import com.github.jeremyt135.reminderapp.data.domain.SnoozeRules
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.asEventFlow
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
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
    private val _reminder = MutableStateFlow(Reminder())

    /**
     * The Reminder being edited
     */
    val reminder = _reminder.asStateFlow()

    // How should the reminder repeat - never, forever, or no. of times
    val snoozeRepetitionOption = _reminder.map { reminder ->
        when (reminder.snoozeRules.maxRepeats) {
            0L -> SnoozeRepetitionOption.None
            Long.MAX_VALUE -> SnoozeRepetitionOption.Infinite
            else -> SnoozeRepetitionOption.Choose
        }
    }.stateIn(
        viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = SnoozeRepetitionOption.Choose
    )

    // Can the repetition be changed (is snoozeRepetitionOption "Times")
    val canChangeSnoozeRepetitions =
        snoozeRepetitionOption.map { it == SnoozeRepetitionOption.Choose }
            .asLiveData(viewModelScope.coroutineContext)

    // Can the snooze duration be changed (is snoozeRepetitionOption not "None")
    val canChangeSnoozeDuration =
        snoozeRepetitionOption.map { it != SnoozeRepetitionOption.None }
            .asLiveData(viewModelScope.coroutineContext)


    val reminderDaysDisplayString: LiveData<String> =
        _reminder.map { it.getDaysDisplayString() }.asLiveData(viewModelScope.coroutineContext)

    val selectedSound: LiveData<String> =
        _reminder.map { getSoundTitle(it.notifSoundUri) }
            .asLiveData(viewModelScope.coroutineContext)

    private val _navigateToReminders = MutableEventFlow<Unit>()
    val navigateToReminders = _navigateToReminders.asEventFlow()

    var isEditing by Delegates.notNull<Boolean>()
        private set

    init {
        val savedReminderId = savedStateHandle.get<String>(EXTRA_REMINDER_ID) ?: run {
            if (BuildConfig.DEBUG) {
                showToast(
                    getApplication<Application>().applicationContext,
                    "ReminderEditorViewModel: savedStateHandle.get(REMINDER_ID) was null",
                    Toast.LENGTH_LONG
                )
            }
            ""
        }
        isEditing = savedReminderId.isNotEmpty()

        Timber.d("reminderId = $savedReminderId")
        if (isEditing) {
            viewModelScope.launch {
                reminderRepository.findById(savedReminderId)?.let {
                    _reminder.value = it
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
        val daysOfWeek = _reminder.value.daysOfWeek.toSortedSet()
        if (daysOfWeek.contains(day)) {
            daysOfWeek.remove(day)
        } else {
            daysOfWeek.add(day)
        }
        _reminder.value = _reminder.value.copy(daysOfWeek = daysOfWeek).also {
            Timber.d("datesDisplay is now ${it.getDaysDisplayString()}")
        }
    }

    fun onTimeChanged(newHour: Int, newMinute: Int) {
        val (oldHour, oldMinute) = _reminder.value.hour to _reminder.value.minute
        if (newHour != oldHour || newMinute != oldMinute) {
            _reminder.value = _reminder.value.copy(time = LocalTime.of(newHour, newMinute))
        }
    }

    fun onSnoozeRepetitionOptionChanged(newSelection: String) {
        val newOption = SnoozeRepetitionOption.valueOf(newSelection)
        if (newOption == snoozeRepetitionOption.value) {
            return
        }

        when (newOption) {
            SnoozeRepetitionOption.None -> {
                _reminder.value = _reminder.value.copy(maxSnoozeRepeats = 0)
            }
            SnoozeRepetitionOption.Infinite -> {
                _reminder.value =
                    _reminder.value.copy(
                        maxSnoozeRepeats = Long.MAX_VALUE,
                        snoozeDuration = SnoozeRules.DEFAULT_SNOOZE_DURATION
                    )
            }
            SnoozeRepetitionOption.Choose -> {
                _reminder.value =
                    _reminder.value.copy(
                        maxSnoozeRepeats = SnoozeRules.DEFAULT_SNOOZE_REPEATS,
                        snoozeDuration = SnoozeRules.DEFAULT_SNOOZE_DURATION
                    )
            }
        }
    }

    fun setAlarmSound(sound: Uri) {
        if (sound.toString() != _reminder.value.notifSoundUri) {
            Timber.d("Setting snooze sound to $sound")
            _reminder.value = _reminder.value.copy(notifSoundUri = sound.toString())
        }
    }

    fun saveReminder() {
        applicationScope.launch {
            _reminder.value.copy().let { reminder ->
                Timber.d("Saving reminder $reminder")
                reminderRepository.save(reminder)

                val wasScheduled = stopAlarmsForReminder(getApplication(), reminder)

                val today = LocalDate.now().dayOfWeek
                reminder.getNextTimeForAlarm(today)?.let { time ->
                    val nextAlarm = Alarm(
                        reminderId = reminder.id,
                        triggerAt = time,
                        maxSnoozeRepeats = reminder.maxSnoozeRepeats,
                        snoozeDuration = reminder.snoozeDuration
                    )
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

    private fun getSoundTitle(soundUri: String) = if (soundUri.isEmpty()) "None" else
        RingtoneManager.getRingtone(getApplication(), Uri.parse(soundUri))
            .getTitle(getApplication())


    private fun scheduleNextAlarm() {
        AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, "").let {
            WorkManager.getInstance(getApplication()).enqueue(it)
        }
    }

    // Cancels & deletes Reminder, returning value of alarm.scheduled
    private suspend fun stopAlarmsForReminder(context: Context, reminder: Reminder): Boolean =
        alarmRepository.findByReminderId(reminder.id)?.let {
            Timber.d("Cancelling previous alarm")
            cancelAlarmIntentsForReminder(context, reminder)
            alarmRepository.delete(it)
            it.scheduled
        } == true
}
