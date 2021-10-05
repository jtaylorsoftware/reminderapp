package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.service.AlarmSchedulingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * AlarmBootScheduler is responsible for rescheduling alarms in response to system events
 * indicating a restart or other changes that would invalidate or remove AlarmManager alarms.
 *
 * It accepts android.intent.action.BOOT_COMPLETED or android.intent.action.MY_PACKAGE_REPLACED
 */
@AndroidEntryPoint
class AlarmBootScheduler : BroadcastReceiver() {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    private val actions = arrayOf(
        // Results in AlarmManager alarms being cleared
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.MY_PACKAGE_REPLACED",
        // Requires rescheduling using new timezone
        "android.intent.action.TIMEZONE_CHANGED"
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null || intent.action !in actions) {
            Timber.w("AlarmBootScheduler called with incorrect action: ${intent.action}")
            return
        }

        AlarmSchedulingService.startRescheduleAll(context)
    }
}
