package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Receives android.intent.action.BOOT_COMPLETED and reschedules alarms that have been dropped.
 */
class AlarmBootScheduler : AlarmReceiver("android.intent.action.BOOT_COMPLETED") {

    override fun doReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        coroutineScope.launch {
            Timber.d("Rescheduling alarms")

           scheduleNextAlarm(context, alarmRepository, reminderRepository)
        }
        pendingResult.finish()
    }
}