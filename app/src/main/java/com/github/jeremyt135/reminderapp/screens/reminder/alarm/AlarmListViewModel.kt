package com.github.jeremyt135.reminderapp.screens.reminder.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    reminderRepository: ReminderRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    val remindersAndAlarms = reminderRepository.getAllWithAlarmsTimeAscending().map {
        categorizeAlarms(it)
    }.flowOn(dispatcher).asLiveData()

    private suspend fun categorizeAlarms(list: List<ReminderAndAlarm>): List<Any> = coroutineScope {
        if (list.isEmpty()) {
            return@coroutineScope emptyList()
        }

        // Add title separators to list only if there's alarms
        val labeledAlarms = mutableListOf<Any>()

        // Label the first alarm as "next"
        labeledAlarms.add(AlarmListSeparator("Next"))
        labeledAlarms.add(list[0])

        // Store other alarms in time categories
        var today: List<Any> = emptyList()
        var thisWeek: List<Any> = emptyList()
        var future: List<Any> = emptyList()
        coroutineScope {
            launch {
                today = getTodayAlarms(list)
            }
            launch {
                thisWeek = getThisWeekAlarms(list)
            }
            launch {
                future = getFutureAlarms(list)
            }
        }

        // Add ones today
        if (today.isNotEmpty()) {
            labeledAlarms.add(AlarmListSeparator("Today"))
            labeledAlarms.addAll(today)
        }

        // Add ones within a week
        if (thisWeek.isNotEmpty()) {
            labeledAlarms.add(AlarmListSeparator("Within a week"))
            labeledAlarms.addAll(thisWeek)
        }
        // Label ones further than a week into the future as
        if (future.isNotEmpty()) {
            labeledAlarms.add(AlarmListSeparator("Future"))
            labeledAlarms.addAll(future)
        }

        labeledAlarms
    }

    private fun getTodayAlarms(list: List<ReminderAndAlarm>): List<ReminderAndAlarm> = list.filter {
        val daysUntil = it.daysUntil()
        daysUntil == 0L
    }

    private fun getThisWeekAlarms(list: List<ReminderAndAlarm>): List<ReminderAndAlarm> =
        list.filter {
            val daysUntil = it.daysUntil()
            daysUntil in (1L..7L)
        }

    private fun getFutureAlarms(list: List<ReminderAndAlarm>): List<ReminderAndAlarm> =
        list.filter {
            val daysUntil = it.daysUntil()
            daysUntil > 7L
        }

    private fun ReminderAndAlarm.daysUntil() = if (alarm == null) {
        Long.MAX_VALUE
    } else {
        LocalDate.now().until(alarm.triggerAt.toLocalDate(), ChronoUnit.DAYS)
    }
}