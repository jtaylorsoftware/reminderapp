package com.github.jeremyt135.reminderapp.service

import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.github.jeremyt135.reminderapp.broadcast.ACTION_ALARM_COMPLETE
import com.github.jeremyt135.reminderapp.broadcast.ACTION_ALARM_SKIP
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.domain.SchedulingType
import com.github.jeremyt135.reminderapp.data.domain.parcel.ReminderParcel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Service for saving history entries for reminders.
 */
@AndroidEntryPoint
class AlarmHistoryService : ReminderService() {
    @Inject
    lateinit var historyRepository: HistoryRepository

    override val binder: Binder = Binder()

    override fun onCreate() {
        super.onCreate()
        Timber.d("Created AlarmHistoryService")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("AlarmHistoryService destroyed")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Timber.d("Started for ${intent.action}")
        val reminder = intent.getParcelableExtra<ReminderParcel>(EXTRA_REMINDER)
        if (reminder == null) {
            tryStopSelf(startId)
        } else {
            when (intent.action) {
                ACTION_ALARM_SKIP -> createHistoryEntryForReminder(
                    startId,
                    reminder.data,
                    ReminderStatus.SKIPPED
                )
                ACTION_ALARM_COMPLETE -> createHistoryEntryForReminder(
                    startId,
                    reminder.data,
                    ReminderStatus.COMPLETED
                )
                else -> tryStopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    /**
     * Saves a history entry with a completion status if one does not exist for today.
     */
    private fun createHistoryEntryForReminder(
        startId: Int,
        reminder: Reminder,
        status: ReminderStatus
    ) {
        applicationScope.launch(dispatcher) {
            addStartCommand(startId)
            launch {
                try {
                    val historyToday = historyRepository.getLatestForReminder(reminder.id)
                    if (historyToday == null) {
                        // Don't allow saving multiple history on the same day
                        val history = History(
                            reminderId = reminder.id,
                            description = reminder.description,
                            completionStatus = status,
                            // Save history with time to highest resolution to allow distinguishing between multiple
                            // history on same day, possibly within same minute
                            completedAt = Instant.now().atZone(ZoneId.systemDefault()).toInstant()
                                .toEpochMilli()
                        )
                        historyRepository.save(history)

                        if (reminder.schedulingRules.type == SchedulingType.SINGLE) {
                            // Single reminders are deleted after they have completed - this is a design decision
                            // to avoid confusion that a reminder is in the list but never rescheduled.
                            reminderRepository.delete(reminder)
                        }
                    }
                    stopSelfIfLast(startId)
                } catch (e: Exception) {
                    Timber.e(e)
                    withContext(NonCancellable) {
                        stopSelfIfLast(startId)
                    }
                    if (e is CancellationException) {
                        throw e
                    }
                }
            }
        }
    }

    inner class Binder : ReminderService.Binder() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ReminderService> getService(): T? = this@AlarmHistoryService as? T
    }

    companion object {
        /**
         * Starts the service to save a history entry for a reminder with the given status.
         */
        fun start(context: Context, reminder: Reminder, status: ReminderStatus) {
            val statusAction = when (status) {
                ReminderStatus.SKIPPED -> ACTION_ALARM_SKIP
                ReminderStatus.COMPLETED -> ACTION_ALARM_COMPLETE
                else -> throw IllegalArgumentException("Status must be SKIPPED or COMPLETED")
            }
            Intent(context, AlarmHistoryService::class.java).apply {
                action = statusAction
                putExtra(EXTRA_REMINDER, ReminderParcel(reminder))
            }.also {
                context.startService(it)
            }
        }
    }
}