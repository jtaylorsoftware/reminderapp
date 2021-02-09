package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import timber.log.Timber
import java.time.LocalDate

/**
 * Receives android.intent.action.BOOT_COMPLETED and reschedules alarms that have been dropped.
 */
@AndroidEntryPoint
class AlarmBootScheduler : AlarmReceiver() {

    private val coroutineScope = MainScope() + CoroutineName("AlarmBootScheduler")

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action != "android.intent.action.BOOT_COMPLETED") {
            return
        }

        val pendingResult = goAsync()
        coroutineScope.launch {
            Timber.d("Clearing existing alarms")
            // Clear the table but do not cancel broadcasts because they should already be
            // cancelled by the system reboot
            alarmRepository.deleteAll()

            Timber.d("Rescheduling alarms")
            val reminders = reminderRepository.getAll().first()
            val today = LocalDate.now().dayOfWeek
            val jobs = reminders.map { reminder ->
                scheduleAlarmForReminder(context, reminder, today)?.let { alarm ->
                    return@map launch { alarmRepository.save(alarm) }
                }
            }
            jobs.forEach { it?.join() }
            pendingResult.finish()
        }
    }
}