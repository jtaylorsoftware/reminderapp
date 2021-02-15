package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class AlarmSkipReceiver : AlarmReceiver(ACTION_ALARM_SKIP) {

    override fun doReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        coroutineScope.launch {
            val reminder = requireNotNull(reminderRepository.findById(reminderId))
            reminder.status = ReminderStatus.SKIPPED
            reminderRepository.save(reminder)

            val history = History(
                reminderId = reminder.id,
                description = reminder.description,
                completionStatus = ReminderStatus.SKIPPED,
                completedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
            )
            historyRepository.save(history)
            Timber.d("Set reminder.status to SKIPPED and saved")

            cancelNotification(context, reminder.id)

            pendingResult.finish()
        }
    }
}