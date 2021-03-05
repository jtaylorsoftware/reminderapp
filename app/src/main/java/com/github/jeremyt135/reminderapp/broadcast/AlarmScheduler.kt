package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * AlarmScheduler removes old alarms, resets scheduled alarms, and schedules the next
 * Alarm to trigger.
 *
 * It accepts android.intent.action.BOOT_COMPLETED or android.intent.action.MY_PACKAGE_REPLACED
 */
class AlarmScheduler : AlarmReceiver(
    "android.intent.action.BOOT_COMPLETED",
    "android.intent.action.MY_PACKAGE_REPLACED"
) {
    override fun doReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        applicationScope.launch {
            Timber.d("Rescheduling alarms")

            // Clean up dangling Alarms
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
            alarmRepository.deleteAllInPast(now.toEpochMilli())
            alarmRepository.clearAllScheduled()

            // Schedule starting from when this boot completed (now)
            scheduleNextAlarm(context, alarmRepository, reminderRepository, now)
        }
        pendingResult.finish()
    }
}