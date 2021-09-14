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
    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())

    /**
     * Currently selected year & month
     */
    val selectedYearMonth = _selectedYearMonth.asStateFlow()

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

    /**
     * List of boolean values where a value of true indicates there is at least one Reminder
     * on that day of the month.
     */
    val daysWithReminders =
        _selectedYearMonth.combine(_remindersInSelectedMonth) { yearMonth, reminders ->
            Timber.d("Reminders in month len: ${reminders.size}")
            (1..yearMonth.lengthOfMonth()).map { dayOfMonth ->
                val date = LocalDate.of(yearMonth.year, yearMonth.monthValue, dayOfMonth)
                reminders.any {
                    it.schedulingRules.containsDate(date)
                }
            }
        }.flowOn(dispatcher).stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            emptyList()
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
        loadRemindersForDate()
        loadRemindersForMonth()
    }

    fun setSelectedDate(date: LocalDate) {
        if (date == _selectedDate.value) {
            return
        }
        Timber.d("Set selected date to $date")
        _selectedDate.value = date
        loadRemindersForDate()
    }

    fun goToNextMonth() {
        setSelectedMonth(_selectedYearMonth.value.plusMonths(1))
    }

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
                list.filter { it.reminder.schedulingRules.containsDate(_selectedDate.value) }.also {
                    Timber.d("Loaded $it")
                }
            }.flowOn(dispatcher).collect {
                Timber.d("Loaded reminders for selected date")
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