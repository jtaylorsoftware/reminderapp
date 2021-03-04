package com.github.jeremyt135.reminderapp.service

import android.content.Context
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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

    private lateinit var reminder: Reminder

    @Before
    fun setup() {
        hiltRule.inject()

        reminder = Reminder(
            description = "My Reminder",
            daysOfWeek = sortedSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        )
        runBlocking {
            reminder.id = reminderRepository.save(reminder)
        }
        mockkStatic(::showToast)
        every { showToast(any(), any(), any()) } returns Unit
    }

    @Test
    fun start_actionStart_createsNotification() {
        val context: Context = ApplicationProvider.getApplicationContext()
        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SET_NOTIF, reminder.id)
        )

        assertThat(getCreatedIntentForAlarmActivity(context, reminder.id)).isNotNull()
    }

    @Test
    fun onBind_returnsLocalBinder() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent =
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SET_NOTIF, reminder.id)
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent)
        assertThat(binder).isInstanceOf(ReminderAlarmManager.LocalBinder::class.java)
    }

    @Test
    fun actionSnooze_snoozesAlarm() = testCoroutineDispatcher.runBlockingTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SET_NOTIF, reminder.id)
        )

        mockkStatic(::scheduleNextAlarm, ::showToast, ::triggerNotification)
        coJustRun { scheduleNextAlarm(any(), alarmRepository, reminderRepository) }
        every { showToast(any(), any(), any()) } returns Unit
        every { triggerNotification(any(), any(), any()) } returns Unit

        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SNOOZE, reminder.id)
        )

        val nextAlarm = alarmRepository.findByReminderId(reminder.id)!!

        coVerify {
            scheduleNextAlarm(any(), alarmRepository, reminderRepository)
            showToast(any(), any())
            triggerSnoozeNotification(
                any(),
                reminder,
                nextAlarm
            )
        }
    }

    @Test
    fun actionSkip_savesHistory_andReschedules() {
        val context: Context = ApplicationProvider.getApplicationContext()
        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SET_NOTIF, reminder.id)
        )

        mockkStatic(::scheduleNextAlarm)
        coJustRun { scheduleNextAlarm(any(), alarmRepository, reminderRepository) }

        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SKIP, reminder.id)
        )

        runBlockingTest {
            val history = historyRepository.getAll().first().find {
                it.reminderId == reminder.id && it.completionStatus == ReminderStatus.SKIPPED
            }
            assertThat(history).isNotNull()

            val nextAlarm = alarmRepository.findByReminderId(reminder.id)
            assertThat(nextAlarm).isNotNull()
        }
    }

    @Test
    fun actionComplete_savesHistory_andReschedules() {
        val context: Context = ApplicationProvider.getApplicationContext()
        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SET_NOTIF, reminder.id)
        )

        mockkStatic(::scheduleNextAlarm)
        coJustRun { scheduleNextAlarm(any(), alarmRepository, reminderRepository) }

        serviceRule.startService(
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_COMPLETE, reminder.id)
        )

        runBlockingTest {
            val history = historyRepository.getAll().first().find {
                it.reminderId == reminder.id && it.completionStatus == ReminderStatus.COMPLETED
            }
            assertThat(history).isNotNull()

            val nextAlarm = alarmRepository.findByReminderId(reminder.id)
            assertThat(nextAlarm).isNotNull()
        }
    }

    @Test
    fun localBinder_snooze_snoozesAlarm() = testCoroutineDispatcher.runBlockingTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent =
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SET_NOTIF, reminder.id)
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder
        binder.snooze()

        val nextAlarm = alarmRepository.findByReminderId(reminder.id)!!
        assertThat(nextAlarm.triggerAt).isGreaterThan(0)
    }

    @Test
    fun localBinder_skip_skipsAlarm() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent =
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SET_NOTIF, reminder.id)
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder
        binder.skip()

        runBlockingTest {
            val history = historyRepository.getAll().first().find {
                it.reminderId == reminder.id && it.completionStatus == ReminderStatus.SKIPPED
            }
            assertThat(history).isNotNull()
        }
    }

    @Test
    fun localBinder_complete_completesAlarm() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent =
            ReminderAlarmManager.getActionIntent(context, ACTION_ALARM_SET_NOTIF, reminder.id)
        serviceRule.startService(intent)

        val binder = serviceRule.bindService(intent) as ReminderAlarmManager.LocalBinder
        binder.complete()

        runBlockingTest {
            val history = historyRepository.getAll().first().find {
                it.reminderId == reminder.id && it.completionStatus == ReminderStatus.COMPLETED
            }
            assertThat(history).isNotNull()
        }
    }

}