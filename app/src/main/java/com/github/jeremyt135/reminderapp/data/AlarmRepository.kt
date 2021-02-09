package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.data.local.AlarmDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface AlarmRepository {
    fun getAll(): Flow<List<Alarm>>
    suspend fun findById(id: Long): Alarm?
    suspend fun findByReminderId(reminderId: Long): Alarm?
    suspend fun save(alarm: Alarm): Long
    suspend fun delete(alarm: Alarm)
    suspend fun deleteAll()
}

@Singleton
class AlarmRepositoryImpl @Inject constructor(
    private val alarmDao: AlarmDao
) : AlarmRepository {
    override fun getAll(): Flow<List<Alarm>> = alarmDao.getAll()

    override suspend fun findById(id: Long): Alarm? = alarmDao.findById(id)
    override suspend fun findByReminderId(reminderId: Long): Alarm? =
        alarmDao.findByReminderId(reminderId)

    override suspend fun save(alarm: Alarm): Long = alarmDao.insert(alarm)

    override suspend fun delete(alarm: Alarm) = alarmDao.delete(alarm)

    override suspend fun deleteAll() = alarmDao.clearTable()
}