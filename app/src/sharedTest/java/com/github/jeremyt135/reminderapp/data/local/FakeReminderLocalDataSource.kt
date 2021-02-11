package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.ReminderDataSource
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeReminderLocalDataSource(
    private val reminderCache: MutableList<Reminder> = mutableListOf(),
    private val alarmCache: List<Alarm> = listOf()
) : ReminderDataSource {

    override fun getAll(): Flow<List<Reminder>> = flow {
        emit(reminderCache)
    }

    override suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm? {
        return findById(reminderId)?.let {
            val alarm = alarmCache.first { it.reminderId == reminderId }
            ReminderAndAlarm(
                it,
                alarm
            )
        }
    }

    override suspend fun findById(id: Long): Reminder? = reminderCache.find { it.id == id }

    override suspend fun save(reminder: Reminder): Long {
        val nextId = reminderCache.size.toLong() + 1
        val savedReminder = reminder.copy(id = nextId)
        reminderCache.add(savedReminder)
        return nextId
    }
}