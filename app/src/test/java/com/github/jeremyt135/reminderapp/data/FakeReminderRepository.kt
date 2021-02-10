package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.data.local.FakeReminderLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeReminderRepository(private val cache: MutableList<Reminder> = mutableListOf()) :
    ReminderRepository {
    private val fakeLocalDataSource = FakeReminderLocalDataSource(cache)

    override fun getAll(): Flow<List<Reminder>> = flow {
        emit(cache)
    }

    override suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm? {
        return fakeLocalDataSource.getReminderAndAlarm(reminderId)
    }

    override suspend fun save(reminder: Reminder): Long {
        return fakeLocalDataSource.save(reminder)
    }

    override suspend fun findById(id: Long): Reminder? = cache.find { it.id == id }
}