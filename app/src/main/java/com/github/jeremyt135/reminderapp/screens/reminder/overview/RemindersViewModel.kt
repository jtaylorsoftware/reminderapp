package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
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
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import com.github.jeremyt135.reminderapp.di.IoDispatcher
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.settings.getBaseSharedPreferences
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.asEventFlow
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    application: Application,
    private val reminderRepository: ReminderRepository,
    private val alarmRepository: AlarmRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope = MainScope(),
) : AndroidViewModel(application) {
    private val sharedPreferences: SharedPreferences by lazy {
        application.applicationContext.getBaseSharedPreferences()
    }

    private val _sortAndFilterRules = MutableStateFlow(SortAndFilterRules())

    /**
     * Determines how to sort `reminders`
     */
    val reminderSortRules: ReminderSortRules
        get() = _sortAndFilterRules.value.sortRules

    /**
     * Determines what data to include in `reminders`
     */
    val reminderFilterRules: ReminderFilterRules
        get() = _sortAndFilterRules.value.filterRules


    @ExperimentalCoroutinesApi
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val _reminders: StateFlow<List<ReminderAndHistory>?> = combine(
        _sortAndFilterRules,
        reminderRepository.getAllWithTodayHistory()
    ) { sortAndFilterRules, reminderAndHistory ->
        val filtered = filterReminders(reminderAndHistory,sortAndFilterRules.filterRules)
        yield()
        sortReminders(filtered,sortAndFilterRules.sortRules)
    }.flowOn(dispatcher).stateIn(
        viewModelScope,
        started = SharingStarted.Lazily,
        null
    )

    /**
     * List of `ReminderAndHistory` where each object's `History` corresponds to
     * the most recent saved `History` entity for its `Reminder`.
     */
    @ExperimentalCoroutinesApi
    val reminders = _reminders.asLiveData(viewModelScope.coroutineContext)

    private val _navigateToEditor = MutableEventFlow<Reminder>()

    /**
     * Navigation event triggered when adding or editing a reminder.
     */
    val navigateToEditor = _navigateToEditor.asEventFlow()

    init {
        loadSortPreferences()
        loadFilterPreferences()
    }

    private fun loadSortPreferences() {
        viewModelScope.launch(ioDispatcher) {
            val sortTime = sharedPreferences.getString("SORT_REMINDER_TIME", "NONE")!!
            val sortDescription = sharedPreferences.getString("SORT_REMINDER_DESCRIPTION", "NONE")!!
            _sortAndFilterRules.value = _sortAndFilterRules.value.copy(
                sortRules =
                ReminderSortRules(
                    SortRule.valueOf(sortTime),
                    SortRule.valueOf(sortDescription)
                )
            )
        }
    }

    private fun loadFilterPreferences() {
        viewModelScope.launch(ioDispatcher) {
            val includeToday = sharedPreferences.getBoolean("FILTER_REMINDER_TODAY", true)
            val daysOfWeek =
                sharedPreferences.getString(
                    "FILTER_REMINDER_DAYS",
                    DayOfWeek.values().joinToString(",")
                )!!
                    .let { storedValue ->
                        if (storedValue.isEmpty()) {
                            emptySet()
                        } else {
                            storedValue.split(",").map { DayOfWeek.valueOf(it) }.toSet()
                        }
                    }
            val statuses = sharedPreferences.getString(
                "FILTER_REMINDER_STATUS",
                ReminderStatus.values().joinToString(",")
            )!!.let { storedValue ->
                if (storedValue.isEmpty()) {
                    emptySet()
                } else {
                    storedValue.split(",").map { ReminderStatus.valueOf(it) }.toSet()
                }
            }
            val filterStartDate =
                sharedPreferences.getString("FILTER_REMINDER_START_DATE", "")!!.let {
                    if (it.isNotEmpty()) LocalDate.parse(it) else null
                }
            val filterEndDate = sharedPreferences.getString("FILTER_REMINDER_END_DATE", "")!!.let {
                if (it.isNotEmpty()) LocalDate.parse(it) else null
            }
            _sortAndFilterRules.value = _sortAndFilterRules.value.copy(
                filterRules = ReminderFilterRules(
                    includeToday,
                    daysOfWeek,
                    statuses,
                    startDate = filterStartDate,
                    endDate = filterEndDate
                )
            )
        }
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
        AlarmActionHandler.createForScheduleNext( "", 0).let {
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
        _sortAndFilterRules.value = _sortAndFilterRules.value.copy(
            sortRules = newRules
        )
        saveSortRules()
    }

    @SuppressLint("ApplySharedPref")
    private fun saveSortRules() {
        appCoroutineScope.launch(ioDispatcher) {
            with(sharedPreferences.edit()) {
                putString("SORT_REMINDER_TIME", reminderSortRules.time.toString())
                putString("SORT_REMINDER_DESCRIPTION", reminderSortRules.description.toString())
                commit()
            }
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
        _sortAndFilterRules.value = _sortAndFilterRules.value.copy(
            filterRules = newRules
        )
        saveFilterRules()
    }

    @SuppressLint("ApplySharedPref")
    private fun saveFilterRules() {
        appCoroutineScope.launch(ioDispatcher) {
            with(sharedPreferences.edit()) {
                putBoolean("FILTER_REMINDER_TODAY", reminderFilterRules.includeToday)
                putString("FILTER_REMINDER_DAYS", reminderFilterRules.daysOfWeek.joinToString(","))
                putString(
                    "FILTER_REMINDER_STATUS",
                    reminderFilterRules.reminderStatuses.joinToString(",")
                )
                putString("FILTER_REMINDER_START_DATE", reminderFilterRules.startDate?.toString())
                putString("FILTER_REMINDER_END_DATE", reminderFilterRules.endDate?.toString())
                commit()
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

    private data class SortAndFilterRules(
        val filterRules: ReminderFilterRules = ReminderFilterRules(),
        val sortRules: ReminderSortRules = ReminderSortRules()
    )

    private suspend fun filterReminders(
        list: List<ReminderAndHistory>,
        filterRules: ReminderFilterRules
    ): List<ReminderAndHistory> = list.filter {
        yield()
        // is the reminder within the filter's start and end date
        val withinDateRange =
            filterRules.checkWithinDateRange(it.reminder.schedulingRules)
        if (!withinDateRange) {
            return@filter false
        }

        // does the filter include at least one of the reminder's DayOfWeeks?
        val withinDayOfWeek =
            filterRules.checkWithinDaysOfWeek(it.reminder.schedulingRules)
        // does the filter include the reminder's status?
        val status =
            it.history?.completionStatus ?: ReminderStatus.PENDING in filterRules.reminderStatuses

        // include reminder if the date or time is included and its status is included
        withinDateRange && withinDayOfWeek && status
    }

    private suspend fun sortReminders(
        list: List<ReminderAndHistory>,
        sortRules: ReminderSortRules
    ): List<ReminderAndHistory> {
        yield()
        // sort by time first
        var comparator = compareBy<ReminderAndHistory> {
            when (sortRules.time) {
                SortRule.ASCENDING -> calculateTimeOrder(it.reminder)
                SortRule.DESCENDING -> -1.0 * calculateTimeOrder(it.reminder)
                else -> 0.0
            }
        }
        // then sort by an alphabetical ordering of the description
        comparator = when (sortRules.description) {
            SortRule.ASCENDING ->
                comparator.thenBy(String.CASE_INSENSITIVE_ORDER) { it.reminder.description }
            SortRule.DESCENDING ->
                comparator.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.reminder.description }
            else -> comparator
        }

        return list.sortedWith(comparator)
    }
}