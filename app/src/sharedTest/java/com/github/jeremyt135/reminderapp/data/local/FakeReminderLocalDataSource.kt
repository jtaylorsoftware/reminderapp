package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.ReminderDataSource
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeReminderLocalDataSource(private val cache: MutableList<Reminder> = mutableListOf()):
    ReminderDataSource {
    private var nextAlarmId = 1L

    override fun getAll(): Flow<List<Reminder>> = flow {
        emit(cache)
    }

    override suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm? {
        return findById(reminderId)?.let{
            val alarmId = nextAlarmId
            nextAlarmId += 1
            ReminderAndAlarm(
                it,
                Alarm(id = alarmId, reminderId = it.id, triggerAt = 0)
            )
        }
    }

    override suspend fun findById(id: Long): Reminder? = cache.find { it.id == id }

    override suspend fun save(reminder: Reminder): Long{
        val nextId = cache.size.toLong() + 1
        val savedReminder = reminder.copy(id = nextId)
        cache.add(savedReminder)
        return nextId
    }
}