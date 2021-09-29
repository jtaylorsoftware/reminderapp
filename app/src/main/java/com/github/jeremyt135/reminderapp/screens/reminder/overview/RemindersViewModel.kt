package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.broadcast.ACTION_ALARM_COMPLETE
import com.github.jeremyt135.reminderapp.broadcast.ACTION_ALARM_SCHEDULE_NEXT
import com.github.jeremyt135.reminderapp.broadcast.ACTION_ALARM_SKIP
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
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
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
        val includeToday = preferences.getBoolean("FILTER_REMINDER_TODAY", true)
        val daysOfWeek =
            preferences.getString("FILTER_REMINDER_DAYS", DayOfWeek.values().joinToString(","))!!
                .let { storedValue ->
                    if (storedValue.isEmpty()) {
                        emptySet()
                    } else {
                        storedValue.split(",").map { DayOfWeek.valueOf(it) }.toSet()
                    }
                }
        val statuses = preferences.getString(
            "FILTER_REMINDER_STATUS",
            ReminderStatus.values().joinToString(",")
        )!!.let { storedValue ->
            if (storedValue.isEmpty()) {
                emptySet()
            } else {
                storedValue.split(",").map { ReminderStatus.valueOf(it) }.toSet()
            }
        }
        val filterStartDate = preferences.getString("FILTER_REMINDER_START_DATE", "")!!.let {
            if (it.isNotEmpty()) LocalDate.parse(it) else null
        }
        val filterEndDate = preferences.getString("FILTER_REMINDER_END_DATE", "")!!.let {
            if (it.isNotEmpty()) LocalDate.parse(it) else null
        }
        reminderFilterRules = ReminderFilterRules(
            includeToday,
            daysOfWeek,
            statuses,
            startDate = filterStartDate,
            endDate = filterEndDate
        )
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
            AlarmService.requestStop(getApplication())
            clearAllAlarmIntents()
            reminderRepository.deleteAll()
        }
    }

    /**
     * Marks a reminder as complete for today, starting the process of
     * adding a history entry and scheduling the next alarm
     */
    fun completeReminder(reminder: Reminder) {
        viewModelScope.launch {
            if (reminderAlarmIsScheduled(reminder)) {
                AlarmService.requestStop(getApplication())
            }
            changeReminderStatus(reminder, ReminderStatus.COMPLETED)
        }
    }

    /**
     * Marks a reminder as skipped for today, starting the process of
     * adding a history entry and scheduling the next alarm
     */
    fun skipReminder(reminder: Reminder) {
        viewModelScope.launch {
            if (reminderAlarmIsScheduled(reminder)) {
                AlarmService.requestStop(getApplication())
            }
            changeReminderStatus(reminder, ReminderStatus.SKIPPED)
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
     * Cancels & deletes Reminder's current alarm, if it exists, returning value of alarm.scheduled
     */
    private suspend fun stopAlarmsForReminder(context: Context, reminder: Reminder): Boolean =
        alarmRepository.findByReminderId(reminder.id)?.let {
            Timber.d("Cancelling previous alarm")
            cancelAlarmIntentsForReminder(context, reminder)
            alarmRepository.delete(it)
            it.scheduled // nullable bool
        } == true // force to nonnull bool

    private suspend fun reminderAlarmIsScheduled(reminder: Reminder): Boolean =
        alarmRepository.findByReminderId(reminder.id)?.scheduled == true

    private fun scheduleNextAlarm() {
        AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, "").let {
            WorkManager.getInstance(getApplication()).enqueue(it)
        }
    }

    private suspend fun clearAllAlarmIntents() {
        reminderRepository.getAll().first().map {
            Timber.d("Cancelling reminder alarms for $it")
            cancelAlarmIntentsForReminder(getApplication(), it)
        }
    }

    private fun changeReminderStatus(reminder: Reminder, status: ReminderStatus) {
        val action = when (status) {
            ReminderStatus.COMPLETED -> ACTION_ALARM_COMPLETE
            ReminderStatus.SKIPPED -> ACTION_ALARM_SKIP
            else -> throw IllegalArgumentException("Cannot change status to PENDING")
        }
        AlarmActionHandler.createForAction(action, reminder.id).let {
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
            putString("FILTER_REMINDER_START_DATE", reminderFilterRules.startDate?.toString())
            putString("FILTER_REMINDER_END_DATE", reminderFilterRules.endDate?.toString())
            apply()
        }
    }

    private fun sortAndFilterList() {
        viewModelScope.launch {
            reminderRepository.getAllWithTodayHistory().map { list ->
                list.filter {
                    // is the reminder within the filter's start and end date
                    val withinDateRange =
                        reminderFilterRules.checkWithinDateRange(it.reminder.schedulingRules)
                    if (!withinDateRange) {
                        return@filter false
                    }

                    // does the filter include at least one of the reminder's DayOfWeeks?
                    val withinDayOfWeek =
                        reminderFilterRules.checkWithinDaysOfWeek(it.reminder.schedulingRules)
                    // does the filter include the reminder's status?
                    val status =
                        it.history?.completionStatus ?: ReminderStatus.PENDING in reminderFilterRules.reminderStatuses

                    // include reminder if the date or time is included and its status is included
                    withinDateRange && withinDayOfWeek && status
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
                        comparator.thenBy(String.CASE_INSENSITIVE_ORDER) { it.reminder.description }
                    SortRule.DESCENDING ->
                        comparator.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.reminder.description }
                    else -> comparator
                }

                list.sortedWith(comparator)
            }.flowOn(dispatcher).collect {
                _reminders.emit(it)
            }
        }
    }

    private fun calculateTimeOrder(reminder: Reminder): Long {
        return reminder.schedulingRules.getNextTimeForAlarm(
            currentLocalDateTime(),
            allowSameDay = true
        )?.toEpochMilli()
            ?: 0
    }
}