package com.github.jeremyt135.reminderapp.screens.reminder.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class AlarmListViewModel @Inject constructor(reminderRepository: ReminderRepository) : ViewModel() {
    val remindersAndAlarms = reminderRepository.getAllWithAlarmsTimeAscending().map { list ->
        if (list.isNotEmpty()) {
            // Add title separators to list only if there's alarms
            val newList =
                listOf(AlarmListSeparator("Next")) + list[0] + listOf(AlarmListSeparator("Upcoming")) + list.slice(
                    1 until list.size
                )
            newList
        } else emptyList()
    }.asLiveData()
}