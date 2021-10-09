package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.ReminderDataSource
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.util.toLocalDate
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.Month

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
            val today = LocalDate.now().dayOfWeek
            ReminderAndHistory(
                reminder = reminder.copy(),
                history = historyCache.filter {
                    it.reminderId == reminder.id && it.completedAt.toLocalDateTime().dayOfWeek == today
                }.maxByOrNull { it.completedAt }
            )
        }
    }

    override fun getAllInRangeWithHistoryOnDate(
        startDate: LocalDate,
        endDate: LocalDate,
        historyDate: LocalDate
    ): Flow<List<ReminderAndHistory>> = flow {
        emit(reminderCache)
    }.map { reminders ->
        reminders.filter { reminder ->
            reminder.schedulingRules.let {
                when (it.type) {
                    SchedulingType.SINGLE -> startDate <= it.startDate && it.startDate <= it.endDate
                    SchedulingType.REPEATING -> {
                        startDate <= it.endDate && endDate >= it.startDate
                    }
                    SchedulingType.REPEAT_FOREVER -> endDate >= it.startDate
                }
            }
        }.map { reminder ->
            ReminderAndHistory(
                reminder = reminder,
                history = historyCache.filter {
                    it.reminderId == reminder.id && it.completedAt.toLocalDate() == historyDate
                }.maxByOrNull { it.completedAt }
            )
        }
    }

    override fun getAllInMonth(month: String): Flow<List<Reminder>> = flow {
        emit(reminderCache)
    }.map { reminders ->
        val month = Month.of(month.toInt())
        reminders.filter {
            it.schedulingRules.startDate.month <= month
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

    override fun getAllWithAlarmsTimeAscending(): Flow<List<ReminderAndAlarm>> = flow {
        emit(reminderCache)
    }.map { reminders ->
        reminders.map { reminder ->
            ReminderAndAlarm(reminder, alarmCache.find { it.reminderId == reminder.id })
        }.sortedBy { it.alarm?.triggerAt }
    }

    override suspend fun getAllWithAlarms(): List<ReminderAndAlarm> = reminderCache.map {  reminder ->
            val alarm = alarmCache.firstOrNull { it.reminderId == reminder.id }
            ReminderAndAlarm(reminder, alarm)
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