package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import kotlinx.coroutines.flow.Flow

interface ReminderDataSource {
    fun getAll(): Flow<List<Reminder>>
    fun getAllWithTodayHistory(): Flow<List<ReminderAndHistory>>
    suspend fun findById(id: String): Reminder?
    suspend fun getReminderAndAlarm(reminderId: String): ReminderAndAlarm?
    suspend fun save(reminder: Reminder)
    suspend fun saveAll(reminders: List<Reminder>)
    suspend fun delete(reminder: Reminder)
}