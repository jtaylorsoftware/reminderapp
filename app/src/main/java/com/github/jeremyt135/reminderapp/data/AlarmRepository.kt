package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.local.AlarmDao
import com.github.jeremyt135.reminderapp.data.local.AlarmScheduleFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface AlarmRepository {
    fun getAll(): Flow<List<Alarm>>
    suspend fun findById(id: Long): Alarm?
    suspend fun findByReminderId(reminderId: Long): Alarm?
    suspend fun getFirstStartingFrom(timeNow: Long): Alarm?
    suspend fun getFirstStartingFrom(timeNow: Long, id: Long): Alarm?
    suspend fun getFirstScheduledAlarm(): Alarm?
    fun getAllScheduledAlarms(): Flow<List<Alarm>>
    suspend fun setScheduled(alarm: Alarm)
    suspend fun clearScheduled(alarm: Alarm)
    suspend fun clearAllScheduled()
    suspend fun save(alarm: Alarm): Long
    suspend fun delete(alarm: Alarm)
    suspend fun deleteAllInPast(timeNow: Long)
    suspend fun deleteAll()
}

@Singleton
class AlarmRepositoryImpl @Inject constructor(
    private val alarmDao: AlarmDao
) : AlarmRepository {
    override fun getAll(): Flow<List<Alarm>> = alarmDao.getAll().map { it.asDomainModel() }

    override suspend fun findById(id: Long): Alarm? = alarmDao.findById(id)?.asDomainModel()
    override suspend fun findByReminderId(reminderId: Long): Alarm? =
        alarmDao.findByReminderId(reminderId)?.asDomainModel()

    override suspend fun getFirstStartingFrom(timeNow: Long) =
        alarmDao.getFirstStartingFrom(timeNow)?.asDomainModel()

    override suspend fun getFirstStartingFrom(timeNow: Long, id: Long) =
        alarmDao.getFirstStartingFrom(timeNow, id)?.asDomainModel()

    override suspend fun getFirstScheduledAlarm() =
        alarmDao.getFirstScheduledAlarm()?.asDomainModel()

    override fun getAllScheduledAlarms(): Flow<List<Alarm>> =
        alarmDao.getAllScheduledAlarms().map { it.asDomainModel() }

    override suspend fun setScheduled(alarm: Alarm) {
        alarmDao.setScheduled(AlarmScheduleFlag(alarm.id, true))
    }

    override suspend fun clearScheduled(alarm: Alarm) {
        alarmDao.setScheduled(AlarmScheduleFlag(alarm.id, null))
    }

    override suspend fun clearAllScheduled() = alarmDao.clearScheduled()

    override suspend fun save(alarm: Alarm): Long = alarmDao.insert(alarm.asDataModel())

    override suspend fun delete(alarm: Alarm) = alarmDao.delete(alarm.asDataModel())

    override suspend fun deleteAllInPast(timeNow: Long) = alarmDao.deleteAllInPast(timeNow)

    override suspend fun deleteAll() = alarmDao.clearTable()
}