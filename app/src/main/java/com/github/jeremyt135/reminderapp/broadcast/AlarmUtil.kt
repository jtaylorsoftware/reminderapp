package com.github.jeremyt135.reminderapp.broadcast

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.screens.alarm.AlarmActivity
import com.github.jeremyt135.reminderapp.screens.reminder.editor.ReminderEditorActivity
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.getShortLocalDateDisplay
import com.github.jeremyt135.reminderapp.util.getShortLocalTimeDisplay
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Action to schedule the next Alarm
 */
const val ACTION_ALARM_SCHEDULE_NEXT =
    "com.github.jeremyt135.reminderapp.ACTION_ALARM_SCHEDULE_NEXT"

/**
 * Action to start AlarmService
 */
const val ACTION_ALARM_START = "com.github.jeremyt135.reminderapp.ACTION_ALARM_START"

/**
 * Action to request AlarmService stops
 */
const val ACTION_ALARM_STOP = "com.github.jeremyt135.reminderapp.ACTION_ALARM_STOP"

/**
 * Action to snooze an Alarm
 */
const val ACTION_ALARM_SNOOZE = "com.github.jeremyt135.reminderapp.ACTION_ALARM_SNOOZE"

/**
 * Action to mark an Alarm as ReminderStatus.SKIPPED
 */
const val ACTION_ALARM_SKIP = "com.github.jeremyt135.reminderapp.ACTION_ALARM_SKIP"

/**
 * Action to mark an Alarm as ReminderStatus.COMPLETED
 */
const val ACTION_ALARM_COMPLETE = "com.github.jeremyt135.reminderapp.ACTION_ALARM_COMPLETE"

/**
 * String: The name of the Alarm action being taken (used in workers or where there is no Intent)
 */
const val EXTRA_ALARM_ACTION_NAME = "com.github.jeremyt135.reminderapp.EXTRA_ALARM_ACTION_NAME"

/**
 * String: The UUID of the Reminder
 */
const val EXTRA_REMINDER_ID = "com.github.jeremyt135.reminderapp.EXTRA_REMINDER_ID"

/**
 * Long: The ID of the Alarm
 */
const val EXTRA_ALARM_ID = "com.github.jeremyt135.reminderapp.EXTRA_ALARM_ID"

/**
 *
 */
const val EXTRA_REMINDER_START_DATE = "com.github.jeremyt135.reminderapp.EXTRA_REMINDER_START_DATE"

/**
 * Bool: The on-off should-vibrate setting from the Reminder as entered into the editor.
 */
const val EXTRA_REMINDER_VIBRATE = "com.github.jeremyt135.reminderapp.EXTRA_REMINDER_VIBRATE"

/**
 * String: The description of the Reminder as entered into the editor.
 */
const val EXTRA_REMINDER_DESCRIPTION =
    "com.github.jeremyt135.reminderapp.EXTRA_REMINDER_DESCRIPTION"

/**
 * String: The sound played by the Reminder
 */
const val EXTRA_REMINDER_SOUND_URI = "com.github.jeremyt135.reminderapp.EXTRA_REMINDER_SOUND"

/**
 * A 30 seconds long vibration pattern
 */
val VIBRATION_PATTERN: LongArray by lazy {
    val value = Array<Long>(31) { 1000 }.toLongArray()
    value[0] = 0
    value
}

/**
 * Notification light colors and duration
 */
const val NOTIFICATION_LED_ARGB: Int = Color.GREEN
const val NOTIFICATION_LED_ONMS: Int = 1
const val NOTIFICATION_LED_OFFMS: Int = 0

/**
 * Schedules the Alarm that is chronologically next to trigger today, if one exists.
 */
suspend fun scheduleNextAlarm(
    context: Context,
    alarmRepository: AlarmRepository,
    reminderRepository: ReminderRepository,
): Boolean {
    Timber.d("Scheduling next alarm...")

    val alarm = alarmRepository.getFirstByTimeAscending() ?: return false

    val previousScheduled = alarmRepository.getFirstScheduledAlarm()
    if (previousScheduled != alarm) {
        // Alarm is not the one already scheduled - clear previous & set new
        previousScheduled?.let { prevAlarm ->
            alarmRepository.clearScheduled(prevAlarm)
            reminderRepository.findById(prevAlarm.reminderId)?.let { prevReminder ->
                // Clear the pending intents
                cancelAlarmIntentsForReminder(context, prevReminder)
            }
        }
        alarmRepository.setScheduled(alarm)
        reminderRepository.findById(alarm.reminderId)?.let { reminder ->
            scheduleAlarmForReminder(context, alarm.copy(scheduled = true), reminder)
        }
    } // Else the Alarm was the one already scheduled
    return true
}

/**
 * Deletes all alarms, clears the scheduled flag for all alarms, saves new alarms
 * for all reminders, and then creates an action to schedule next alarm. Reminders that have
 * been completed on the current date will not be schedule on the same day, instead they
 * are scheduled when next available.
 * For use when the application has restarted from a force stop, reinstalled, or when phone restarts.
 * Type SINGLE reminders that expired while the app was unable to run will be rescheduled if they don't have
 * any history.
 * @return Number of saved alarms.
 */
suspend fun rescheduleReminders(
    context: Context,
    reminderRepository: ReminderRepository,
    alarmRepository: AlarmRepository,
    historyRepository: HistoryRepository
): Int {
    Timber.d("Rescheduling all reminders and their alarms...")

    // Clear the past alarms
    alarmRepository.deleteAll()

    Timber.d("Cleared all past alarms, saving new ones...")

    // Get all reminders and re-save their alarms
    val reminders = reminderRepository.getAllWithTodayHistory().first()
    val rescheduledAlarmIds: List<Long> = reminders.map { (reminder, historyToday) ->
        // Allow same day alarm if either a) no history or b) history.status is PENDING
        val allowSameDay =
            historyToday?.let { it.completionStatus == ReminderStatus.PENDING }
                ?: true


        // The actual time that's scheduled next
        val scheduledNextTimeForAlarm =
            reminder.schedulingRules.getNextTimeForAlarm(
                currentLocalDateTime(),
                nextTimeOptions = SchedulingRules.NextTimeOptions(allowSameDay = allowSameDay)
            )

        // If no history or pending history, SINGLE reminders can be rescheduled (they may exist
        // on restart depending on several factors including how the app crashed and when it next
        // started)
        val recoveredNextTimeForAlarm = scheduledNextTimeForAlarm
            ?: if (reminder.schedulingRules.type == SchedulingType.SINGLE) {
                // Check if there's any history for it
                val latestHistory = historyRepository.getLatestForReminder(reminder.id)
                if (latestHistory == null) currentLocalDateTime().plusMinutes(5) else null
            } else null

        with(reminder) {
            recoveredNextTimeForAlarm?.let { time ->
                val nextAlarm = Alarm(
                    reminderId = id,
                    triggerAt = time,
                    maxSnoozeRepeats = maxSnoozeRepeats,
                    snoozeDuration = snoozeDuration
                )
                alarmRepository.save(nextAlarm)
            } ?: 0L
        }
    }

    val numRescheduled = rescheduledAlarmIds.count { it != 0L }
    val nextAlarmId = rescheduledAlarmIds.firstOrNull() ?: 0L

    Timber.d("Saved $numRescheduled new alarms, starting action for SCHEDULE_NEXT...")

    enqueueScheduleNext(context, nextAlarmId)

    return numRescheduled
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun enqueueScheduleNext(context: Context, nextAlarmId: Long) {
    val worker = AlarmActionHandler.createForScheduleNext("", nextAlarmId)
    WorkManager.getInstance(context).enqueue(worker)
}

/**
 * Schedules a system AlarmClock for the Alarm and Reminder, using the Alarm.triggerAt value
 * for the AlarmClock triggerAt value.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun scheduleAlarmForReminder(
    context: Context,
    alarm: Alarm,
    reminder: Reminder
) {
    Timber.d("Scheduling $alarm for ${alarm.triggerAt.getShortLocalDateDisplay()} at ${alarm.triggerAt.getShortLocalTimeDisplay()}")
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = createIntentForAlarmAction(context, ACTION_ALARM_START, reminder.id)
    val showIntent = getIntentForEditorActivity(context, reminder.id)

    AlarmManagerCompat.setAlarmClock(
        alarmManager, alarm.triggerAt.toEpochMilli(),
        showIntent,
        intent
    )
}

/**
 * Cancels previously scheduled PendingIntents associated with the given alarm.
 */
fun cancelAlarmIntentsForReminder(context: Context, reminder: Reminder) {
    if (reminder.id.isEmpty()) {
        return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Cancel the alarm notification intent
    getCreatedIntentForAlarmAction(context, ACTION_ALARM_START, reminder.id)?.let {
        alarmManager.cancel(it)
    }

    // Cancel the snooze intent
    getCreatedIntentForAlarmAction(context, ACTION_ALARM_SNOOZE, reminder.id)?.let {
        alarmManager.cancel(it)
    }
}

fun showSnoozeNotification(context: Context, reminder: Reminder, alarm: Alarm) {
    val notification = buildSnoozeNotification(context, reminder, alarm)
    with(NotificationManagerCompat.from(context)) {
        notify(alarm.notificationId, notification)
    }
}

fun showSkipNotification(context: Context, reminder: Reminder, alarm: Alarm) {
    val notification = buildSkipNotification(context, reminder)
    with(NotificationManagerCompat.from(context)) {
        notify(alarm.notificationId, notification)
    }
}

fun showToast(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(
        context, text, duration
    ).show()
}

/**
 * Creates and immediately fires a fullscreen alarm notification for the Reminder.
 */
fun triggerNotification(context: Context, reminder: Reminder, alarm: Alarm) {
    val notification = getIntentForAlarmActivity(context, reminder.id).let {
        buildNotificationForAlarm(context, it, reminder)
    }
    with(NotificationManagerCompat.from(context)) {
        notify(alarm.notificationId, notification)
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getIntentForEditorActivity(context: Context, reminderId: String): PendingIntent =
    Intent(context, ReminderEditorActivity::class.java).run {
//        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            this,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getCreatedIntentForEditorActivity(context: Context, reminderId: String): PendingIntent? =
    Intent(context, ReminderEditorActivity::class.java).run {
//        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            this,
            PendingIntent.FLAG_NO_CREATE
        )
    }

fun getIntentForAlarmActivity(context: Context, reminderId: String): PendingIntent =
    createAlarmActivityIntent(context, reminderId, PendingIntent.FLAG_UPDATE_CURRENT)!!

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getCreatedIntentForAlarmActivity(context: Context, reminderId: String): PendingIntent? =
    createAlarmActivityIntent(context, reminderId, PendingIntent.FLAG_NO_CREATE)

private fun createAlarmActivityIntent(
    context: Context,
    reminderId: String,
    pendingIntentFlags: Int
): PendingIntent? =
    Intent(context, AlarmActivity::class.java).run {
//        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            this,
            pendingIntentFlags
        )
    }


@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun createIntentForAlarmAction(
    context: Context,
    action: String,
    reminderId: String
): PendingIntent =
    createAlarmActionIntent(context, action, reminderId, 0)!!

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getCreatedIntentForAlarmAction(
    context: Context,
    action: String,
    reminderId: String
): PendingIntent? =
    createAlarmActionIntent(context, action, reminderId, PendingIntent.FLAG_NO_CREATE)

private fun createAlarmActionIntent(
    context: Context,
    action: String,
    reminderId: String,
    pendingIntentFlags: Int
): PendingIntent? =
    when (action) {
        ACTION_ALARM_START -> Intent(context, AlarmStartReceiver::class.java).run {
            this.action = action
            putExtra(EXTRA_REMINDER_ID, reminderId)
            PendingIntent.getBroadcast(context, reminderId.hashCode(), this, pendingIntentFlags)
        }
        else -> Intent(context, AlarmActionReceiver::class.java).run {
            this.action = action
            putExtra(EXTRA_REMINDER_ID, reminderId)
            PendingIntent.getBroadcast(context, reminderId.hashCode(), this, pendingIntentFlags)
        }
    }


fun buildNotificationForAlarm(
    context: Context,
    intent: PendingIntent,
    reminder: Reminder,
): Notification =
    NotificationCompat.Builder(
        context,
        context.getString(R.string.alarm_notification_channel_id)
    ).run {
        setAutoCancel(true)
        setOngoing(true)
        setSmallIcon(R.drawable.ic_notification_alarm)
        setContentTitle("Reminder Alarm")

        val message = "Remember to do: \"${reminder.description}\""
        setContentText(message)
        setStyle(NotificationCompat.BigTextStyle().bigText(message))

        setPriority(NotificationCompat.PRIORITY_MAX)
        setCategory(NotificationCompat.CATEGORY_ALARM)
        setContentIntent(intent)
        setFullScreenIntent(intent, true)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            setLights(NOTIFICATION_LED_ARGB, NOTIFICATION_LED_ONMS, NOTIFICATION_LED_OFFMS)
        }

        if (!reminder.snoozeRules.neverRepeats) {
            addAction(
                R.drawable.ic_notification_snooze,
                context.getString(R.string.snooze),
                createIntentForAlarmAction(context, ACTION_ALARM_SNOOZE, reminder.id)
            )
        }
        addAction(
            R.drawable.ic_notification_skip,
            context.getString(R.string.skip),
            createIntentForAlarmAction(context, ACTION_ALARM_SKIP, reminder.id)
        )
        addAction(
            R.drawable.ic_notification_complete,
            context.getString(R.string.complete),
            createIntentForAlarmAction(context, ACTION_ALARM_COMPLETE, reminder.id)
        )

        build()
    }


@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun buildSnoozeNotification(context: Context, reminder: Reminder, alarm: Alarm): Notification =
    NotificationCompat.Builder(
        context,
        context.getString(R.string.alarm_notification_channel_id)
    ).run {
        setAutoCancel(true)
        setSmallIcon(R.drawable.ic_notification_snooze)
        setContentIntent(getIntentForEditorActivity(context, reminder.id))
        setContentTitle("Reminder Snoozed")

        val message =
            "Reminder for \"${reminder.description}\" was snoozed until ${alarm.triggerAt.getShortLocalTimeDisplay()}"
        setContentText(message)
        setStyle(NotificationCompat.BigTextStyle().bigText(message))
        setPriority(NotificationCompat.PRIORITY_DEFAULT)

        build()
    }


@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun buildSkipNotification(context: Context, reminder: Reminder): Notification =
    NotificationCompat.Builder(
        context,
        context.getString(R.string.alarm_notification_channel_id)
    ).run {
        setAutoCancel(true)
        setSmallIcon(R.drawable.ic_notification_snooze)
        setContentIntent(getIntentForEditorActivity(context, reminder.id))
        setContentTitle("Reminder Skipped")

        val message =
            "Reminder for \"${reminder.description}\" has been skipped (missed too many snoozes)"
        setContentText(message)
        setStyle(NotificationCompat.BigTextStyle().bigText(message))
        setPriority(NotificationCompat.PRIORITY_DEFAULT)

        build()
    }

