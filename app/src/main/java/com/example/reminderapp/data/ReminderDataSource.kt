package com.example.reminderapp.data

import com.example.reminderapp.data.domain.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderDataSource {
    fun getAll(): Flow<List<Reminder>>
    suspend fun findById(id: Int): Reminder?
    suspend fun save(reminder: Reminder)
}