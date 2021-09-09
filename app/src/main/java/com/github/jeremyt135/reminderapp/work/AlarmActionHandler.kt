package com.github.jeremyt135.reminderapp.work

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import androidx.work.ListenableWorker.Result
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.util.NotificationId
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Performs the necessary work to handle an Alarm action like
 * snooze, schedule-next, skip, and complete.
 */
@HiltWorker
class AlarmActionHandler @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    alarmRepository: AlarmRepository,
    reminderRepository: ReminderRepository,
    historyRepository: HistoryRepository
) : CoroutineWorker(context, workerParameters) {

    // Using pimpl because the test worker builders don't work well with injection & easier to test
    private val impl = AlarmActionHandlerImpl(
        context, inputData, alarmRepository, reminderRepository, historyRepository
    )

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        return impl.doWork()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channel = applicationContext.getString(R.string.scheduling_notification_channel_id)
        val title = "Reminders scheduling"
        val notification = NotificationCompat.Builder(applicationContext, channel).apply {
            setContentTitle(title)
            setSmallIcon(R.drawable.ic_notification_alarm)
            setContentText("Reminders app is scheduling your reminder")
            setPriority(NotificationCompat.PRIORITY_LOW)
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            setOngoing(true)
        }.build()
        return ForegroundInfo(Int.MAX_VALUE, notification)
    }

    companion object {
        fun createForAction(action: String, reminderId: String) =
            OneTimeWorkRequestBuilder<AlarmActionHandler>()
                .setInputData(
                    workDataOf(
                        EXTRA_ALARM_ACTION_NAME to action,
                        EXTRA_REMINDER_ID to reminderId
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

    }

}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
class AlarmActionHandlerImpl(
    private val applicationContext: Context,
    private val inputData: Data,
    private val alarmRepository: AlarmRepository,
    private val reminderRepository: ReminderRepository,
    private val historyRepository: HistoryRepository
) {
    private val actions = arrayOf(
        ACTION_ALARM_SCHEDULE_NEXT,
        ACTION_ALARM_SNOOZE,
        ACTION_ALARM_SKIP,
        ACTION_ALARM_COMPLETE,
    )

    suspend fun doWork(): Result {
        val action = inputData.getString(EXTRA_ALARM_ACTION_NAME) ?: return Result.failure()
        val reminderId = inputData.getString(EXTRA_REMINDER_ID) ?: ""
        return try {
            if (action !in actions) {
                Timber.e("Invalid action for doWork: $action")
                Result.failure()
            } else {
                Timber.d("Started doWork for $action")
                handleAction(action, reminderId)
            }
        } catch (e: Exception) {
            Timber.e(e)
            Result.retry()
        }
    }

    /**
     * Attempts to handle an action
     */
    private suspend fun handleAction(action: String, reminderId: String): Result {
        return if (action != ACTION_ALARM_SCHEDULE_NEXT) {
            // Do action
            when (action) {
                ACTION_ALARM_SNOOZE -> handleSnooze(reminderId)
                ACTION_ALARM_SKIP -> handleSkip(reminderId)
                ACTION_ALARM_COMPLETE -> handleCompleted(reminderId)
                else -> Result.failure()
            }
        } else {
            scheduleNext()
        }
    }

    private suspend fun scheduleNext(): Result =
        if (alarmRepository.getAll().first().isNotEmpty()) {
            if (scheduleNextAlarm(applicationContext, alarmRepository, reminderRepository)) {
                Result.success()
            } else Result.retry()
        } else Result.failure()

    private suspend fun handleSkip(reminderId: String): Result =
        saveHistoryWithStatus(reminderId, ReminderStatus.SKIPPED)

    private suspend fun handleCompleted(reminderId: String): Result =
        saveHistoryWithStatus(reminderId, ReminderStatus.COMPLETED)

    private fun enqueueScheduleNext() {
        AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, "").let {
            WorkManager.getInstance(applicationContext).enqueue(it)
        }
    }

    private fun enqueueSkip(reminderId: String) {
        AlarmActionHandler.createForAction(ACTION_ALARM_SKIP, reminderId).let {
            WorkManager.getInstance(applicationContext).enqueue(it)
        }
    }

    /**
     * Performs completion of a Reminder - cancels alarms, saves a new alarm and history entry,
     * schedules next alarm
     */
    private suspend fun saveHistoryWithStatus(
        reminderId: String,
        completionStatus: ReminderStatus
    ): Result {
        val reminder = reminderRepository.findById(reminderId) ?: return Result.failure()
        saveHistoryEntry(reminder, completionStatus)

        // Allow a Reminder with no associated Alarm, in which case only interested in saving history
        alarmRepository.findByReminderId(reminder.id)?.let { alarm ->

            // Save a new alarm if appropriate - not one-off and has an upcoming time
            if (!reminder.schedulingRules.isOneOff) {
                reminder.schedulingRules.getNextTimeForAlarm(currentLocalDateTime())
                    ?.let { nextTime ->
                        alarmRepository.save(
                            alarm.copy(
                                id = 0,
                                triggerAt = nextTime,
                                snoozeCount = 0,
                                scheduled = null,
                                notificationId = NotificationId.next
                            )
                        )
                    }
            } else {
                // is a one-off, delete the reminder since it can no longer be scheduled
                reminderRepository.delete(reminder)
            }
            alarmRepository.delete(alarm)
        }

        enqueueScheduleNext()

        return Result.success()
    }

    private suspend fun saveHistoryEntry(
        reminder: Reminder,
        completionStatus: ReminderStatus
    ) {
        val history = History(
            reminderId = reminder.id,
            description = reminder.description,
            completionStatus = completionStatus,
            completedAt = Instant.now().toEpochMilli()
        )
        historyRepository.save(history)
    }

    private suspend fun handleSnooze(reminderId: String): Result {
        val reminder = reminderRepository.findById(reminderId) ?: return Result.failure()
        val alarm = alarmRepository.findByReminderId(reminder.id) ?: return Result.failure()
        Timber.d("Handle snooze: ${reminder.id}")
        return snoozeAlarm(applicationContext, reminder, alarm)
    }

    private suspend fun snoozeAlarm(context: Context, reminder: Reminder, alarm: Alarm): Result {
        Timber.d("Cancelling previous notification: ${alarm.notificationId}")
        NotificationManagerCompat.from(context).cancel(alarm.notificationId)

        // Determine if the alarm can snooze again
        val result = if (
            alarm.snoozeRules.neverRepeats ||
            !alarm.snoozeRules.canSnooze(alarm.snoozeCount)
        ) {
            // Can't snooze again, never snoozes or out of attempts
            Timber.d("Not snoozing Alarm: beyond allowed limit")
            enqueueSkip(reminder.id)
            showSkipNotification(context, reminder, alarm)
            Result.failure()
        } else {
            // Determine next triggerAt for a snoozing alarm
            val nextAlarm = alarm.copy(
                id = 0,
                scheduled = null,
                notificationId = NotificationId.next,
                snoozeCount = alarm.snoozeCount + 1,
                // may cause backed up scheduling if multiple alarms scheduled w/in a certain time window
                // triggerAt = alarm.triggerAt.plusMinutes(SNOOZE_TIME_MIN),

                // if Alarm A, B, C all are scheduled for time T, you wouldn't want the last one C
                // to snooze until T+2:00, you'd want it to snooze until T_next=T_current+2:00 since
                // T could be now in the past by a minute or more
                triggerAt = currentLocalDateTime().plusMinutes(alarm.snoozeDuration),
            )

            // Delete previous, save changes
            nextAlarm.id = alarmRepository.save(nextAlarm)
            Timber.d("Saved next alarm: $nextAlarm")

            Timber.d("Creating notification: ${nextAlarm.notificationId}")
            showSnoozeNotification(context, reminder, nextAlarm)

            Result.success()
        }

        alarmRepository.delete(alarm)
        enqueueScheduleNext()

        return result
    }
}