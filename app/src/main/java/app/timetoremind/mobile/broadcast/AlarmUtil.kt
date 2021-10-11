package app.timetoremind.mobile.broadcast

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.timetoremind.mobile.R
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.*
import app.timetoremind.mobile.screens.alarm.AlarmActivity
import app.timetoremind.mobile.screens.reminder.editor.ReminderEditorActivity
import app.timetoremind.mobile.service.AlarmSchedulingService
import app.timetoremind.mobile.util.currentLocalDateTime
import app.timetoremind.mobile.util.getShortLocalTimeDisplay
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Action to schedule the next Alarm
 */
const val ACTION_ALARM_SCHEDULE_NEXT =
    "app.timetoremind.mobile.ACTION_ALARM_SCHEDULE_NEXT"

/**
 * Action to reschedule all Alarms
 */
const val ACTION_ALARM_RESCHEDULE_ALL =
    "app.timetoremind.mobile.ACTION_ALARM_RESCHEDULE_ALL"

/**
 * Action to save a new alarm
 */
const val ACTION_ALARM_SAVE_NEW =
    "app.timetoremind.mobile.ACTION_ALARM_SAVE_NEW"

/**
 * Action to start AlarmService
 */
const val ACTION_ALARM_START = "app.timetoremind.mobile.ACTION_ALARM_START"

/**
 * Action to request AlarmService stops
 */
const val ACTION_ALARM_STOP = "app.timetoremind.mobile.ACTION_ALARM_STOP"

/**
 * Action to snooze an Alarm
 */
const val ACTION_ALARM_SNOOZE = "app.timetoremind.mobile.ACTION_ALARM_SNOOZE"

/**
 * Action to mark an Alarm as ReminderStatus.SKIPPED
 */
const val ACTION_ALARM_SKIP = "app.timetoremind.mobile.ACTION_ALARM_SKIP"

/**
 * Action to mark an Alarm as ReminderStatus.COMPLETED
 */
const val ACTION_ALARM_COMPLETE = "app.timetoremind.mobile.ACTION_ALARM_COMPLETE"

/**
 * String: The name of the Alarm action being taken (used in workers or where there is no Intent)
 */
const val EXTRA_ALARM_ACTION_NAME = "app.timetoremind.mobile.EXTRA_ALARM_ACTION_NAME"

/**
 * Reminder: parcelized Reminder
 */
const val EXTRA_REMINDER = "app.timetoremind.mobile.EXTRA_REMINDER"

/**
 * String: The UUID of the Reminder
 */
const val EXTRA_REMINDER_ID = "app.timetoremind.mobile.EXTRA_REMINDER_ID"

/**
 * String: The selected start date from the Calendar.
 */
const val EXTRA_REMINDER_START_DATE = "app.timetoremind.mobile.EXTRA_REMINDER_START_DATE"

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
): Result<Int> {
    val result = try {
        supervisorScope {
            Timber.d("Rescheduling all reminders and their alarms...")

            val alarms = reminderRepository.getAllWithAlarms().map { reminderAndAlarm ->
                async {
                    try {
                        val (reminder, alarm) = reminderAndAlarm
                        val history = historyRepository.getLatestForReminder(reminder.id)
                        val newAlarm = refreshAlarm(reminder, alarm, history)

                        withContext(NonCancellable) {
                            if (alarm != null && newAlarm == null) {
                                // Alarm is invalid and also can't reschedule
                                alarmRepository.delete(alarm)
                            }
                            if (newAlarm != null && alarm != newAlarm) {
                                newAlarm.id = alarmRepository.save(newAlarm)
                            }
                        }

                        return@async newAlarm ?: alarm
                    } catch (e: Exception) {
                        Timber.e("rescheduleReminders: child exception thrown:\n${e.stackTraceToString()}")
                        if (e is CancellationException) {
                            throw e
                        }
                    }
                    return@async null
                }
            }.awaitAll()

            Result.success(alarms.count { it != null && it.id != 0L })
        }
    } catch (e: Exception) {
        Timber.e("rescheduleReminders: parent exception thrown:\n${e.stackTraceToString()}")
        if (e is CancellationException) {
            throw e
        }
        Result.failure(e)
    }
    Timber.d("Saved new alarms, starting action for SCHEDULE_NEXT...")

    AlarmSchedulingService.startScheduleNext(context)
    return result
}

/**
 * "Refreshes" an alarm based on its reminder and today's history entry.
 * @param reminder The reminder associated with the alarm, not null
 * @param alarm The current alarm associated with the reminder
 * @param history The latest history entry today with the reminder
 * @return A new Alarm instance if the alarm no longer satisfies its constraints,
 * and a new one could be created.
 * If the current alarm still satisfies its constraints, returns the parameter alarm.
 * If null is returned, this indicates that the previous alarm is invalid and there could
 * be no new one saved here.
 */
fun refreshAlarm(reminder: Reminder, alarm: Alarm?, history: History?): Alarm? {
    return if (alarm != null) {
        // Have an alarm for it already
        val timeNow = currentLocalDateTime()
        if (alarm.triggerAt < timeNow) {
            // In past or same time
            if (history != null && history.completionStatus != ReminderStatus.PENDING) {
                // Has history for today, so it's been completed today and this alarm is invalid
                null
            } else {
                // No history today & in past, save new time but don't touch snooze settings
                val newTime = currentLocalDateTime().plusMinutes(1)
                alarm.copy(triggerAt = newTime, scheduled = null)
            }
        } else if (alarm.triggerAt > timeNow) {
            // In future, don't need to do anything
            alarm.copy(scheduled = null)
        } else {
            // TriggerAt is right now
            when {
                alarm.scheduled == true -> {
                    // Alarm might be firing, don't touch anything
                    alarm
                }
                history == null -> {
                    // Bump up to next minute so it doesn't go into the past
                    val newTime = currentLocalDateTime().plusMinutes(1)
                    alarm.copy(triggerAt = newTime, scheduled = null)
                }
                history.completionStatus != ReminderStatus.PENDING -> {
                    null
                }
                else -> {
                    alarm.copy(scheduled = null)
                }
            }
        }
    } else {
        // No alarm exists (should not be the usual case), make new one
        if (history != null && history.completionStatus != ReminderStatus.PENDING) {
            // Already completed today
            if (reminder.schedulingRules.type == SchedulingType.SINGLE) {
                // can't do a one-off more than once
                null
            } else {
                // repeating can go off again but on a different date
                reminder.schedulingRules.getNextTimeForAlarm(
                    currentLocalDateTime(),
                    nextTimeOptions = SchedulingRules.NextTimeOptions(allowSameDay = false)
                )?.let { nextTime ->
                    Alarm(
                        reminderId = reminder.id,
                        triggerAt = nextTime,
                        maxSnoozeRepeats = reminder.maxSnoozeRepeats,
                        snoozeDuration = reminder.snoozeDuration
                    )
                }
            }
        } else {
            // Did not complete today yet
            reminder.schedulingRules.getNextTimeForAlarm(
                currentLocalDateTime(),
                nextTimeOptions = SchedulingRules.NextTimeOptions.ALLOW_ALL
            )?.let { scheduledTime ->
                val time = if (scheduledTime < currentLocalDateTime()) {
                    currentLocalDateTime().plusMinutes(1)
                } else {
                    scheduledTime
                }
                Alarm(
                    reminderId = reminder.id,
                    triggerAt = time,
                    maxSnoozeRepeats = reminder.maxSnoozeRepeats,
                    snoozeDuration = reminder.snoozeDuration
                )
            }
        }
    }
}

/**
 * Cancels previously scheduled PendingIntents associated with the given alarm.
 */
fun cancelAlarmIntentsForReminder(context: Context, reminder: Reminder) {
    if (reminder.id.isEmpty()) {
        return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Cancel the alarm start broadcast intent
    getCreatedAlarmStartIntent(context, ACTION_ALARM_START, reminder.id)?.let {
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

fun getIntentForEditorActivity(context: Context, reminderId: String): PendingIntent =
    Intent(context, ReminderEditorActivity::class.java).run {
        flags =
            (Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            this,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
    }

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getCreatedIntentForEditorActivity(context: Context, reminderId: String): PendingIntent? =
    Intent(context, ReminderEditorActivity::class.java).run {
        flags =
            (Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            this,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
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
        flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            this,
            pendingIntentFlags
        )
    }


fun createAlarmActionIntent(
    context: Context,
    action: String,
    reminderId: String
): PendingIntent = createAlarmActionIntent(context, action, reminderId, 0)!!

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getCreatedAlarmStartIntent(
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
): PendingIntent? {
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_IMMUTABLE or pendingIntentFlags
    } else {
        pendingIntentFlags
    }
    return when (action) {
        ACTION_ALARM_START -> Intent(context, AlarmStartReceiver::class.java).run {
            this.action = action
            putExtra(EXTRA_REMINDER_ID, reminderId)
            PendingIntent.getBroadcast(
                context,
                reminderId.hashCode(),
                this,
                flags
            )
        }
        else -> Intent(context, AlarmActionReceiver::class.java).run {
            this.action = action
            putExtra(EXTRA_REMINDER_ID, reminderId)
            PendingIntent.getBroadcast(
                context,
                reminderId.hashCode(),
                this,
                flags
            )
        }
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
                createAlarmActionIntent(context, ACTION_ALARM_SNOOZE, reminder.id)
            )
        }
        addAction(
            R.drawable.ic_notification_skip,
            context.getString(R.string.skip),
            createAlarmActionIntent(context, ACTION_ALARM_SKIP, reminder.id)
        )
        addAction(
            R.drawable.ic_notification_complete,
            context.getString(R.string.complete),
            createAlarmActionIntent(context, ACTION_ALARM_COMPLETE, reminder.id)
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

