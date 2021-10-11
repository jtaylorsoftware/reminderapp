package app.timetoremind.mobile.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import app.timetoremind.mobile.R
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.di.AppCoroutineScope
import app.timetoremind.mobile.service.*
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

        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
            ?: return AlarmSchedulingService.startScheduleNext(context)

        val pendingResult = goAsync()
        applicationScope.launch {
            val reminderAndAlarm = reminderRepository.getReminderAndAlarm(reminderId)
            if (reminderAndAlarm?.alarm == null) {
                // had no data to work with, try to schedule next
                Timber.e("Could not get ReminderAndAlarm")
                AlarmSchedulingService.startScheduleNext(context)
                pendingResult.finish()
                return@launch
            }
            // Have alarm, start service

            // get preference for how long to delay until the alarm auto stops
            val millisPerSec = 1000
            val alarmDelay =
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(
                        context.getString(R.string.preference_key_alarm_delay),
                        AlarmTimer.DEFAULT_TIMER_DELAY_MILLI.toInt() / millisPerSec
                    ) * millisPerSec
            Timber.d("Read AlarmDelay as $alarmDelay ms")

            val (reminder, _) = reminderAndAlarm

            // Start timer
            val started = timerManager.startTimer(applicationScope, alarmDelay.toLong()) {
                // Snooze when timer ends uninterrupted
                AlarmSnoozeService.start(context, reminder)
            }
            // Ensure that it started
            if (!started) {
                // the previous timer wasn't cleared, or there's other problems making timers
                Timber.e("Could not create AlarmTimer for reminder ${reminder.id}")
                AlarmSchedulingService.startScheduleNext(context)
            } else {
                // started, so create service
                Timber.d("AlarmStartReceiver starting service")
                AlarmMediaService.start(context, reminder)
            }

            pendingResult.finish()
        }
    }
}