package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import kotlinx.coroutines.launch
import timber.log.Timber

class AlarmCompleteReceiver : AlarmReceiver(ACTION_ALARM_COMPLETE) {

    override fun doReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        coroutineScope.launch {
            val reminder = requireNotNull(reminderRepository.findById(reminderId))

            reminder.status = ReminderStatus.COMPLETED
            reminderRepository.save(reminder)
            Timber.d("Set reminder.status to COMPLETED and saved")

            cancelNotification(context, reminder.id)

            pendingResult.finish()
        }
    }
}