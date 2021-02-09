package com.github.jeremyt135.reminderapp.broadcast

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.AlarmActivity
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.local.Alarm
import org.jetbrains.annotations.TestOnly
import timber.log.Timber
import java.time.*
import java.time.temporal.ChronoUnit

/**
 * A 30 seconds long vibration pattern
 */
val VIBRATION_PATTERN: LongArray by lazy {
    val value = Array<Long>(31) { 1000 }.toLongArray()
    value[0] = 0
    value
}

/**
 * Schedules an alarm to be received by AlarmReceiver. Returns the scheduled alarm or null if
 * it could not be scheduled due to timing constraints.
 */
fun scheduleAlarmForReminder(
    context: Context,
    reminder: Reminder,
    today: DayOfWeek
): Alarm? = reminder.getNextTimeForAlarm(today)?.let {
    val alarm = Alarm(
        reminderId = reminder.id,
        triggerAt = it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    Timber.d("Scheduling next alarm for reminder ${reminder.id} at ${alarm.triggerAt}")

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = getIntentForAlarmReceiver(context, alarm)

    AlarmManagerCompat.setExactAndAllowWhileIdle(
        alarmManager, AlarmManager.RTC_WAKEUP,
        alarm.triggerAt, intent
    )

    alarm
}

@TestOnly
fun getIntentForAlarmReceiver(context: Context, alarm: Alarm): PendingIntent =
    Intent(context, AlarmTriggerReceiver::class.java).run {
        flags = Intent.FLAG_RECEIVER_REPLACE_PENDING
        action = context.getString(R.string.action_alarm_start)
        putExtra("alarm", alarm)
        PendingIntent.getBroadcast(
            context,
            alarm.reminderId.toInt(),
            this,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

@TestOnly
fun getIntentForAlarmReceiverTest(context: Context, alarm: Alarm): PendingIntent? =
    Intent(context, AlarmTriggerReceiver::class.java).run {
        flags = Intent.FLAG_RECEIVER_REPLACE_PENDING
        action = context.getString(R.string.action_alarm_start)
        putExtra("alarm", alarm)
        PendingIntent.getBroadcast(
            context,
            alarm.reminderId.toInt(),
            this,
            PendingIntent.FLAG_NO_CREATE
        )
    }


/**
 * Cancels a previously scheduled alarm.
 */
fun cancelAlarm(context: Context, alarm: Alarm) {
    if (alarm.id == 0L) {
        return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    alarmManager.cancel(
        getIntentForAlarmReceiver(context, alarm)
    )
}

/**
 * Returns true if a reminder's alarm should be triggered because its time is in the future and
 * the reminder is not complete. If the reminder and alarm are not paired, returns false.
 */
fun alarmShouldTriggerNow(reminder: Reminder, alarm: Alarm): Boolean {
    if (reminder.id != alarm.reminderId) {
        return false
    }

    val today = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    val alarmDateTime =
        Instant.ofEpochMilli(alarm.triggerAt).atZone(ZoneId.systemDefault()).toLocalDateTime()

    return reminder.alarmCanBeSetToday(today.dayOfWeek) && today.isEqual(alarmDateTime)
}


/**
 * Creates and immediately fires a fullscreen alarm notification for the Reminder.
 */
fun triggerNotification(context: Context, alarm: Alarm, reminder: Reminder) {
    val notification = Intent(context, AlarmActivity::class.java).run {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        PendingIntent.getActivity(context, 0, this, PendingIntent.FLAG_UPDATE_CURRENT)
    }.let {
        buildNotificationForAlarmIntent(context, it, reminder)
    }
    with(NotificationManagerCompat.from(context)) {
        notify(alarm.id.toInt(), notification)
    }
}

@TestOnly
fun buildNotificationForAlarmIntent(
    context: Context,
    intent: PendingIntent,
    reminder: Reminder
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

//    if (reminder.vibrate) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
//            builder.setVibrate(VIBRATION_PATTERN)
//        }
//    }

    return builder.build()
}