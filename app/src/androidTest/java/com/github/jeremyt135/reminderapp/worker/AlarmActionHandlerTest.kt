package com.github.jeremyt135.reminderapp.worker

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.work.*
import androidx.work.ListenableWorker.Result
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.data.local.AppDatabase
import com.github.jeremyt135.reminderapp.data.local.HistoryLocalDataSource
import com.github.jeremyt135.reminderapp.data.local.ReminderLocalDataSource
import com.github.jeremyt135.reminderapp.di.DatabaseModule
import com.github.jeremyt135.reminderapp.di.RepositoryModule
import com.github.jeremyt135.reminderapp.util.InMemoryDatabaseRule
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.google.common.truth.Truth.assertThat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import io.mockk.coVerify
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@ExperimentalCoroutinesApi
@MediumTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
@UninstallModules(DatabaseModule::class, RepositoryModule::class)
@HiltAndroidTest
class AlarmActionHandlerTest {

    @InstallIn(SingletonComponent::class)
    @Module
    inner class TestModule {
        @Provides
        @Singleton
        fun provideDb(): AppDatabase = inMemoryDatabaseRule.db

        @Provides
        @Singleton
        fun provideReminderRepository(): ReminderRepository =
            ReminderRepositoryImpl(ReminderLocalDataSource(inMemoryDatabaseRule.db.reminderDao()))

        @Provides
        @Singleton
        fun provideAlarmRepository(): AlarmRepository =
            AlarmRepositoryImpl(inMemoryDatabaseRule.db.alarmDao())

        @Provides
        @Singleton
        fun provideHistoryRepository(): HistoryRepository =
            HistoryRepositoryImpl(HistoryLocalDataSource(inMemoryDatabaseRule.db.historyDao()))
    }

    @get:Rule(order = 0)
    var inMemoryDatabaseRule = InMemoryDatabaseRule()

    @get:Rule(order = 1)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    var instantExecutorRules = InstantTaskExecutorRule()

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    lateinit var testCoordinator: WorkTestCoordinator

    @Before
    fun setup() {
        hiltRule.inject()
        mockkStatic(::showSnoozeNotification, ::scheduleNextAlarm, ::showSkipNotification)
        testCoordinator =
            WorkTestCoordinator(alarmRepository, reminderRepository, historyRepository)
    }

    @Test
    fun actionIsNull_returnsFailure() {
        testCoordinator.runWorkTest(workDataOf()) {
            val result = it.doWork()
            assertThat(result).isEqualTo(Result.failure())
        }
    }

    @Test
    fun actionIsNotExpected_returnsFailure() {
        testCoordinator.runWorkTest(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_START
            )
        ) {
            val result = it.doWork()
            assertThat(result).isEqualTo(Result.failure())
        }
    }

    @Test
    fun actionSnooze_snoozesAlarm_showsNotification() = runBlockingTest {
        // Setup
        val reminder = Reminder()
        val alarm = Alarm(
            id = 1,
            reminderId = reminder.id,
            triggerAt = currentLocalDateTime(),
            maxSnoozeRepeats = 3
        )

        reminderRepository.save(reminder)
        alarmRepository.save(alarm)

        testCoordinator.runWorkTest(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_SNOOZE,
                EXTRA_REMINDER_ID to reminder.id
            )
        ) { worker ->
            // Do the work expecting success
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.success())

            // Should contain a new alarm but not the old one
            val alarms = alarmRepository.getAll().first()
            assertThat(alarms).contains(
                alarm.copy(
                    id = 2,
                    notificationId = 2,
                    snoozeCount = 1,
                    triggerAt = currentLocalDateTime().plusMinutes(reminder.snoozeDuration)
                )
            )
            assertThat(alarms).doesNotContain(alarm)

            // Should have made notifications
            verify {
                showSnoozeNotification(any(), reminder, any())
            }

            val nextAlarm = alarms.first { it.id == 2L }
            // The notification should be made with the data used
            assertThat(nextAlarm.reminderId).isEqualTo(reminder.id)
            assertThat(nextAlarm.triggerAt).isEqualTo(
                alarm.triggerAt.plusMinutes(alarm.snoozeDuration)
            )
            assertThat(nextAlarm.snoozeCount).isEqualTo(1)
        }
    }

    @Test
    fun actionSnooze_alarmNeverSnoozes_returnsFailure() = runBlockingTest {
        // Setup
        val reminder = Reminder()
        val alarm = Alarm(
            id = 1,
            reminderId = reminder.id,
            triggerAt = currentLocalDateTime(),
            maxSnoozeRepeats = 0
        )

        reminderRepository.save(reminder)
        alarmRepository.save(alarm)

        testCoordinator.runWorkTest(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_SNOOZE,
                EXTRA_REMINDER_ID to reminder.id
            )
        ) { worker ->
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.failure())

            // Should not have saved a new one, and should have deleted old one
            val alarms = alarmRepository.getAll().first()
            assertThat(alarms).doesNotContain(alarm)

            coVerify(exactly = 0) {
                showSnoozeNotification(any(), reminder, any()) // No notification should trigger
            }
        }
    }

    @Test
    fun actionSnooze_alarmCannotBeSnoozedMore_returnsFailure() = runBlockingTest {
        // Setup
        val reminder = Reminder()
        val alarm = Alarm(
            id = 1,
            reminderId = reminder.id,
            triggerAt = currentLocalDateTime(),
            maxSnoozeRepeats = 1,
            snoozeCount = 1
        )

        reminderRepository.save(reminder)
        alarmRepository.save(alarm)

        testCoordinator.runWorkTest(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_SNOOZE,
                EXTRA_REMINDER_ID to reminder.id
            )
        ) { worker ->
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.failure())

            // Should not have saved a new one, and should have deleted old one
            val alarms = alarmRepository.getAll().first()
            assertThat(alarms).doesNotContain(alarm)

            coVerify(exactly = 0) {
                showSnoozeNotification(any(), reminder, any())
            }
        }
    }

    @Test
    fun actionSkip_savesHistoryAsSkip_schedulesNext() = runBlockingTest {
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    type = SchedulingType.REPEATING,
                    daysOfWeek = sortedSetOf(*DayOfWeek.values()),
                )
            )
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())

        // Setup
        reminderRepository.save(reminder)
        alarmRepository.save(alarm)


        testCoordinator.runWorkTest(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_SKIP,
                EXTRA_REMINDER_ID to reminder.id
            )
        ) { worker ->
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.success())

            // Should contain a new alarm but not the old one
            val alarms = alarmRepository.getAll().first()
            assertThat(alarms).contains(
                alarm.copy(
                    id = 2,
                    notificationId = 2,
                    snoozeCount = 0,
                    triggerAt = reminder.schedulingRules.getNextTimeForAlarm(currentLocalDateTime())!!
                )
            )
            assertThat(alarms).doesNotContain(alarm)

            // Should have saved a history entry
            val historyEntries = historyRepository.findByReminderId(reminder.id).first()
            assertThat(historyEntries).hasSize(1)
            val history = historyEntries[0]
            assertThat(history.copy(id = "", completedAt = 0)).isEqualTo(
                History(
                    id = "",
                    reminderId = reminder.id,
                    description = reminder.description,
                    completionStatus = ReminderStatus.SKIPPED,
                    completedAt = 0
                )
            )

            // Check history saved to current second, accounting for test delays
            assertThat(abs(history.completedAt - Instant.now().toEpochMilli()) < 60)

            // Ensure next alarm has same reminder id and the next time
            val nextAlarm = alarms.first { it.id == 2L }
            assertThat(nextAlarm).isNotNull()
            assertThat(nextAlarm.reminderId).isEqualTo(reminder.id)
            assertThat(nextAlarm.triggerAt).isEqualTo(
                reminder.schedulingRules.getNextTimeForAlarm(
                    currentLocalDateTime()
                )!!
            )
        }
    }

    @Test
    fun actionComplete_savesHistoryAsComplete_schedulesNext() = runBlockingTest {
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    type = SchedulingType.REPEATING,
                    daysOfWeek = sortedSetOf(*DayOfWeek.values())
                )
            )
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())
        // Setup
        reminderRepository.save(reminder)
        alarmRepository.save(alarm)

        testCoordinator.runWorkTest(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_COMPLETE,
                EXTRA_REMINDER_ID to reminder.id
            )
        ) { worker ->
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.success())

            // Should save history as complete, and there should be another alarm
            val alarms = alarmRepository.getAll().first()
            assertThat(alarms).contains(
                alarm.copy(
                    id = 2,
                    notificationId = 2,
                    snoozeCount = 0,
                    triggerAt = reminder.schedulingRules.getNextTimeForAlarm(currentLocalDateTime())!!
                )
            )
            assertThat(alarms).doesNotContain(alarm)


            // Should have saved a history entry
            val historyEntries = historyRepository.findByReminderId(reminder.id).first()
            assertThat(historyEntries).hasSize(1)
            val history = historyEntries[0]
            assertThat(history.copy(id = "", completedAt = 0)).isEqualTo(
                History(
                    id = "",
                    reminderId = reminder.id,
                    description = reminder.description,
                    completionStatus = ReminderStatus.COMPLETED,
                    completedAt = 0
                )
            )

            // Check history saved to current second, accounting for test delays
            assertThat(abs(history.completedAt - Instant.now().toEpochMilli()) < 60)

            // Ensure next alarm has same reminder id and the next time
            val nextAlarm = alarms.first { it.id == 2L }
            assertThat(nextAlarm).isNotNull()
            assertThat(nextAlarm.reminderId).isEqualTo(reminder.id)
            assertThat(nextAlarm.triggerAt).isEqualTo(
                reminder.schedulingRules.getNextTimeForAlarm(
                    currentLocalDateTime()
                )!!
            )
        }
    }
}