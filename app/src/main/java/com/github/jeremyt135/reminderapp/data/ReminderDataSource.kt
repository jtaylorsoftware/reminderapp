package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderDataSource {
    fun getAll(): Flow<List<Reminder>>
    suspend fun findById(id: Long): Reminder?
    suspend fun save(reminder: Reminder)
}