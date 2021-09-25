package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.SchedulingType
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())

    /**
     * Currently selected year & month
     */
    val selectedYearMonth = _selectedYearMonth.asStateFlow()

    /**
     * Currently tapped on date, including the current scrolled-to month & year, in the calendar
     */
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate = _selectedDate.asLiveData()

    /**
     * Display string for selected date's month & year. Format is full month and short year, such as
     * "September '21."
     */
    val selectedYearMonthString = _selectedYearMonth.map { date ->
        date.format(DateTimeFormatter.ofPattern("MMMM ''uu"))
    }.asLiveData()

    /**
     * Refresh "event" emitter to force calendar data to update
     */
    private val refresh = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @ExperimentalCoroutinesApi
    private val _remindersOnSelectedDate = refresh.flatMapLatest {
        _selectedDate.flatMapLatest { selectedDate ->
            selectedDate?.let {
                // Get any reminders on the selected date and only the history for the selected date
                reminderRepository.getAllInRangeWithHistoryOnDate(
                    startDate = selectedDate,
                    endDate = selectedDate,
                    historyDate = selectedDate
                ).mapLatest { reminders ->
                    reminders.filter {
                        with(it.reminder.schedulingRules) {
                            when (type) {
                                SchedulingType.SINGLE -> startDate == selectedDate
                                else -> daysOfWeek.contains(selectedDate.dayOfWeek)
                            }
                        }
                    }
                }.flowOn(dispatcher)
            } ?: flowOf(null)
        }
    }

    /**
     * List of reminders for selected date
     */
    @ExperimentalCoroutinesApi
    val remindersOnSelectedDate = _remindersOnSelectedDate.asLiveData()

    /**
     * The backing data for the Calendar. Contains all data necessary to produce
     * date views for the current month.
     */
    @ExperimentalCoroutinesApi
    val calendarData = refresh.flatMapLatest {
        _selectedYearMonth.flatMapLatest { yearMonth ->
            reminderRepository.getAllInMonth(
                yearMonth.month.value.toString().padStart(2, '0')
            )
        }
    }.mapLatest { reminders ->
        getCalendarData(_selectedYearMonth.value, reminders)
    }.flowOn(dispatcher).stateIn(
        viewModelScope,
        started = SharingStarted.Lazily,
        // Use placeholder data as initial data
        (0 until CalendarAdapter.ITEM_COUNT).map { position ->
            if (position < CalendarAdapter.HEADING_SLOTS) {
                getHeadingItem(position)
            } else {
                // Use null date to indicate placeholder data
                PlaceholderDateItem
            }
        }
    ).asLiveData()

    init {
        Timber.d("Init VM")
    }

    /**
     * Changes the selected date, and starts a job to load the reminders for that date.
     */
    fun setSelectedDate(date: LocalDate?) {
        if (date == _selectedDate.value) {
            return
        }
        _selectedDate.value = date
    }

    /**
     * Changes the calendar month to the next sequential month.
     */
    fun goToNextMonth() {
        setSelectedMonth(_selectedYearMonth.value.plusMonths(1))
    }

    /**
     * Changes the calendar month to the previous sequential month.
     */
    fun goToPreviousMonth() {
        if (_selectedYearMonth.value == YearMonth.now()) {
            // Do not go into the past if on the starting month
            Timber.d("goToPreviousMonth: data was loading, skipped action")
            return
        }
        setSelectedMonth(_selectedYearMonth.value.minusMonths(1))
    }

    /**
     * Causes the calendarData to refresh. Useful if Reminders have been added or removed
     * and one wants to notify the Calendar to update.
     */
    fun refreshData() {
        Timber.d("Refreshing data")
        refresh.tryEmit(Unit)
    }

    private fun setSelectedMonth(yearMonth: YearMonth) {
        setSelectedDate(null)
        _selectedYearMonth.value = yearMonth
    }

    private suspend fun getCalendarData(
        yearMonth: YearMonth,
        remindersInSelectedMonth: List<Reminder>
    ) = coroutineScope {
        try {
            val monthData = CalculatedMonthData(yearMonth, remindersInSelectedMonth)
            val items = (0 until CalendarAdapter.ITEM_COUNT).map { position ->
                async {
                    if (position < CalendarAdapter.HEADING_SLOTS) {
                        getHeadingItem(position)
                    } else {
                        getDateItem(position, monthData)
                    }
                }
            }
            Timber.d("getCalendarData finished")
            items.awaitAll()
        } catch (e: CancellationException) {
            Timber.d("getCalendarData: cancelled")
            null
        }
    }

    private fun getHeadingItem(position: Int) = DayOfWeek.of(if (position == 0) 7 else position)

    private fun getDateItem(
        position: Int,
        monthData: CalculatedMonthData
    ): CalendarDateItem {

        return with(monthData) {
            val date = calculateDateFromPosition(position, monthData)
            val isCurrentMonth = date.month == monthData.yearMonthDate.month
            var hasReminder = false

            if (isCurrentMonth) {
                hasReminder =
                    daysWithReminders.elementAtOrNull(position - firstPositionOfMonth)
                        ?: false
            }

            CalendarDateItem(date, isCurrentMonth, hasReminder)
        }
    }

    private fun getDaysWithReminders(
        yearMonth: YearMonth,
        reminders: List<Reminder>
    ) = (1..yearMonth.lengthOfMonth()).map { dayOfMonth ->
        // Transform each array index representing a dayOfMonth into a true/false
        // value for presence of reminders
        val date = LocalDate.of(yearMonth.year, yearMonth.monthValue, dayOfMonth)
        reminders.any {
            it.schedulingRules.containsDate(date)
        }
    }

    private fun calculateDateFromPosition(position: Int, monthData: CalculatedMonthData) =
        with(monthData) {
            when {
                position in firstPositionOfMonth..lastPositionOfMonth -> {
                    yearMonthDate.withDayOfMonth(position - firstPositionOfMonth + 1)
                }
                position < firstPositionOfMonth -> yearMonthDate.minusMonths(1)
                    .withDayOfMonth(lastDayOfPreviousMonth - firstPositionOfMonth + position + 1)
                position > lastPositionOfMonth -> yearMonthDate.plusMonths(1)
                    .withDayOfMonth(position - lastPositionOfMonth)
                else -> throw IllegalStateException("Invalid position $position")
            }
        }

    private inner class CalculatedMonthData(yearMonth: YearMonth, reminders: List<Reminder>) {
        // Date adjusted for yearMonth
        val yearMonthDate: LocalDate = LocalDate.of(yearMonth.year, yearMonth.month, 1)

        // Days in selected month
        val daysInMonth: Int = yearMonth.lengthOfMonth()

        // First day of week of selected month
        val firstDayOfWeek: DayOfWeek = yearMonthDate.dayOfWeek

        // First position in the adapter that contains a day of the selected month
        val firstPositionOfMonth: Int = CalendarAdapter.HEADING_SLOTS + firstDayOfWeek.value % 7

        // Last position of a day of the current month
        val lastPositionOfMonth: Int = firstPositionOfMonth + daysInMonth - 1

        // Last calendar day (not position) of previous month
        val lastDayOfPreviousMonth: Int = yearMonth.minusMonths(1).lengthOfMonth()

        val daysWithReminders = getDaysWithReminders(yearMonth, reminders)
    }
}