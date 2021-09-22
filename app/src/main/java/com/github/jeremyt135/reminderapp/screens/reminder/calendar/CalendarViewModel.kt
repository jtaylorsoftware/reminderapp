package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.SchedulingType
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    /**
     * Currently selected year & month
     */
    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())

    /**
     * List of reminders in the currently selected yearMonth. Used to compute dates
     * that should have an indicator dot displayed.
     */
    private val _remindersInSelectedMonth = _selectedYearMonth.map { selectedYearMonth ->
        reminderRepository.getAllInMonth(
            selectedYearMonth.month.value.toString().padStart(2, '0')
        ).first()
    }.stateIn(
        viewModelScope,
        started = SharingStarted.Lazily,
        emptyList()
    )

    /**
     * Currently tapped on date, including the current scrolled-to month & year, in the calendar
     */
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)

    /**
     * Display string for selected date's month & year. Format is full month and short year, such as
     * "September '21."
     */
    val selectedYearMonthString = _selectedYearMonth.map { date ->
        date.format(DateTimeFormatter.ofPattern("MMMM ''uu"))
    }.asLiveData()

    private val _remindersOnSelectedDate = _selectedDate.map { selectedDate ->
        selectedDate?.let {
            // Get any reminders on the selected date and only the history for the selected date
            reminderRepository.getAllInRangeWithHistoryOnDate(
                startDate = selectedDate,
                endDate = selectedDate,
                historyDate = selectedDate
            ).first().filter {
                with(it.reminder.schedulingRules) {
                    when (type) {
                        SchedulingType.SINGLE -> startDate == selectedDate
                        else -> daysOfWeek.contains(selectedDate.dayOfWeek)
                    }
                }
            }
        } ?: emptyList()
    }.flowOn(dispatcher).stateIn(
        viewModelScope,
        started = SharingStarted.Lazily,
        emptyList()
    )

    /**
     * List of reminders for selected date
     */
    val remindersOnSelectedDate = _remindersOnSelectedDate.asLiveData()

    /**
     * The backing data for the Calendar. Contains all data necessary to produce
     * date views for the current month.
     */
    val calendarData =
        _selectedYearMonth.combine(_remindersInSelectedMonth) { yearMonth, reminders ->
            // Transform each array index representing a dayOfMonth into a true/false
            // value for presence of reminders
            val daysWithReminders = (1..yearMonth.lengthOfMonth()).map { dayOfMonth ->
                val date = LocalDate.of(yearMonth.year, yearMonth.monthValue, dayOfMonth)
                reminders.any {
                    it.schedulingRules.containsDate(date)
                }
            }
            CalendarData(yearMonth, daysWithReminders)
        }.flowOn(dispatcher).stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            CalendarData()
        )

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
        setSelectedMonth(_selectedYearMonth.value.minusMonths(1))
    }

    private fun setSelectedMonth(yearMonth: YearMonth) {
        setSelectedDate(null)
        _selectedYearMonth.value = yearMonth
    }
}