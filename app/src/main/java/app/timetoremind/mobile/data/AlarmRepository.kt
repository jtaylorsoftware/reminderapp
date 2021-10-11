package app.timetoremind.mobile.data

import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.data.local.AlarmDao
import app.timetoremind.mobile.data.local.AlarmScheduleFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface AlarmRepository {
    fun getAll(): Flow<List<Alarm>>
    suspend fun findById(id: Long): Alarm?
    suspend fun findByReminderId(reminderId: String): Alarm?
    suspend fun getFirstScheduledAlarm(): Alarm?
    suspend fun getFirstByTimeAscending(): Alarm?
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
    override suspend fun findByReminderId(reminderId: String): Alarm? =
        alarmDao.findByReminderId(reminderId)?.asDomainModel()

    override suspend fun getFirstScheduledAlarm() =
        alarmDao.getFirstScheduledAlarm()?.asDomainModel()

    override suspend fun getFirstByTimeAscending() =
        alarmDao.getFirstByTimeAscending()?.asDomainModel()

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