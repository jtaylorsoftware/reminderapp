package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.ReminderDataSource
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeReminderLocalDataSource(private val cache: MutableList<Reminder> = mutableListOf()): ReminderDataSource {

    override fun getAll(): Flow<List<Reminder>> = flow {
        emit(cache)
    }

    override suspend fun findById(id: Long): Reminder? = cache.find { it.id == id }

    override suspend fun save(reminder: Reminder) {
        cache.add(reminder)
    }
}