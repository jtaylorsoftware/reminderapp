package com.example.reminderapp.data

import com.example.reminderapp.data.domain.Reminder
import com.example.reminderapp.di.ReminderDatabaseSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    @ReminderDatabaseSource private val reminderLocalDataSource: ReminderDataSource,
) {
    fun getAll(): Flow<List<Reminder>> = reminderLocalDataSource.getAll()
    suspend fun save(reminder: Reminder) = reminderLocalDataSource.save(reminder)
    suspend fun findById(id: Int): Reminder? = reminderLocalDataSource.findById(id)
}