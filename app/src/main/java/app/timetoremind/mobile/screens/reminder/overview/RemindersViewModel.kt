package app.timetoremind.mobile.screens.reminder.overview

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import app.timetoremind.mobile.broadcast.*
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.ReminderAndHistory
import app.timetoremind.mobile.data.domain.ReminderStatus
import app.timetoremind.mobile.data.domain.SchedulingRules
import app.timetoremind.mobile.di.AppCoroutineScope
import app.timetoremind.mobile.di.DefaultDispatcher
import app.timetoremind.mobile.di.IoDispatcher
import app.timetoremind.mobile.service.AlarmHistoryService
import app.timetoremind.mobile.service.AlarmMediaService
import app.timetoremind.mobile.service.AlarmSchedulingService
import app.timetoremind.mobile.util.MutableEventFlow
import app.timetoremind.mobile.util.asEventFlow
import app.timetoremind.mobile.util.currentLocalDateTime
import app.timetoremind.mobile.util.toEpochMilli
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
        PreferenceManager.getDefaultSharedPreferences(application)
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
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val _reminders: StateFlow<List<ReminderAndHistory>?> = combine(
        _sortAndFilterRules,
        reminderRepository.getAllWithTodayHistory()
    ) { sortAndFilterRules, reminderAndHistory ->
        sortAndFilterRules to filterReminders(reminderAndHistory, sortAndFilterRules.filterRules)
    }.flatMapLatest { (sortAndFilterRules, reminderAndHistory) ->
        reminderAndHistory.map {
            sortReminders(it, sortAndFilterRules.sortRules)
        }
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
            withContext(NonCancellable) {
                if (stopAlarmsForReminder(getApplication(), reminder)) {
                    AlarmMediaService.requestStop(getApplication())
                }
                reminderRepository.delete(reminder)
                AlarmSchedulingService.startScheduleNext(getApplication())
            }
        }
    }

    /**
     * Completely removes all reminders (but not their history).
     */
    fun deleteAllReminders() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                AlarmMediaService.requestStop(getApplication())
                clearAllAlarmIntents()
                reminderRepository.deleteAll()
            }
        }
    }

    /**
     * Marks a reminder as complete for today, starting the process of
     * adding a history entry and scheduling the next alarm
     */
    fun completeReminder(reminder: Reminder) {
        viewModelScope.launch {
            if (stopAlarmsForReminder(getApplication(), reminder)) {
                AlarmMediaService.requestStop(getApplication())
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
            if (stopAlarmsForReminder(getApplication(), reminder)) {
                AlarmMediaService.requestStop(getApplication())
            }
            changeReminderStatus(reminder, ReminderStatus.SKIPPED)
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

    private suspend fun clearAllAlarmIntents() = withContext(NonCancellable) {
        reminderRepository.getAll().first().map {
            Timber.d("Cancelling reminder alarms for $it")
            cancelAlarmIntentsForReminder(getApplication(), it)
        }
    }

    private fun changeReminderStatus(reminder: Reminder, status: ReminderStatus) {
        when (status) {
            ReminderStatus.SKIPPED -> {
                AlarmHistoryService.start(getApplication(), reminder, ReminderStatus.SKIPPED)
            }
            ReminderStatus.COMPLETED -> {
                AlarmHistoryService.start(getApplication(), reminder, ReminderStatus.COMPLETED)
            }
            else -> throw IllegalArgumentException("Cannot change status to PENDING")
        }
        AlarmSchedulingService.startScheduleNext(getApplication())
        AlarmSchedulingService.startScheduleNewAlarm(getApplication(), reminder)
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
            nextTimeOptions = SchedulingRules.NextTimeOptions.ALLOW_ALL
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
    ): Flow<List<ReminderAndHistory>> {
        val filtered = mutableListOf<ReminderAndHistory>()
        list.asFlow().filter {
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
        }.toList(filtered)
        return flow {
            emit(filtered)
        }
    }

    private fun sortReminders(
        list: List<ReminderAndHistory>,
        sortRules: ReminderSortRules
    ): List<ReminderAndHistory> {
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