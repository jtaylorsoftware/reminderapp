package com.github.jeremyt135.reminderapp.broadcast

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.AlarmActivity
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

const val ACTION_ALARM_START = "com.github.jeremyt135.reminderapp.ACTION_ALARM_START"
const val ACTION_ALARM_SNOOZE = "com.github.jeremyt135.reminderapp.ACTION_ALARM_SNOOZE"
const val ACTION_ALARM_SKIP = "com.github.jeremyt135.reminderapp.ACTION_ALARM_SKIP"
const val ACTION_ALARM_COMPLETE = "com.github.jeremyt135.reminderapp.ACTION_ALARM_COMPLETE"

const val EXTRA_REMINDER_ID = "com.github.jeremyt135.reminderapp.EXTRA_REMINDER_ID"

const val SNOOZE_TIME_MIN: Long = 2L

/**
 * A 30 seconds long vibration pattern
 */
val VIBRATION_PATTERN: LongArray by lazy {
    val value = Array<Long>(31) { 1000 }.toLongArray()
    value[0] = 0
    value
}

/**
 * Schedules an alarm to be received by AlarmReceiver. Client should pass in a new Alarm that
 * has everything but its triggerAt field set.
 * Returns the alarm with its triggerAt updated or null if it could not be scheduled due to timing constraints.
 */
fun scheduleAlarmForReminder(
    context: Context,
    reminder: Reminder,
    today: DayOfWeek
): Alarm? = reminder.getNextTimeForAlarm(today)?.let {
    val alarm = Alarm(
        0,
        reminderId = reminder.id,
        triggerAt = it.toEpochMilli()
    )

    Timber.d("Scheduling next alarm for reminder ${reminder.id} at ${alarm.triggerAt}")

    scheduleAlarm(context, alarm)

    alarm
}

fun scheduleAlarm(context: Context, alarm: Alarm) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = getIntentForAlarmReceiver(context, alarm.reminderId)

    AlarmManagerCompat.setExactAndAllowWhileIdle(
        alarmManager, AlarmManager.RTC_WAKEUP,
        alarm.triggerAt, intent
    )
}

/**
 * Cancels previously scheduled broadcast PendingIntents associated with the given alarm.
 */
fun cancelAlarm(context: Context, alarm: Alarm) {
    if (alarm.id == 0L) {
        return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Cancel the alarm notification intent
    getCreatedIntentForAlarmReceiver(context, alarm.reminderId)?.let {
        alarmManager.cancel(it)
    }

    // Cancel the snooze intent
    getCreatedIntentForSnoozeReceiver(context, alarm.reminderId)?.let {
        alarmManager.cancel(it)
    }
}

/**
 * Cancels the notification associated with reminderId
 */
fun cancelNotification(context: Context, reminderId: Long) {
    with(NotificationManagerCompat.from(context)) {
        cancel(reminderId.toInt())
    }
    Timber.d("Cancelled current notification")
}

fun makeSnoozeToast(context: Context) {
    Toast.makeText(
        context, "Alarm snoozed until 2 minutes from now", Toast.LENGTH_SHORT
    ).show()
}

/**
 * Returns true if a reminder's alarm should be triggered because its triggerAt is the current time and
 * the reminder is not complete. If the reminder and alarm are not paired, returns false without
 * evaluating the time.
 */
fun alarmShouldTriggerNow(reminder: Reminder, alarm: Alarm): Boolean {
    if (reminder.id != alarm.reminderId) {
        return false
    }

    val today = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    val alarmDateTime = alarm.triggerAt.toLocalDateTime()

    return reminder.alarmCanBeSetToday(today.dayOfWeek) && today.isEqual(alarmDateTime)
}


/**
 * Creates and immediately fires a fullscreen alarm notification for the Reminder.
 */
fun triggerNotification(context: Context, reminder: Reminder) {
    val notification = Intent(context, AlarmActivity::class.java).run {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        PendingIntent.getActivity(context, 0, this, PendingIntent.FLAG_UPDATE_CURRENT)
    }.let {
        buildNotificationForAlarmIntent(context, it, reminder)
    }
    with(NotificationManagerCompat.from(context)) {
        notify(reminder.id.toInt(), notification)
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getIntentForAlarmReceiver(context: Context, reminderId: Long): PendingIntent =
    Intent(context, AlarmTriggerReceiver::class.java).run {
        flags = Intent.FLAG_RECEIVER_REPLACE_PENDING
        action = ACTION_ALARM_START
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            this,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getCreatedIntentForAlarmReceiver(context: Context, reminderId: Long): PendingIntent? =
    Intent(context, AlarmTriggerReceiver::class.java).run {
        flags = Intent.FLAG_RECEIVER_REPLACE_PENDING
        action = ACTION_ALARM_START
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            this,
            PendingIntent.FLAG_NO_CREATE
        )
    }

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getIntentForSkipReceiver(context: Context, reminderId: Long): PendingIntent =
    Intent(context, AlarmSkipReceiver::class.java).run {
        flags = Intent.FLAG_RECEIVER_REPLACE_PENDING
        action = ACTION_ALARM_SKIP
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            this,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getCreatedIntentForSkipReceiver(context: Context, reminderId: Long): PendingIntent? =
    Intent(context, AlarmSkipReceiver::class.java).run {
        flags = Intent.FLAG_RECEIVER_REPLACE_PENDING
        action = ACTION_ALARM_SKIP
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            this,
            PendingIntent.FLAG_NO_CREATE
        )
    }


@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getIntentForSnoozeReceiver(context: Context, reminderId: Long): PendingIntent =
    Intent(context, AlarmSnoozeReceiver::class.java).run {
        flags = Intent.FLAG_RECEIVER_REPLACE_PENDING
        action = ACTION_ALARM_SNOOZE
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            this,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getCreatedIntentForSnoozeReceiver(context: Context, reminderId: Long): PendingIntent? =
    Intent(context, AlarmSnoozeReceiver::class.java).run {
        flags = Intent.FLAG_RECEIVER_REPLACE_PENDING
        action = ACTION_ALARM_SNOOZE
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            this,
            PendingIntent.FLAG_NO_CREATE
        )
    }

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getIntentForCompleteReceiver(context: Context, reminderId: Long): PendingIntent =
    Intent(context, AlarmCompleteReceiver::class.java).run {
        flags = Intent.FLAG_RECEIVER_REPLACE_PENDING
        action = ACTION_ALARM_COMPLETE
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            this,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
fun getCreatedIntentForCompleteReceiver(context: Context, reminderId: Long): PendingIntent? =
    Intent(context, AlarmCompleteReceiver::class.java).run {
        flags = Intent.FLAG_RECEIVER_REPLACE_PENDING
        action = ACTION_ALARM_COMPLETE
        putExtra(EXTRA_REMINDER_ID, reminderId)
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            this,
            PendingIntent.FLAG_NO_CREATE
        )
    }


@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun buildNotificationForAlarmIntent(
    context: Context,
    intent: PendingIntent,
    reminder: Reminder,
): Notification {
    val builder = NotificationCompat.Builder(
        context,
        context.getString(R.string.alarm_notification_channel_id)
    )

    builder
        .setAutoCancel(true)
        .setOngoing(true)
        .setSmallIcon(R.drawable.ic_notification_alarm)
        .setContentTitle("Reminder Notification")
        .setContentText("Remember to do: ${reminder.description}")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setContentIntent(intent)
        .setFullScreenIntent(intent, true)


    val snoozeIntent = getIntentForSnoozeReceiver(context, reminder.id)
    val skipIntent = getIntentForSkipReceiver(context, reminder.id)
    val completeIntent = getIntentForCompleteReceiver(context, reminder.id)

    builder
        .addAction(
            R.drawable.ic_notification_snooze,
            context.getString(R.string.action_alarm_snooze_name),
            snoozeIntent
        )
        .addAction(
            R.drawable.ic_notification_skip,
            context.getString(R.string.action_alarm_skip_name),
            skipIntent
        )
        .addAction(
            R.drawable.ic_notification_complete,
            context.getString(R.string.action_alarm_complete_name),
            completeIntent
        )

    return builder.build()
}