package com.github.jeremyt135.reminderapp.service

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import androidx.test.rule.ServiceTestRule
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.di.*
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.google.common.truth.Truth.assertThat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@ExperimentalCoroutinesApi
@UninstallModules(DatabaseModule::class, RepositoryModule::class, DispatcherModule::class)
@HiltAndroidTest
@MediumTest
class ReminderAlarmManagerTest {
    private val testCoroutineDispatcher = TestCoroutineDispatcher()

    @InstallIn(SingletonComponent::class)
    @Module
    inner class TestModule {
        private val reminderCache: MutableList<Reminder> = mutableListOf()
        private val alarmCache: MutableList<Alarm> = mutableListOf()
        private val historyCache: MutableList<History> = mutableListOf()

        @Provides
        @Singleton
        fun provideReminderRepository(): ReminderRepository =
            FakeReminderRepository(reminderCache, alarmCache, historyCache)


        @Provides
        @Singleton
        fun provideAlarmRepository(): AlarmRepository = FakeAlarmRepository(alarmCache)

        @Provides
        @Singleton
        fun provideHistoryRepository(): HistoryRepository = FakeHistoryRepository(
            FakeHistoryLocalDataSource(historyCache)
        )

        @IoDispatcher
        @Provides
        @Singleton
        fun provideIoDispatcher(): CoroutineDispatcher = testCoroutineDispatcher

        @DefaultDispatcher
        @Provides
        @Singleton
        fun provideDefaultDispatcher(): CoroutineDispatcher = testCoroutineDispatcher

        @MainDispatcher
        @Provides
        @Singleton
        fun provideMainDispatcher(): CoroutineDispatcher = testCoroutineDispatcher

        @AppCoroutineScope
        @Provides
        @Singleton
        fun provideAppCoroutineScope(): CoroutineScope = CoroutineScope(testCoroutineDispatcher)
    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    private lateinit var repeatingReminder: Reminder
    private lateinit var oneOffReminder: Reminder

    @Before
    fun setup() {
        hiltRule.inject()

        val time = LocalTime.now()

        repeatingReminder = Reminder(
            description = "My Reminder",
            daysOfWeek = sortedSetOf(*DayOfWeek.values()),
            time = time
        )
        oneOffReminder = Reminder(
            description = "One-off Reminder",
            daysOfWeek = sortedSetOf(),
            time = time
        )

        runBlocking {
            repeatingReminder.id = reminderRepository.save(repeatingReminder)
            oneOffReminder.id = reminderRepository.save(oneOffReminder)
            val today = LocalDate.now().dayOfWeek
            alarmRepository.save(
                Alarm(
                    id = 0,
                    reminderId = repeatingReminder.id,
                    triggerAt = repeatingReminder.getNextTimeForAlarm(today)!!.toEpochMilli()
                )
            )
            alarmRepository.save(
                Alarm(
                    id = 0,
                    reminderId = oneOffReminder.id,
                    triggerAt = oneOffReminder.getNextTimeForAlarm(today)!!.plusMinutes(5)
                        .toEpochMilli()
                )
            )
        }
        mockkStatic(::showToast)
        every { showToast(any(), any(), any()) } returns Unit
    }

    @After
    fun teardown() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        alarmRepository.getFirstScheduledAlarm()?.let { alarm ->
            reminderRepository.findById(alarm.id)?.let { reminder ->
                cancelReminder(context, alarm, reminder)
            }
        }
        NotificationManagerCompat.from(context).cancelAll()
        alarmRepository.clearAllScheduled()
        alarmRepository.deleteAllInPast(LocalDateTime.now().toEpochMilli())
    }

    @Test
    fun start_actionStart_createsNotification() {
        val context: Context = ApplicationProvider.getApplicationContext()
        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                repeatingReminder.id
            )
        )

        assertThat(getCreatedIntentForAlarmActivity(context, repeatingReminder.id)).isNotNull()
    }

    @Test
    fun onBind_returnsLocalBinder() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent =
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                repeatingReminder.id
            )
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent)
        assertThat(binder).isInstanceOf(ReminderAlarmManager.LocalBinder::class.java)
    }

    @Test
    fun actionSnooze_snoozesAlarm() = testCoroutineDispatcher.runBlockingTest {
        val context: Context = ApplicationProvider.getApplicationContext()

        val previousAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!

        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                repeatingReminder.id
            )
        )

        mockkStatic(::scheduleNextAlarm, ::showToast, ::triggerNotification)
        coJustRun { scheduleNextAlarm(any(), alarmRepository, reminderRepository, any()) }
        every { showToast(any(), any(), any()) } returns Unit
        every { triggerNotification(any(), any(), any()) } returns Unit

        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SNOOZE, repeatingReminder.id)
        )

        val nextAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!
        assertThat(nextAlarm.id).isNotEqualTo(previousAlarm.id)
        assertThat(nextAlarm.reminderId).isEqualTo(previousAlarm.reminderId)
        assertThat(alarmRepository.findById(previousAlarm.id)).isNull()

        coVerify {
            scheduleNextAlarm(any(), alarmRepository, reminderRepository, any())
            showToast(any(), any())
            triggerSnoozeNotification(
                any(),
                repeatingReminder,
                nextAlarm
            )
        }
    }

    @Test
    fun actionSkip_savesHistory_andReschedules() = testCoroutineDispatcher.runBlockingTest {
        val context: Context = ApplicationProvider.getApplicationContext()

        val previousAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!

        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                repeatingReminder.id
            )
        )

        mockkStatic(::scheduleNextAlarm)
        coJustRun { scheduleNextAlarm(any(), alarmRepository, reminderRepository) }

        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SKIP, repeatingReminder.id)
        )

        val nextAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!
        assertThat(nextAlarm.id).isNotEqualTo(previousAlarm.id)
        assertThat(nextAlarm.reminderId).isEqualTo(previousAlarm.reminderId)
        assertThat(alarmRepository.findById(previousAlarm.id)).isNull()

        val history = historyRepository.getAll().first().find {
            it.reminderId == repeatingReminder.id && it.completionStatus == ReminderStatus.SKIPPED
        }
        assertThat(history).isNotNull()
    }

    @Test
    fun actionComplete_savesHistory_andReschedules() = testCoroutineDispatcher.runBlockingTest {
        val context: Context = ApplicationProvider.getApplicationContext()

        val previousAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!

        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                repeatingReminder.id
            )
        )

        mockkStatic(::scheduleNextAlarm)
        coJustRun { scheduleNextAlarm(any(), alarmRepository, reminderRepository) }

        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_COMPLETE,
                repeatingReminder.id
            )
        )

        val nextAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!
        assertThat(nextAlarm.id).isNotEqualTo(previousAlarm.id)
        assertThat(nextAlarm.reminderId).isEqualTo(previousAlarm.reminderId)
        assertThat(alarmRepository.findById(previousAlarm.id)).isNull()

        val history = historyRepository.getAll().first().find {
            it.reminderId == repeatingReminder.id && it.completionStatus == ReminderStatus.COMPLETED
        }
        assertThat(history).isNotNull()
    }

    @Test
    fun localBinder_snooze_snoozesAlarm() = testCoroutineDispatcher.runBlockingTest {
        val context: Context = ApplicationProvider.getApplicationContext()

        val previousAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!

        val intent =
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                repeatingReminder.id
            )
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder
        binder.snooze()

        val nextAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!
        assertThat(nextAlarm.id).isNotEqualTo(previousAlarm.id)
        assertThat(nextAlarm.reminderId).isEqualTo(previousAlarm.reminderId)
        assertThat(alarmRepository.findById(previousAlarm.id)).isNull()
        assertThat(nextAlarm.triggerAt).isGreaterThan(0)
    }

    @Test
    fun localBinder_skip_skipsAlarm() {
        val context: Context = ApplicationProvider.getApplicationContext()

        val intent =
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                repeatingReminder.id
            )
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder
        binder.skip()

        testCoroutineDispatcher.runBlockingTest {
            val history = historyRepository.getAll().first().find {
                it.reminderId == repeatingReminder.id && it.completionStatus == ReminderStatus.SKIPPED
            }
            assertThat(history).isNotNull()
        }
    }

    @Test
    fun localBinder_skipOneOffReminder_doesNotReschedule() =
        testCoroutineDispatcher.runBlockingTest {
            val context: Context = ApplicationProvider.getApplicationContext()

            val intent =
                ReminderAlarmManager.getActionIntent(
                    context,
                    ACTION_ALARM_SET_NOTIF,
                    oneOffReminder.id
                )
            serviceRule.startService(intent)

            val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder
            binder.skip()

            val nextAlarm = alarmRepository.findByReminderId(oneOffReminder.id)
            assertThat(nextAlarm).isNull()
        }

    @Test
    fun localBinder_complete_completesAlarm() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent =
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                repeatingReminder.id
            )
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder
        binder.complete()

        testCoroutineDispatcher.runBlockingTest {
            val history = historyRepository.getAll().first().find {
                it.reminderId == repeatingReminder.id && it.completionStatus == ReminderStatus.COMPLETED
            }
            assertThat(history).isNotNull()
        }
    }


    @Test
    fun localBinder_completeOneOffReminder_doesNotReschedule() =
        testCoroutineDispatcher.runBlockingTest {
            val context: Context = ApplicationProvider.getApplicationContext()

            val intent =
                ReminderAlarmManager.getActionIntent(
                    context,
                    ACTION_ALARM_SET_NOTIF,
                    oneOffReminder.id
                )
            serviceRule.startService(intent)

            val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder
            binder.complete()

            val nextAlarm = alarmRepository.findByReminderId(oneOffReminder.id)
            assertThat(nextAlarm).isNull()
        }

    @Test
    fun localBinder_doubleAction_doesOnlyOnce() = testCoroutineDispatcher.runBlockingTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent =
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                repeatingReminder.id
            )
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder

        // do once
        binder.snooze()

        // expect that it was saved the first time
        val nextAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!
        assertThat(nextAlarm.triggerAt).isGreaterThan(0)

        // do again
        binder.snooze()

        // expect that the saved alarm is the same one
        val duplicateNextAlarm = alarmRepository.findByReminderId(repeatingReminder.id)
        assertThat(duplicateNextAlarm).isNotNull()
        assertThat(duplicateNextAlarm).isEqualTo(nextAlarm)
    }

    @Test
    fun autoSnooze_noPreviousAction_happens() = testCoroutineDispatcher.runBlockingTest {
        val context: Context = ApplicationProvider.getApplicationContext()

        val previousAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!

        val intent =
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                repeatingReminder.id
            )
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder

        // advance time, expect that the event was fired but not handled because there's no listener
        advanceTimeBy((DELAY_UNTIL_SNOOZE_SEC + 10) * 1000)
        assertThat(binder.snoozeTimeout.peek()).isNotNull()
        assertThat(binder.snoozeTimeout.handled).isFalse()

        // expect that an alarm for the same reminder, but a different id can be found
        val nextAlarm = alarmRepository.findByReminderId(repeatingReminder.id)!!
        assertThat(nextAlarm.id).isNotEqualTo(previousAlarm.id)
        assertThat(nextAlarm.reminderId).isEqualTo(previousAlarm.reminderId)
        assertThat(alarmRepository.findById(previousAlarm.id)).isNull()
    }

    @Test
    fun autoSnooze_withPreviousAction_doesNotHappen() = testCoroutineDispatcher.runBlockingTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent =
            ReminderAlarmManager.getActionIntent(
                context,
                ACTION_ALARM_SET_NOTIF,
                oneOffReminder.id // use the oneOff to prevent reschedule
            )
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder

        // do some completion action
        binder.complete()

        // advance time, expect that the event was never fired
        advanceTimeBy((DELAY_UNTIL_SNOOZE_SEC + 10) * 1000)
        assertThat(binder.snoozeTimeout.peek()).isNull()
        assertThat(binder.snoozeTimeout.handled).isFalse()

        val nextAlarm = alarmRepository.findByReminderId(oneOffReminder.id)
        assertThat(nextAlarm).isNull()
    }

    @Test
    fun alarmSavedWhileServiceOpen_isStillScheduled() = testCoroutineDispatcher.runBlockingTest {
        val context: Context = ApplicationProvider.getApplicationContext()

        lateinit var intent: Intent
        lateinit var reminder: Reminder
        pauseDispatcher {
            intent =
                ReminderAlarmManager.getActionIntent(
                    context,
                    ACTION_ALARM_SET_NOTIF,
                    repeatingReminder.id
                )
            serviceRule.startService(intent)

            // Save an alarm
            reminder = Reminder(description = "Temp Reminder")
            reminder.id = reminderRepository.save(reminder)
            val alarm =
                Alarm(
                    id = 0,
                    reminderId = reminder.id,
                    triggerAt = LocalDateTime.now().toEpochMilli()
                )
            alarm.id = alarmRepository.save(alarm)
        }

        val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder
        binder.snooze()

        val nextAlarm = alarmRepository.findByReminderId(reminder.id)
        assertThat(nextAlarm).isNotNull()
        assertThat(nextAlarm!!.scheduled).isTrue()
    }
}