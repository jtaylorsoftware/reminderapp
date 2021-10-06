package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.data.local.FakeReminderLocalDataSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeReminderRepository @Inject constructor(
    private val cache: MutableList<Reminder>,
    alarmCache: MutableList<Alarm>,
    historyCache: MutableList<History>
) :
    ReminderRepository {
    private val fakeLocalDataSource = FakeReminderLocalDataSource(cache, alarmCache, historyCache)

    override fun getAll(): Flow<List<Reminder>> = fakeLocalDataSource.getAll()

    override fun getAllWithTodayHistory(): Flow<List<ReminderAndHistory>> =
        fakeLocalDataSource.getAllWithTodayHistory()

    override fun getAllInRangeWithHistoryOnDate(
        startDate: LocalDate,
        endDate: LocalDate,
        historyDate: LocalDate
    ): Flow<List<ReminderAndHistory>> =
        fakeLocalDataSource.getAllInRangeWithHistoryOnDate(startDate, endDate, historyDate)

    override fun getAllInMonth(month: String): Flow<List<Reminder>> =
        fakeLocalDataSource.getAllInMonth(month)

    override suspend fun getReminderAndAlarm(reminderId: String): ReminderAndAlarm? {
        return fakeLocalDataSource.getReminderAndAlarm(reminderId)
    }

    override fun getAllWithAlarmsTimeAscending(): Flow<List<ReminderAndAlarm>> = fakeLocalDataSource.getAllWithAlarmsTimeAscending()
    override suspend fun getAllWithAlarms(): List<ReminderAndAlarm> = fakeLocalDataSource.getAllWithAlarms()

    override suspend fun save(reminder: Reminder) {
        fakeLocalDataSource.save(reminder)
    }

    override suspend fun saveAll(reminders: List<Reminder>) {
        fakeLocalDataSource.saveAll(reminders)
    }

    override suspend fun findById(id: String): Reminder? = cache.find { it.id == id }

    override suspend fun delete(reminder: Reminder) = fakeLocalDataSource.delete(reminder)
    override suspend fun deleteAll() {
        fakeLocalDataSource.deleteAll()
    }
}