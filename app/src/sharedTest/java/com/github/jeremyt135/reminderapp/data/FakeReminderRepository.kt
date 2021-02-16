package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.data.local.FakeReminderLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeReminderRepository @Inject constructor(
    private val cache: MutableList<Reminder>,
    alarmCache: MutableList<Alarm>,
    historyCache: MutableList<History>
) :
    ReminderRepository {
    private val fakeLocalDataSource = FakeReminderLocalDataSource(cache, alarmCache, historyCache)

    override fun getAll(): Flow<List<Reminder>> = fakeLocalDataSource.getAll()

    override fun getAllWithTodayHistory(): Flow<List<ReminderAndHistory>> = fakeLocalDataSource.getAllWithTodayHistory()

    override suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm? {
        return fakeLocalDataSource.getReminderAndAlarm(reminderId)
    }

    override suspend fun save(reminder: Reminder): Long {
        return fakeLocalDataSource.save(reminder)
    }

    override suspend fun findById(id: Long): Reminder? = cache.find { it.id == id }

    override suspend fun delete(reminder: Reminder) = fakeLocalDataSource.delete(reminder)
}