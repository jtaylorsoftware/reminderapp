package com.github.jeremyt135.reminderapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SmallTest
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
import com.github.jeremyt135.reminderapp.data.local.AlarmEntity as Alarm

@ExperimentalCoroutinesApi
@HiltAndroidTest
@SmallTest
class AlarmDaoTest {
    private lateinit var alarmDao: AlarmDao

    private val reminders = listOf(
        ReminderEntity(id = 1), ReminderEntity(id = 2)
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

        assertThat(alarmDao.findById(1)).isEqualTo(alarms[0].copy(id = 1))

        alarmDao.clearTable()

        assertThat(alarmDao.findById(1)).isNull()
    }

    @Test
    fun getFirstStartingFrom_timeIs0_returnsLowestTime() = runBlockingTest {
        val alarms = (1..3).map { i ->
            Alarm(id = i.toLong(), reminderId = 1, triggerAt = 1000L * i).also {
                alarmDao.insert(it)
            }
        }

        assertThat(alarmDao.getFirstStartingFrom(0)).isEqualTo(alarms[0])
    }

    @Test
    fun getFirstStartingFrom_farInFuture_returnsNull() = runBlockingTest {
        (1..3).map { i ->
            Alarm(reminderId = 1, triggerAt = 1000L * i).also {
                alarmDao.insert(it)
            }
        }

        assertThat(alarmDao.getFirstStartingFrom(100000)).isNull()
    }

    @Test
    fun getFirstStartingFrom_idAndTimeIs0_returnsLowestTime() = runBlockingTest {
        val alarms = (1..3).map { i ->
            Alarm(id = i.toLong(), reminderId = 1, triggerAt = 1000L * i).also {
                alarmDao.insert(it)
            }
        }

        assertThat(alarmDao.getFirstStartingFrom(0, 0)).isEqualTo(alarms[0])
    }

    @Test
    fun getFirstStartingFrom_lastId_returnsNull() = runBlockingTest {
        val alarms = (1..3).map { i ->
            Alarm(reminderId = 1, triggerAt = 1000L * i).also {
                alarmDao.insert(it)
            }
        }

        assertThat(alarmDao.getFirstStartingFrom(0, 4)).isNull()
    }

    @Test
    fun getFirstScheduledAlarm_noneScheduled_returnsNull() = runBlockingTest {
        val alarms = (1..3).map { i ->
            Alarm(reminderId = 1, triggerAt = 1000L * i).also {
                alarmDao.insert(it)
            }
        }

        assertThat(alarmDao.getFirstScheduledAlarm()).isNull()
    }

    @Test
    fun getFirstScheduledAlarm_multipleScheduled_returnsFirst() = runBlockingTest {
        val alarms = (1..6).map { i ->
            Alarm(
                id = i.toLong(),
                reminderId = 1,
                triggerAt = 1000L * i,
                scheduled = if (i % 2 == 0) true else null
            ).also {
                alarmDao.insert(it)
            }
        }

        assertThat(alarmDao.getFirstScheduledAlarm()).isEqualTo(alarms[1])
    }

    @Test
    fun getAllScheduledAlarms_returnsOnlyScheduledAlarms() = runBlockingTest {
        val alarms = (1..6).map { i ->
            Alarm(
                id = i.toLong(),
                reminderId = 1,
                triggerAt = 1000L * i,
                scheduled = if (i % 2 == 0) true else null
            ).also {
                alarmDao.insert(it)
            }
        }

        assertThat(alarmDao.getAllScheduledAlarms().first()).containsExactlyElementsIn(
            alarms.filter { it.scheduled == true }
        )
    }

    @Test
    fun deleteAllInPast_deletesOnlyAlarmsBeforeTime() = runBlockingTest {
        val alarms = (1..5).map { i ->
            Alarm(
                id = i.toLong(),
                reminderId = 1,
                triggerAt = 1000L * i,
                scheduled = if (i % 2 == 0) true else null
            ).also {
                alarmDao.insert(it)
            }
        }

        alarmDao.deleteAllInPast(3000)

        assertThat(alarmDao.getAll().first()).containsExactlyElementsIn(
            alarms.filter { it.triggerAt >= 3000 }
        )
    }

    @Test
    fun clearScheduled_setsAllScheduledNull() = runBlockingTest {
         (1..5).map { i ->
            Alarm(
                id = i.toLong(),
                reminderId = 1,
                triggerAt = 1000L * i,
                scheduled = true
            ).also {
                alarmDao.insert(it)
            }
        }

        alarmDao.clearScheduled()

        assertThat(alarmDao.getAll().first().all { it.scheduled == null }).isTrue()
    }

    @Test
    fun setScheduled_changesValue() = runBlockingTest {
        val alarm = Alarm(id=1,reminderId = 1, triggerAt = 0, scheduled = null)
        alarmDao.insert(alarm)

        alarmDao.setScheduled(AlarmScheduleFlag(alarm.id, true))

        assertThat(alarmDao.findById(alarm.id)!!.scheduled).isTrue()

        alarmDao.setScheduled(AlarmScheduleFlag(alarm.id, null))

        assertThat(alarmDao.findById(alarm.id)!!.scheduled).isNull()
    }
}