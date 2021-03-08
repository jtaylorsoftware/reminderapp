package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import timber.log.Timber

class AlarmActionReceiver : BroadcastReceiver() {
    private val actions = arrayOf(
        ACTION_ALARM_COMPLETE,
        ACTION_ALARM_SKIP,
        ACTION_ALARM_SNOOZE,
        ACTION_ALARM_SCHEDULE_NEXT,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null || intent.action !in actions) {
            Timber.w("AlarmActionReceiver called with incorrect action: ${intent.action}")
            return
        }

        stopAlarmService(context)

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0)
        createWorkerForAction(context, intent.action!!, reminderId)
    }

    private fun stopAlarmService(context: Context) {
        Timber.d("Requesting AlarmService stops")
        AlarmService.requestStop(context)
    }

    private fun createWorkerForAction(context: Context, action: String, reminderId: Long) {
        Timber.w("Starting work for $action")
        val worker = AlarmActionHandler.createForAction(action, reminderId)
        WorkManager.getInstance(context).enqueue(worker)
    }
}