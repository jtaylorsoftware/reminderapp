package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * An AlarmReceiver that changes the status of a Reminder, adds history, and reschedules.
 */
abstract class AlarmStatusReceiver(private val newStatus: ReminderStatus, action: String):  AlarmReceiver(action){

    override fun doReceive(context: Context, intent: Intent) {
        Timber.d("AlarmStatusReceiver: $newStatus")

        val pendingResult = goAsync()
        coroutineScope.launch {
            val reminder = requireNotNull(reminderRepository.findById(reminderId))

            val history = History(
                reminderId = reminder.id,
                description = reminder.description,
                completionStatus = newStatus,
                completedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
            )
            historyRepository.save(history)

            cancelNotification(context, reminder.id)

            if (!reminder.isOneOff){
                Timber.d("$newStatus -> scheduling new reminder")
                scheduleAlarmForReminder(context, reminder, LocalDate.now().dayOfWeek)?.let{
                    Timber.d("Saving new alarm")
                    alarmRepository.save(it)
                } ?: Timber.d("got null reminder")
            } else {
                Timber.d("$newStatus -> changing one-off status")
                reminder.status = newStatus
                reminderRepository.save(reminder)
            }

            pendingResult.finish()
        }
    }
}