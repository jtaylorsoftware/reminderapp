package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
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

    private val _remindersInSelectedMonth = MutableStateFlow<List<Reminder>>(emptyList())

    /**
     * Currently tapped on date, including the current scrolled-to month & year, in the calendar
     */
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    /**
     * Display string for selected date's month & year. Format is full month and short year, such as
     * "September '21."
     */
    val selectedYearMonthString = _selectedYearMonth.map { date ->
        date.format(DateTimeFormatter.ofPattern("MMMM ''uu"))
    }.asLiveData()

    private val _remindersOnSelectedDate = MutableStateFlow(listOf<ReminderAndHistory>())

    /**
     * List of reminders for selected date
     */
    val remindersOnSelectedDate = _remindersOnSelectedDate.asLiveData()


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
     * Job for loading reminders for selected date
     */
    private var loadDateRemindersJob: Job? = null

    /**
     * Job for loading reminders for selected month
     */
    private var loadMonthRemindersJob: Job? = null

    init {
        loadRemindersForMonth()
    }

    /**
     * Changes the selected date, and starts a job to load the reminders for that date.
     */
    fun setSelectedDate(date: LocalDate) {
        if (date == _selectedDate.value) {
            return
        }
        _selectedDate.value = date
        loadRemindersForDate()
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
        setSelectedDate(LocalDate.now().withYear(yearMonth.year).withMonth(yearMonth.monthValue))
        _selectedYearMonth.value = yearMonth
        loadRemindersForMonth()
    }

    private fun loadRemindersForDate() {
        loadDateRemindersJob?.cancel()
        loadDateRemindersJob = viewModelScope.launch {
            reminderRepository.getAllWithTodayHistory().map { list ->
                // Only return reminders where the selected date is within its schedule,
                // so expired/future reminders aren't displayed
                list.filter { it.reminder.schedulingRules.containsDate(_selectedDate.value) }
            }.flowOn(dispatcher).collect {
                _remindersOnSelectedDate.emit(it)
            }
        }
    }

    private fun loadRemindersForMonth() {
        loadMonthRemindersJob?.cancel()
        loadMonthRemindersJob = viewModelScope.launch {
            val selectedYearMonth = _selectedYearMonth.value
            reminderRepository.getAllInMonth(
                selectedYearMonth.month.value.toString().padStart(2, '0')
            ).collect {
                _remindersInSelectedMonth.emit(it)
            }
        }
    }
}