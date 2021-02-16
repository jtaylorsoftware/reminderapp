package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import kotlinx.coroutines.flow.Flow

interface ReminderDataSource {
    fun getAll(): Flow<List<Reminder>>
    fun getAllWithTodayHistory(): Flow<List<ReminderAndHistory>>
    suspend fun findById(id: Long): Reminder?
    suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm?
    suspend fun save(reminder: Reminder): Long
    suspend fun delete(reminder: Reminder)
}