package com.github.jeremyt135.reminderapp.screens.reminder.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    reminderRepository: ReminderRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    /**
     * Flag indicating the view is being displayed as a single column (false) or
     * multiple columns (true)
     */
    private val wideLayout = MutableStateFlow(false)

    @ExperimentalCoroutinesApi
    val remindersAndAlarms =
        reminderRepository.getAllWithAlarmsTimeAscending().flatMapLatest { alarms ->
            wideLayout.mapLatest {
                categorizeAlarms(alarms, wideLayout = it)
            }
        }.flowOn(dispatcher).asLiveData()

    /**
     * Changes whether the data list includes labels. Wide layouts do not include the labels
     */
    fun setWideLayout(wideLayout: Boolean){
        this.wideLayout.value = wideLayout
    }

    private suspend fun categorizeAlarms(
        list: List<ReminderAndAlarm>,
        wideLayout: Boolean
    ): List<Any> = coroutineScope {
        if (list.isEmpty()) {
            return@coroutineScope emptyList()
        }

        // Add title separators to list only if there's alarms
        val labeledAlarms = mutableListOf<Any>()

        // Label the first alarm as "next"
        if (!wideLayout) {
            labeledAlarms.add(AlarmListSeparator("Next"))
        }
        labeledAlarms.add(list[0])

        // Store other alarms in time categories
        val today: MutableList<Any> = mutableListOf()
        val thisWeek: MutableList<Any> = mutableListOf()
        val future: MutableList<Any> = mutableListOf()
        coroutineScope {
            launch {
                getTodayAlarms(list.subList(1, list.size)).toList(today)
            }
            launch {
                getThisWeekAlarms(list.subList(1, list.size)).toList(thisWeek)
            }
            launch {
                getFutureAlarms(list.subList(1, list.size)).toList(future)
            }
        }

        // Add ones today
        if (today.isNotEmpty()) {
            if (!wideLayout) {
                labeledAlarms.add(AlarmListSeparator("Today"))
            }
            labeledAlarms.addAll(today)
        }

        // Add ones within a week
        if (thisWeek.isNotEmpty()) {
            if (!wideLayout) {
                labeledAlarms.add(AlarmListSeparator("Within a week"))
            }
            labeledAlarms.addAll(thisWeek)
        }
        // Label ones further than a week into the future as
        if (future.isNotEmpty()) {
            if (!wideLayout) {
                labeledAlarms.add(AlarmListSeparator("Future"))
            }
            labeledAlarms.addAll(future)
        }

        labeledAlarms
    }

    private fun getTodayAlarms(list: List<ReminderAndAlarm>) = list.asFlow().filter {
        val daysUntil = it.daysUntil()
        daysUntil == 0L
    }

    private fun getThisWeekAlarms(list: List<ReminderAndAlarm>) =
        list.asFlow().filter {
            val daysUntil = it.daysUntil()
            daysUntil in (1L..7L)
        }

    private fun getFutureAlarms(list: List<ReminderAndAlarm>) =
        list.asFlow().filter {
            val daysUntil = it.daysUntil()
            daysUntil > 7L
        }

    private fun ReminderAndAlarm.daysUntil() = if (alarm == null) {
        Long.MAX_VALUE
    } else {
        LocalDate.now().until(alarm.triggerAt.toLocalDate(), ChronoUnit.DAYS)
    }
}