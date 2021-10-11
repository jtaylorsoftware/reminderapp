package app.timetoremind.mobile.data.local

import app.timetoremind.mobile.data.ReminderDataSource
import app.timetoremind.mobile.data.asDataModel
import app.timetoremind.mobile.data.asDomainModel
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.ReminderAndAlarm
import app.timetoremind.mobile.data.domain.ReminderAndHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderLocalDataSource @Inject constructor(private val reminderDao: ReminderDao) :
    ReminderDataSource {

    override fun getAll(): Flow<List<Reminder>> = reminderDao.getAll().map {
        it.asDomainModel()
    }

    override fun getAllInMonth(month: String): Flow<List<Reminder>> =
        reminderDao.getAllInMonth(month).map {
            it.asDomainModel()
        }

    override fun getAllWithTodayHistory(): Flow<List<ReminderAndHistory>> =
        reminderDao.getAllWithTodayHistory().map {
            it.asDomainModel()
        }

    override fun getAllInRangeWithHistoryOnDate(
        startDate: LocalDate,
        endDate: LocalDate,
        historyDate: LocalDate
    ): Flow<List<ReminderAndHistory>> =
        reminderDao.getAllInRangeWithHistoryOnDate(
            startDate.toString(),
            endDate.toString(),
            historyDate.toString()
        )
            .map { it.asDomainModel() }

    override suspend fun findById(id: String): Reminder? = reminderDao.findById(id)?.asDomainModel()

    override suspend fun getReminderAndAlarm(reminderId: String): ReminderAndAlarm? =
        reminderDao.getReminderAndAlarm(reminderId)?.asDomainModel()

    override fun getAllWithAlarmsTimeAscending(): Flow<List<ReminderAndAlarm>> =
        reminderDao.getAllWithAlarmsTimeAscending().map {
            it.asDomainModel()
        }

    override suspend fun getAllWithAlarms(): List<ReminderAndAlarm> =
        reminderDao.getAllWithAlarms().map { it.asDomainModel() }

    override suspend fun save(reminder: Reminder) = reminderDao.insert(reminder.asDataModel())
    override suspend fun saveAll(reminders: List<Reminder>) =
        reminderDao.insertAll(reminders.asDataModel())

    override suspend fun delete(reminder: Reminder) = reminderDao.delete(reminder.asDataModel())
    override suspend fun deleteAll() {
        reminderDao.clearTable()
    }
}