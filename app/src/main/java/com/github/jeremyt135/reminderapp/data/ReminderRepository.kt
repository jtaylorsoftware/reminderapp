package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import com.github.jeremyt135.reminderapp.di.ReminderDatabaseSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton


interface ReminderRepository {
    fun getAll(): Flow<List<Reminder>>
    fun getAllWithTodayHistory(): Flow<List<ReminderAndHistory>>
    suspend fun save(reminder: Reminder)
    suspend fun saveAll(reminders: List<Reminder>)
    suspend fun getReminderAndAlarm(reminderId: String): ReminderAndAlarm?
    suspend fun findById(id: String): Reminder?
    suspend fun delete(reminder: Reminder)
}

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    @ReminderDatabaseSource private val reminderLocalDataSource: ReminderDataSource,
) : ReminderRepository {
    override fun getAll(): Flow<List<Reminder>> = reminderLocalDataSource.getAll()
    override fun getAllWithTodayHistory(): Flow<List<ReminderAndHistory>> =
        reminderLocalDataSource.getAllWithTodayHistory()

    override suspend fun save(reminder: Reminder) {
        reminderLocalDataSource.save(reminder)
    }

    override suspend fun saveAll(reminders: List<Reminder>) {
        reminderLocalDataSource.saveAll(reminders)
    }

    override suspend fun getReminderAndAlarm(reminderId: String): ReminderAndAlarm? =
        reminderLocalDataSource.getReminderAndAlarm(reminderId)

    override suspend fun findById(id: String): Reminder? = reminderLocalDataSource.findById(id)
    override suspend fun delete(reminder: Reminder) = reminderLocalDataSource.delete(reminder)
}