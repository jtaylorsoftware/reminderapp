package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import kotlinx.coroutines.launch
import timber.log.Timber

class AlarmSnoozeReceiver : AlarmReceiver(ACTION_ALARM_SNOOZE) {

    override fun doReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        coroutineScope.launch {
            val alarm = requireNotNull(reminderRepository.getReminderAndAlarm(reminderId)).alarm

            snoozeAlarm(context, alarm)

            makeSnoozeToast(context)
            cancelNotification(context, alarm.reminderId)

            pendingResult.finish()
        }
    }

    private suspend fun snoozeAlarm(context: Context, alarm: Alarm) {
        // Cancel previous alarms
        cancelAlarm(context, alarm)

        // Determine next triggerAt
        val nextAlarm = alarm.copy(
            triggerAt = alarm.triggerAt.toLocalDateTime().plusMinutes(SNOOZE_TIME_MIN)
                .toEpochMilli()
        )
        // Save changes
        alarmRepository.save(nextAlarm)

        // Schedule new alarm
        scheduleAlarm(context, nextAlarm)

        Timber.d("Snoozed and saved next alarm")
    }
}

