package app.timetoremind.mobile.data

import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.ReminderAndAlarm
import app.timetoremind.mobile.data.domain.ReminderAndHistory
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ReminderDataSource {
    fun getAll(): Flow<List<Reminder>>
    fun getAllInMonth(month: String): Flow<List<Reminder>>
    fun getAllWithTodayHistory(): Flow<List<ReminderAndHistory>>
    fun getAllInRangeWithHistoryOnDate(
        startDate: LocalDate,
        endDate: LocalDate,
        historyDate: LocalDate
    ): Flow<List<ReminderAndHistory>>

    suspend fun findById(id: String): Reminder?
    suspend fun getReminderAndAlarm(reminderId: String): ReminderAndAlarm?
    fun getAllWithAlarmsTimeAscending(): Flow<List<ReminderAndAlarm>>
    suspend fun getAllWithAlarms(): List<ReminderAndAlarm>
    suspend fun save(reminder: Reminder)
    suspend fun saveAll(reminders: List<Reminder>)
    suspend fun delete(reminder: Reminder)
    suspend fun deleteAll()
}