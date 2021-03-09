package com.github.jeremyt135.reminderapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.util.*
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random
import com.github.jeremyt135.reminderapp.data.local.AlarmEntity as Alarm

@ExperimentalCoroutinesApi
@HiltAndroidTest
@SmallTest
class AlarmDaoTest {
    private lateinit var alarmDao: AlarmDao

    private val reminders = (1..10).map { ReminderEntity() }
    val now = currentLocalDateTime()
    private val alarms = reminders.mapIndexed { i, reminder ->
        Alarm(reminderId = reminder.id, triggerAt = now.plusMinutes(i.toLong()))
    }

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

    @After
    fun teardown() = runBlocking {
        alarmDao.clearTable()
    }

    @Test
    fun getAll_returnsInsertedAlarms() = runBlockingTest {
        alarms.forEach { alarmDao.insert(it) }

        val dbAlarms = alarmDao.getAll().first()

        assertThat(dbAlarms).containsExactlyElementsIn(alarms.mapIndexed { i, it ->
            it.copy(id=i.toLong()+1)
        })
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
        assertThat(alarmDao.findByReminderId("")).isNull()
    }

    @Test
    fun clearTable_deletesAllAlarms() = runBlockingTest {
        alarmDao.insert(alarms[0])

        assertThat(alarmDao.findById(1)).isEqualTo(alarms[0].copy(id = 1))

        alarmDao.clearTable()

        assertThat(alarmDao.findById(1)).isNull()
    }

    @Test
    fun getFirstByTimeAscending_returnsFirstByTime() = runBlockingTest {
        val times = run {
            val temp = mutableSetOf<LocalDateTime>()
            while (temp.size < 10) temp.add(
                Random.nextLong(0, currentEpochMilli()).toLocalDateTime()
            )
            temp.toList()
        }
        val alarms = (1..10).map { i ->
            Alarm(
                id = i.toLong(),
                reminderId = reminders[i - 1].id,
                triggerAt = times[i - 1]
            ).also {
                alarmDao.insert(it)
            }
        }

        assertThat(alarmDao.getFirstByTimeAscending()).isEqualTo(
            alarms.minByOrNull { it.triggerAt }
        )
    }

    @Test
    fun getFirstScheduledAlarm_noneScheduled_returnsNull() = runBlockingTest {
        val now = currentLocalDateTime()
        val alarms = (1..3L).map { i ->
            Alarm(
                id = i,
                reminderId = reminders[i.toInt() - 1].id,
                triggerAt = now.plusMinutes(i)
            ).also {
                alarmDao.insert(it)
            }
        }

        assertThat(alarmDao.getFirstScheduledAlarm()).isNull()
    }

    @Test
    fun getFirstScheduledAlarm_multipleScheduled_returnsFirst() = runBlockingTest {
        val now = currentLocalDateTime()
        val alarms = (1..6L).map { i ->
            Alarm(
                id = i,
                reminderId = reminders[i.toInt()-1].id,
                triggerAt = now.plusMinutes(i),
                scheduled = if (i % 2 == 0L) true else null
            ).also {
                alarmDao.insert(it)
            }
        }

        assertThat(alarmDao.getFirstScheduledAlarm()).isEqualTo(alarms[1])
    }

    @Test
    fun deleteAllInPast_deletesOnlyAlarmsBeforeTime() = runBlockingTest {
        val now = currentLocalDateTime()

        val alarms = (1..5).map { i ->
            Alarm(
                id = i.toLong(),
                reminderId = reminders[i - 1].id,
                triggerAt = now.plusMinutes(i.toLong()),
                scheduled = if (i % 2 == 0) true else null
            ).also {
                alarmDao.insert(it)
            }
        }

        alarmDao.deleteAllInPast(now.plusMinutes(3).toEpochMilli())

        assertThat(alarmDao.getAll().first()).containsExactlyElementsIn(
            alarms.filter { it.triggerAt >= now.plusMinutes(3) }
        )
    }

    @Test
    fun clearScheduled_setsAllScheduledNull() = runBlockingTest {
        val now = currentLocalDateTime()
        (1..5L).map { i ->
            Alarm(
                id = i,
                reminderId = reminders[i.toInt() - 1].id,
                triggerAt = now.plusMinutes(i),
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
        val alarm = Alarm(id = 1, reminderId = reminders[0].id, triggerAt = currentLocalDateTime())
        alarmDao.insert(alarm)

        alarmDao.setScheduled(AlarmScheduleFlag(alarm.id, true))

        assertThat(alarmDao.findById(alarm.id)!!.scheduled).isTrue()

        alarmDao.setScheduled(AlarmScheduleFlag(alarm.id, null))

        assertThat(alarmDao.findById(alarm.id)!!.scheduled).isNull()
    }
}