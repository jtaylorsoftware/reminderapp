package com.github.jeremyt135.reminderapp.screens.reminder.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    reminderRepository: ReminderRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    val remindersAndAlarms = reminderRepository.getAllWithAlarmsTimeAscending().map { list ->
        if (list.isNotEmpty()) {
            // Add title separators to list only if there's alarms

            val labeledAlarms = mutableListOf<Any>()

            // Label the first alarm as "next"
            labeledAlarms.add(AlarmListSeparator("Next"))
            labeledAlarms.add(list[0])

            // Label ones for today under "today"
            labeledAlarms.add(AlarmListSeparator("Today"))
            val thisWeek = mutableListOf<Any>()
            val future = mutableListOf<Any>()
            (1 until list.size).forEach { i ->
                val alarm = list[i].alarm
                if (alarm != null) {
                    val daysUntil =
                        abs(ChronoUnit.DAYS.between(alarm.triggerAt, currentLocalDateTime()))
                    when {
                        daysUntil <= 1 -> {
                            labeledAlarms.add(list[i])
                        }
                        daysUntil <= 7 -> {
                            thisWeek.add(list[i])
                        }
                        else -> {
                            future.add(list[i])
                        }
                    }
                }
            }
            // Add ones within a week
            if (thisWeek.size > 0) {
                labeledAlarms.add(AlarmListSeparator("Within a week"))
                labeledAlarms.addAll(thisWeek)
                thisWeek.clear()
            }
            // Label ones further than a week into the future as
            if (future.size > 0) {
                labeledAlarms.add(AlarmListSeparator("Future"))
                labeledAlarms.addAll(future)
                future.clear()
            }

            labeledAlarms
        } else emptyList()
    }.flowOn(dispatcher).asLiveData()
}