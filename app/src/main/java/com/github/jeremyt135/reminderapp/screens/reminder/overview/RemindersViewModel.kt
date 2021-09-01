package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.broadcast.ACTION_ALARM_SCHEDULE_NEXT
import com.github.jeremyt135.reminderapp.broadcast.cancelAlarmIntentsForReminder
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.asEventFlow
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    application: Application,
    private val reminderRepository: ReminderRepository,
    private val alarmRepository: AlarmRepository,
    private val preferences: SharedPreferences,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : AndroidViewModel(application) {
    /**
     * Determines how to sort `reminders`
     */
    lateinit var reminderSortRules: ReminderSortRules
        private set

    /**
     * Determines what data to include in `reminders`
     */
    lateinit var reminderFilterRules: ReminderFilterRules
        private set

    private val _reminders = MutableStateFlow(emptyList<ReminderAndHistory>())

    /**
     * List of `ReminderAndHistory` where each object's `History` corresponds to
     * the most recent saved `History` entity for its `Reminder`.
     */
    val reminders: LiveData<List<ReminderAndHistory>> =
        _reminders.asLiveData(viewModelScope.coroutineContext)

    private val _navigateToEditor = MutableEventFlow<Reminder>()

    /**
     * Navigation event triggered when adding or editing a reminder.
     */
    val navigateToEditor = _navigateToEditor.asEventFlow()

    init {
        loadSortPreferences()
        loadFilterPreferences()
        sortAndFilterList()
    }

    private fun loadSortPreferences() {
        val sortTime = preferences.getString("SORT_REMINDER_TIME", "NONE")!!
        val sortDescription = preferences.getString("SORT_REMINDER_DESCRIPTION", "NONE")!!
        reminderSortRules =
            ReminderSortRules(SortRule.valueOf(sortTime), SortRule.valueOf(sortDescription))
    }

    private fun loadFilterPreferences() {
        val filterToday = preferences.getBoolean("FILTER_REMINDER_TODAY", true)
        val filterDaysOfWeek =
            preferences.getString("FILTER_REMINDER_DAYS", DayOfWeek.values().joinToString(","))!!
                .let { storedValue ->
                    if (storedValue.isEmpty()) {
                        emptySet()
                    } else {
                        storedValue.split(",").map { DayOfWeek.valueOf(it) }.toSet()
                    }
                }
        val filterStatus = preferences.getString(
            "FILTER_REMINDER_STATUS",
            ReminderStatus.values().joinToString(",")
        )!!.let { storedValue ->
            if (storedValue.isEmpty()) {
                emptySet()
            } else {
                storedValue.split(",").map { ReminderStatus.valueOf(it) }.toSet()
            }
        }
        reminderFilterRules = ReminderFilterRules(filterToday, filterDaysOfWeek, filterStatus)
    }

    /**
     * Navigates to the editor with a new Reminder.
     */
    fun addReminder() {
        viewModelScope.launch {
            _navigateToEditor.value = Reminder(id = "")
        }
    }

    /**
     * Navigates to the editor with an existing Reminder.
     */
    fun editReminder(reminder: Reminder) {
        viewModelScope.launch {
            _navigateToEditor.value = reminder
        }
    }

    /**
     * Deletes a reminder.
     * @param reminder The reminder to delete.
     */
    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            rescheduleAlarms(reminder)
            reminderRepository.delete(reminder)
        }
    }

    /**
     * Completely removes all reminders (but not their history).
     */
    fun deleteAllReminders() {
        viewModelScope.launch {
            reminderRepository.deleteAll()
            AlarmService.requestStop(getApplication())
        }
    }

    /**
     * Cancels alarms for `reminder` and reschedules next alarm.
     */
    private suspend fun rescheduleAlarms(reminder: Reminder) {
        if (stopAlarmsForReminder(getApplication(), reminder)) {
            AlarmService.requestStop(getApplication())
            scheduleNextAlarm()
        }
    }

    /**
     * Cancels & deletes Reminder, returning value of alarm.scheduled
     */
    private suspend fun stopAlarmsForReminder(context: Context, reminder: Reminder): Boolean =
        alarmRepository.findByReminderId(reminder.id)?.let {
            Timber.d("Cancelling previous alarm")
            cancelAlarmIntentsForReminder(context, reminder)
            alarmRepository.delete(it)
            it.scheduled // nullable bool
        } == true // force to nonnull bool

    private fun scheduleNextAlarm() {
        AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, "").let {
            WorkManager.getInstance(getApplication()).enqueue(it)
        }
    }

    /**
     * Changes the rules for sorting the `reminders` list. As a side effect,
     * the `reminders` list will be updated and emitted.
     * @param newRules The updated sorting rules to use.
     */
    fun changeSortRules(newRules: ReminderSortRules) {
        if (newRules == reminderSortRules) {
            return
        }
        reminderSortRules = newRules
        saveSortRules()
        sortAndFilterList()
    }

    private fun saveSortRules() {
        with(preferences.edit()) {
            putString("SORT_REMINDER_TIME", reminderSortRules.time.toString())
            putString("SORT_REMINDER_DESCRIPTION", reminderSortRules.description.toString())
            apply()
        }
    }

    private fun sortAndFilterList() {
        viewModelScope.launch {
            reminderRepository.getAllWithTodayHistory().map { list ->
                list.filter {
                    // does the filter include today?
                    val today =
                        reminderFilterRules.includeToday && it.reminder.schedulingRules.isOneOff
                    // does the filter include at least one of the reminder's DayOfWeeks?
                    val dayOfWeek =
                        it.reminder.schedulingRules.daysOfWeek.any { day -> day in reminderFilterRules.daysOfWeek }
                    // does the filter include the reminder's status?
                    val status =
                        it.history?.completionStatus ?: ReminderStatus.PENDING in reminderFilterRules.reminderStatuses

                    // include reminder if the date or time is included and its status is included
                    (today || dayOfWeek) && status
                }
            }.map { list ->
                // sort by time first
                var comparator = compareBy<ReminderAndHistory> {
                    when (reminderSortRules.time) {
                        SortRule.ASCENDING -> calculateTimeOrder(it.reminder)
                        SortRule.DESCENDING -> -1.0 * calculateTimeOrder(it.reminder)
                        else -> 0.0
                    }
                }
                // then sort by an alphabetical ordering of the description
                comparator = when (reminderSortRules.description) {
                    SortRule.ASCENDING ->
                        comparator.thenBy { it.reminder.description }
                    SortRule.DESCENDING ->
                        comparator.thenByDescending { it.reminder.description }
                    else -> comparator
                }

                list.sortedWith(comparator)
            }.flowOn(dispatcher).collect {
                _reminders.emit(it)
            }
        }
    }

    private fun calculateTimeOrder(reminder: Reminder): Double {
        val dayOfWeek =
            if (reminder.schedulingRules.daysOfWeek.isNotEmpty()) reminder.schedulingRules.daysOfWeek.first().value else 0
        val hour = reminder.schedulingRules.hour / 24.0 / 10
        val minute = reminder.schedulingRules.minute / 60.0 / 100
        return dayOfWeek + hour + minute
    }

    /**
     * Changes the rules for filter the `reminders` list. As a side effect,
     * the `reminders` list will be updated and emitted.
     * @param newRules The updated filter rules to use.
     */
    fun changeFilterRules(newRules: ReminderFilterRules) {
        if (newRules == reminderFilterRules) {
            return
        }
        reminderFilterRules = newRules
        saveFilterRules()
        sortAndFilterList()
    }

    private fun saveFilterRules() {
        with(preferences.edit()) {
            putBoolean("FILTER_REMINDER_TODAY", reminderFilterRules.includeToday)
            putString("FILTER_REMINDER_DAYS", reminderFilterRules.daysOfWeek.joinToString(","))
            putString(
                "FILTER_REMINDER_STATUS",
                reminderFilterRules.reminderStatuses.joinToString(",")
            )
            apply()
        }
    }
}