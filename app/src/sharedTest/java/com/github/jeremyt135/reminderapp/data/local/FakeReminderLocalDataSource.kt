package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.ReminderDataSource
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class FakeReminderLocalDataSource(
    private val reminderCache: MutableList<Reminder> = mutableListOf(),
    private val alarmCache: List<Alarm> = listOf()
) : ReminderDataSource {

    override fun getAll(): Flow<List<Reminder>> = flow {
        emit(reminderCache)
    }

    override fun getAllWithTodayHistory(): Flow<List<ReminderAndHistory>> = flow {
        emit(reminderCache)
    }.map { reminders ->
        reminders.map {
            ReminderAndHistory(
                reminder = it.copy(),
                history = null,
            )
        }
    }

    override suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm? {
        return findById(reminderId)?.let {
            val alarm = alarmCache.firstOrNull { alarm -> alarm.reminderId == reminderId }
                ?: Alarm(reminderId = reminderId, triggerAt = 0)
            ReminderAndAlarm(
                it,
                alarm
            )
        }
    }

    override suspend fun findById(id: Long): Reminder? = reminderCache.find { it.id == id }

    override suspend fun save(reminder: Reminder): Long =
        when (val index = reminderCache.indexOf(reminder)) {
            -1 -> {
                reminderCache.add(reminder.copy(id = reminderCache.size + 1L))
                reminderCache.size.toLong()
            }
            else -> {
                reminderCache[index] = reminder
                reminder.id
            }
        }

    override suspend fun delete(reminder: Reminder) {
        reminderCache.remove(reminder)
    }
}