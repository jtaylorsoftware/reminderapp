package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.util.currentEpochMilli
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * AlarmScheduler removes old alarms, resets scheduled alarms, and schedules the next
 * Alarm to trigger.
 *
 * It accepts android.intent.action.BOOT_COMPLETED or android.intent.action.MY_PACKAGE_REPLACED
 */
@AndroidEntryPoint
class AlarmScheduler : BroadcastReceiver() {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    private val actions = arrayOf(
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.MY_PACKAGE_REPLACED"
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null || intent.action !in actions) {
            Timber.w("AlarmActionReceiver called with incorrect action: ${intent.action}")
            return
        }

        val pendingResult = goAsync()
        applicationScope.launch {
            Timber.d("Rescheduling alarms")

            // Clean up dangling Alarms
            alarmRepository.deleteAllInPast(currentEpochMilli())
            alarmRepository.clearAllScheduled()

            val worker = AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, 0)
            WorkManager.getInstance(context).enqueue(worker)
            pendingResult.finish()
        }
    }
}