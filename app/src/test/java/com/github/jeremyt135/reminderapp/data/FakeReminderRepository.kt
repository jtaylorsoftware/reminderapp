package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeReminderRepository(private val cache: MutableList<Reminder> = mutableListOf()) :
    ReminderRepository {
    override fun getAll(): Flow<List<Reminder>> = flow {
        emit(cache)
    }

    override suspend fun save(reminder: Reminder) {
        cache.add(reminder)
    }

    override suspend fun findById(id: Long): Reminder? = cache.find { it.id == id }
}