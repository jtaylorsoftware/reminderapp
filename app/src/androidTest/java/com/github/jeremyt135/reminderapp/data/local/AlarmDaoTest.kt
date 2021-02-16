package com.github.jeremyt135.reminderapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.data.local.AlarmEntity as Alarm
import com.github.jeremyt135.reminderapp.util.InMemoryDatabaseRule
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
@HiltAndroidTest
@SmallTest
class AlarmDaoTest {
    private lateinit var alarmDao: AlarmDao

    private val reminders = listOf(
        ReminderEntity(id=1), ReminderEntity(id=2)
    )

    private val alarms = listOf(
        Alarm(0, 1, 1),
        Alarm(0, 2, 2),
    )

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var inMemoryDatabaseRule = InMemoryDatabaseRule()

    @Before
    fun setup() = runBlocking {
        alarmDao = inMemoryDatabaseRule.db.alarmDao()
        val reminderDao = inMemoryDatabaseRule.db.reminderDao()
        reminders.forEach {
            reminderDao.insert(it)
        }
    }

    @Test
    fun getAll_returnsInsertedAlarms() = runBlockingTest {
        val alarms = alarms.map {
            it.copy(id = alarmDao.insert(it))
        }

        val dbAlarms = alarmDao.getAll().first()

        assertThat(dbAlarms).containsExactlyElementsIn(alarms)
    }

    @Test
    fun findById_returnsInsertedAlarm() = runBlockingTest {
        val id = alarmDao.insert(alarms[0])

        val dbAlarm = alarmDao.findById(id)

        assertThat(dbAlarm).isEqualTo(alarms[0].copy(id = id))
    }

    @Test
    fun findByReminderId_returnsAlarmWithReminderId() = runBlockingTest {
        val id = alarmDao.insert(alarms[0])

        val dbAlarm = alarmDao.findByReminderId(alarms[0].reminderId)

        assertThat(dbAlarm).isEqualTo(alarms[0].copy(id = id))
    }


    @Test
    fun findById_emptyDb_returnsNull() = runBlockingTest {
        assertThat(alarmDao.findById(1)).isNull()
    }

    @Test
    fun findByReminderId_emptyDb_returnsNull() = runBlockingTest {
        assertThat(alarmDao.findByReminderId(1)).isNull()
    }

    @Test
    fun clearTable_deletesAllAlarms() = runBlockingTest {
        alarmDao.insert(alarms[0])

        assertThat(alarmDao.findById(1)).isEqualTo(alarms[0].copy(id=1))

        alarmDao.clearTable()

        assertThat(alarmDao.findById(1)).isNull()
    }
}