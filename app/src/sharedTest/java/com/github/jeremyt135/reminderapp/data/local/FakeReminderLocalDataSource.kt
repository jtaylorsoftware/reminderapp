package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.ReminderDataSource
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeReminderLocalDataSource(
    private val reminderCache: MutableList<Reminder> = mutableListOf(),
    private val alarmCache: MutableList<Alarm> = mutableListOf(),
    private val historyCache: List<History> = listOf()
) : ReminderDataSource {

    override fun getAll(): Flow<List<Reminder>> = flow {
        emit(reminderCache)
    }

    override fun getAllWithTodayHistory(): Flow<List<ReminderAndHistory>> = flow {
        emit(reminderCache)
    }.map { reminders ->
        reminders.map { reminder ->
            ReminderAndHistory(
                reminder = reminder.copy(),
                history = historyCache.firstOrNull { history ->
                    history.reminderId == reminder.id &&
                            history.id == historyCache.maxOfOrNull { it.id } ?: 0L
                    history.completedAt.toLocalDateTime().dayOfWeek == LocalDate.now().dayOfWeek
                },
            )
        }
    }

    override suspend fun getReminderAndAlarm(reminderId: String): ReminderAndAlarm? {
        return findById(reminderId)?.let {
            val alarm = alarmCache.firstOrNull { alarm -> alarm.reminderId == reminderId }
            ReminderAndAlarm(
                it,
                alarm
            )
        }
    }

    override suspend fun findById(id: String): Reminder? = reminderCache.find { it.id == id }

    override suspend fun save(reminder: Reminder) {
        when (val index = reminderCache.indexOfFirst { it.id == reminder.id }) {
            -1 -> {
                reminderCache.add(reminder.copy())
            }
            else -> {
                reminderCache[index] = reminder
            }
        }
    }

    override suspend fun saveAll(reminders: List<Reminder>) {
        reminders.forEach { save(it) }
    }

    override suspend fun delete(reminder: Reminder) {
        reminderCache.remove(reminder)
        alarmCache.removeIf { it.reminderId == reminder.id } // emulate cascading deletion of alarms when reminder deleted
    }

    override suspend fun deleteAll() {
        reminderCache.clear()
        alarmCache.clear() // emulate cascading deletion of alarms when reminder deleted
    }
}