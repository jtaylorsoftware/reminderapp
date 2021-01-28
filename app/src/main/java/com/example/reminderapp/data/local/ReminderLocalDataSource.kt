package com.example.reminderapp.data.local

import com.example.reminderapp.data.ReminderDataSource
import com.example.reminderapp.data.domain.Reminder
import com.example.reminderapp.data.domain.asEntity
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

    override suspend fun findById(id: Int): Reminder? = reminderDao.findById(id)?.asDomainModel()
    override suspend fun save(reminder: Reminder) = reminderDao.insert(reminder.asEntity())
}