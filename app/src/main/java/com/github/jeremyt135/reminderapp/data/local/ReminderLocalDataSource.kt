package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.ReminderDataSource
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.data.domain.asEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderLocalDataSource @Inject constructor(private val reminderDao: ReminderDao) :
    ReminderDataSource {

    override fun getAll(): Flow<List<Reminder>> = reminderDao.getAll().map {
        it.asDomainModel()
    }

    override suspend fun findById(id: Long): Reminder? = reminderDao.findById(id)?.asDomainModel()

    override suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm? =
        reminderDao.getReminderAndAlarm(reminderId)?.asDomainModel()

    override suspend fun save(reminder: Reminder) = reminderDao.insert(reminder.asEntity())
}