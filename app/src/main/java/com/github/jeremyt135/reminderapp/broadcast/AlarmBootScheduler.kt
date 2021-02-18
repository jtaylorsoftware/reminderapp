package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate

/**
 * Receives android.intent.action.BOOT_COMPLETED and reschedules alarms that have been dropped.
 */
class AlarmBootScheduler : AlarmReceiver("android.intent.action.BOOT_COMPLETED") {

    override fun doReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        coroutineScope.launch {
            Timber.d("Rescheduling alarms")
            alarmRepository.deleteAll()
            val reminders = reminderRepository.getAll().first()
            val today = LocalDate.now().dayOfWeek
            val jobs = reminders.map { reminder ->
                scheduleAlarmForReminder(
                    context,
                    reminder,
                    today
                )?.let { alarm ->
                    return@map launch { alarmRepository.save(alarm) }
                }
            }

            jobs.forEach { it?.join() }
            pendingResult.finish()
        }
    }
}