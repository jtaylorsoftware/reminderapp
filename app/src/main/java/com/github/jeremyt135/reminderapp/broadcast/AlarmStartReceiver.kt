package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.service.AlarmTimerManager
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * AlarmStartReceiver receives ACTION_ALARM_START and starts the AlarmService if possible.
 */
@AndroidEntryPoint
class AlarmStartReceiver : BroadcastReceiver() {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var timerManager: AlarmTimerManager

    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM_START) {
            Timber.w("AlarmStartReceiver called with incorrect action: ${intent.action}")
            return
        }

        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return scheduleNext(context)

        val pendingResult = goAsync()
        applicationScope.launch {
            reminderRepository.getReminderAndAlarm(reminderId)?.let { (reminder, alarm) ->
                alarm?.let {
                    // if the reminder has an alarm, start the timer and service

                    // Start timer
                    val started = timerManager.startTimer(applicationScope) {
                        // Snooze when timer ends uninterrupted
                        snoozeAlarm(context, reminder.id)
                    }
                    // Ensure that it started
                    if (!started) {
                        // the previous timer wasn't cleared, or there's other problems making timers
                        Timber.e("Could not create AlarmTimer for reminder ${reminder.id}")
                        scheduleNext(context)
                    } else {
                        // started, so create service
                        Timber.d("AlarmStartReceiver starting service")
                        AlarmService.start(context, reminder)
                    }
                } ?: run {
                    // alarm?.let was null
                    // schedule next because current reminder/alarm can't be displayed
                    Timber.e("Reminder had no alarm")
                    scheduleNext(context)
                }
            } ?: run {
                // getReminderAndAlarm?.let was null
                // had no data to work with, try to schedule next
                Timber.e("Could not get ReminderAndAlarm")
                scheduleNext(context)
            }
            pendingResult.finish()
        }
    }

    private fun scheduleNext(context: Context) {
        AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, "").let {
            WorkManager.getInstance(context).enqueue(it)
        }
    }

    private fun snoozeAlarm(context: Context, reminderId: String) {
        AlarmActionHandler.createForAction(ACTION_ALARM_SNOOZE, reminderId).let {
            WorkManager.getInstance(context).enqueue(it)
        }
    }
}