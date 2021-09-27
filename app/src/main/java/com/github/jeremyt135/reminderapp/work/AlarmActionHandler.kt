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
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.util.NotificationId
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
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
            setSmallIcon(R.drawable.ic_scheduling_notification)
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
        return when (action) {
            ACTION_ALARM_SNOOZE -> handleSnooze(reminderId)
            ACTION_ALARM_SKIP -> handleSkip(reminderId)
            ACTION_ALARM_COMPLETE -> handleCompleted(reminderId)
            ACTION_ALARM_SCHEDULE_NEXT -> scheduleNext()
            else -> Result.failure()
        }
    }

    private suspend fun scheduleNext(): Result =
        if (alarmRepository.getAll().first().isNotEmpty()) {
            if (scheduleNextAlarm(applicationContext, alarmRepository, reminderRepository)) {
                Result.success()
            } else Result.retry()
        } else Result.failure()

    private suspend fun handleSkip(reminderId: String): Result =
        saveHistoryForReminder(reminderId, ReminderStatus.SKIPPED)

    private suspend fun handleCompleted(reminderId: String): Result =
        saveHistoryForReminder(reminderId, ReminderStatus.COMPLETED)

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
     * Saves a history entry for a reminder and saves a fresh alarm for the reminder if possible.
     * Always results in a call to enqueueScheduleNext.
     * @param reminderId Id of the reminder to save history for.
     * @param completionStatus Status to use for the new history entry.
     */
    private suspend fun saveHistoryForReminder(
        reminderId: String,
        completionStatus: ReminderStatus
    ): Result {
        val reminder = reminderRepository.findById(reminderId) ?: return Result.failure()

        // Save the history for the reminder
        saveHistoryEntry(reminder, completionStatus)

        // Delete previous alarm if it exists
        alarmRepository.findByReminderId(reminder.id)?.let {
            alarmRepository.delete(it)
        }

        // Try to save a new alarm, if reminder's type is repeating
        if (reminder.schedulingRules.type != SchedulingType.SINGLE) {
            // Get next time for alarm, save if there is a next time
            reminder.schedulingRules.getNextTimeForAlarm(currentLocalDateTime())?.let { nextDateTime ->
                alarmRepository.save(Alarm(
                    reminderId = reminder.id,
                    triggerAt = nextDateTime,
                    maxSnoozeRepeats = reminder.maxSnoozeRepeats,
                    snoozeDuration = reminder.snoozeDuration
                ))
            }
        } else {
            // Single reminders are deleted after they have completed - this is a design decision
            // to avoid confusion that a reminder is in the list but never rescheduled.
            reminderRepository.delete(reminder)
        }

        // Schedule the next alarm, whatever it ends up being
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
            // Save history with time to highest resolution to allow distinguishing between multiple
            // history on same day, possibly within same minute
            completedAt = Instant.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
            Result.failure() // Fail rather than retry, because this alarm is done snoozing
        } else {
            // Determine next triggerAt for a snoozing alarm
            val nextAlarm = alarm.copy(
                id = 0,
                scheduled = null,
                notificationId = NotificationId.next,
                snoozeCount = alarm.snoozeCount + 1,

                // Always save the snooze as an offset from the current time to avoid
                // this alarm getting backed up behind others.
                triggerAt = currentLocalDateTime().plusMinutes(alarm.snoozeDuration),
            )

            // Save next alarm
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