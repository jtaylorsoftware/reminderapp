package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.di.ReminderDatabaseSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton


interface ReminderRepository {
    fun getAll(): Flow<List<Reminder>>
    suspend fun save(reminder: Reminder): Long
    suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm?
    suspend fun findById(id: Long): Reminder?
}

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    @ReminderDatabaseSource private val reminderLocalDataSource: ReminderDataSource,
) : ReminderRepository {
    override fun getAll(): Flow<List<Reminder>> = reminderLocalDataSource.getAll()
    override suspend fun save(reminder: Reminder) = reminderLocalDataSource.save(reminder)
    override suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm? =
        reminderLocalDataSource.getReminderAndAlarm(reminderId)

    override suspend fun findById(id: Long): Reminder? = reminderLocalDataSource.findById(id)
}