package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.service.AlarmService
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
    lateinit var alarmRepository: AlarmRepository

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
            reminderRepository.getReminderAndAlarm(reminderId)?.let { (reminder, _) ->
                AlarmService.start(context, reminder)
            } ?: run {
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
}