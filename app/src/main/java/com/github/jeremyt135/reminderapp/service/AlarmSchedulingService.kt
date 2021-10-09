package com.github.jeremyt135.reminderapp.service

import android.app.AlarmManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.data.domain.parcel.ReminderParcel
import com.github.jeremyt135.reminderapp.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

/**
 * Service for either saving a new alarm or finding and scheduling the next alarm out of all locally saved alarms.
 */
@AndroidEntryPoint
class AlarmSchedulingService : ReminderService() {
    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    override val binder: Binder = Binder()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val pendingScheduleNextCommands = ArrayDeque<Int>()

    private var scheduleNextJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("Created AlarmSchedulingService")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("AlarmSchedulingService destroyed")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Timber.d("Started for ${intent.action}")
        // Set up foreground notification
        startForeground(intent.action ?: "")
        when (intent.action) {
            ACTION_ALARM_SCHEDULE_NEXT -> {
                // If this is the first action for scheduling, start immediately
                if (pendingScheduleNextCommands.size == 0) {
                    doScheduleNextAlarm(startId)
                }
            }
            ACTION_ALARM_RESCHEDULE_ALL -> {
                doRescheduleAlarms(startId)
            }
            ACTION_ALARM_SAVE_NEW -> {
                val reminder = intent.getParcelableExtra<ReminderParcel>(EXTRA_REMINDER)
                if (reminder == null) {
                    tryStopSelf(startId)
                } else {
                    // Non-empty reminderId indicates a request to save a new alarm
                    saveNewAlarm(startId, reminder.data)
                }
            }
            else -> {
                // Reject incorrect actions
                tryStopSelf(startId)
            }
        }

        return START_REDELIVER_INTENT
    }

    private fun startForeground(action: String) {
        startForeground(FOREGROUND_NOTIFICATION_ID, createNotification(action))
    }

    private fun createNotification(action: String): Notification {
        val channel = getString(R.string.scheduling_notification_channel_id)
        val title = "Reminders scheduling"
        val appName = getString(R.string.app_name)
        val message = if (action == ACTION_ALARM_SCHEDULE_NEXT || action == ACTION_ALARM_SAVE_NEW) {
            "\"$appName\" is scheduling your next reminders."
        } else {
            "\"$appName\" is performing rescheduling of your reminders."
        }
        return NotificationCompat.Builder(this, channel).apply {
            setContentTitle(title)
            setSmallIcon(R.drawable.ic_scheduling_notification)
            setContentText(message)
            setPriority(NotificationCompat.PRIORITY_LOW)
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            setOngoing(true)
        }.build()
    }

    /**
     * Save a new alarm and follow up by adding this startId to the queue for scheduleNext.
     * Thus, this startId won't be considered done until it has both saved a new alarm and its
     * gone through the scheduleNext queue.
     */
    private fun saveNewAlarm(startId: Int, reminder: Reminder) {
        applicationScope.launch(dispatcher) {
            addStartCommand(startId)
            try {
                val history = historyRepository.getLatestForReminder(reminder.id)

                // Don't allow SINGLE to be saved again after having history for today
                if (reminder.schedulingRules.type == SchedulingType.SINGLE && history != null){
                    Timber.w("Can't save new alarm for type SINGLE reminder - has history entry today")
                    removeStartCommand(startId)
                    startScheduleNext(this@AlarmSchedulingService)
                    return@launch
                }

                val allowSameDay = history?.let {
                    it.completionStatus == ReminderStatus.PENDING
                } ?: true

                reminder.schedulingRules.getNextTimeForAlarm(
                    currentLocalDateTime(),
                    nextTimeOptions = SchedulingRules.NextTimeOptions(
                        allowTimeInPast = true,
                        allowSameDay = allowSameDay
                    )
                )?.let { time ->
                    val nextAlarm = Alarm(
                        reminderId = reminder.id,
                        triggerAt = time,
                        maxSnoozeRepeats = reminder.maxSnoozeRepeats,
                        snoozeDuration = reminder.snoozeDuration
                    )
                    // Delete old and save new
                    withContext(NonCancellable) {
                        alarmRepository.findByReminderId(reminder.id)?.let {
                            alarmRepository.delete(it)
                        }
                        alarmRepository.save(nextAlarm)
                    }
                }

                removeStartCommand(startId)
                startScheduleNext(this@AlarmSchedulingService)
            } catch (e: Exception) {
                Timber.e(e)
                withContext(NonCancellable) {
                    removeStartCommand(startId)
                    startScheduleNext(this@AlarmSchedulingService)
                }
                if (e is CancellationException) {
                    throw e
                }
            }
        }
    }

    private fun doScheduleNextAlarm(startId: Int) {
        applicationScope.launch(dispatcher) {
            // Enqueue self (in case waiting)
            mutex.withLock {
                pendingScheduleNextCommands.addFirst(startId)
            }
            addStartCommand(startId)

            scheduleNextJob?.join()
            scheduleNextJob = launch {
                try {
                    scheduleNextAlarm(startId)
                    cleanupScheduleNextCommand(startId)
                } catch (e: Exception) {
                    Timber.e(e)
                    cleanupScheduleNextCommand(startId)
                    if (e is CancellationException) {
                        throw e
                    }
                }
            }
        }
    }

    private suspend fun cleanupScheduleNextCommand(startId: Int) =
        withContext(NonCancellable) {
            // ID of the next scheduleNext to begin
            val nextId = mutex.withLock {
                // This one is finished, so remove it
                pendingScheduleNextCommands.remove(startId)
                // Get the next
                pendingScheduleNextCommands.removeLastOrNull()
            }

            // Start the next one if available
            if (nextId != null) {
                doScheduleNextAlarm(nextId)
            }

            // Notify this one finished
            stopSelfIfLast(startId)
        }


    private suspend fun scheduleNextAlarm(startId: Int) {
        val pendingCommands = mutex.withLock {
            // Indicate this one has been received
            pendingScheduleNextCommands.remove(startId)
            pendingScheduleNextCommands.size
        }

        // Perform scheduling actions
        if (pendingCommands != 0) {
            // If other commands are waiting, give this one a time limit
            withTimeout(TIME_PER_SCHEDULE_NEXT_MILLIS) {
                scheduleNextAlarmImpl()
            }
        } else {
            scheduleNextAlarmImpl()
        }
    }

    /**
     * Schedules the Alarm that is chronologically next to trigger today, if one exists.
     */
    private suspend fun scheduleNextAlarmImpl(): Boolean {
        Timber.d("Scheduling next alarm...")

        // The "next" alarm according to chronological order
        val alarm = alarmRepository.getFirstByTimeAscending() ?: return false

        // The alarm that is already saved with scheduled flag set
        val previousScheduled = alarmRepository.getFirstScheduledAlarm()

        // See if there's any obvious work to do by comparing the two instances (normally
        // the "scheduled" is also the first chronologically)
        if (previousScheduled != alarm) {
            withContext(NonCancellable) {
                // Alarm is not the one already scheduled - clear previous & set new
                previousScheduled?.let { prevAlarm ->
                    alarmRepository.clearScheduled(prevAlarm)
                    reminderRepository.findById(prevAlarm.reminderId)?.let { prevReminder ->
                        // Clear the pending intents
                        cancelAlarmIntentsForReminder(this@AlarmSchedulingService, prevReminder)
                    }
                }
                alarmRepository.setScheduled(alarm)
                reminderRepository.findById(alarm.reminderId)?.let { reminder ->
                    setAlarmClockForReminder(
                        this@AlarmSchedulingService,
                        alarm.copy(scheduled = true),
                        reminder
                    )
                }
            }
        } else {
            // Else the Alarm was the one already scheduled

            // Check that the triggerAt is not in the past for some reason
            if (previousScheduled.triggerAt <= currentLocalDateTime()) {
                // Is in the past, this has to be fixed by bumping it up, going to also
                // assume that because it's still saved that it is still valid but dead
                val updatedAlarm =
                    previousScheduled.copy(triggerAt = currentLocalDateTime().plusMinutes(1))
                withContext(NonCancellable) {
                    alarmRepository.save(updatedAlarm)
                    reminderRepository.findById(alarm.reminderId)?.let { reminder ->
                        cancelAlarmIntentsForReminder(this@AlarmSchedulingService, reminder)
                        setAlarmClockForReminder(
                            this@AlarmSchedulingService,
                            updatedAlarm,
                            reminder
                        )
                    }
                }
            } // else nothing to do
        }

        return true
    }

    /**
     * Sets a system AlarmClock for the Alarm and Reminder, using the Alarm.triggerAt value
     * for the AlarmClock triggerAt value.
     */
    private fun setAlarmClockForReminder(
        context: Context,
        alarm: Alarm,
        reminder: Reminder
    ) {
        Timber.d("Scheduling $alarm for ${alarm.triggerAt.getShortLocalDateDisplay()} at ${alarm.triggerAt.getShortLocalTimeDisplay()}")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = createAlarmActionIntent(context, ACTION_ALARM_START, reminder.id)
        val showIntent = getIntentForEditorActivity(context, reminder.id)

        // Sanity check the time in case it was still somehow saved in the past (system might also not
        // "Fire immediately" if it's in the past)
        val time = if (alarm.triggerAt < currentLocalDateTime()) {
            currentLocalDateTime().plusSeconds(10)
        } else alarm.triggerAt

        AlarmManagerCompat.setAlarmClock(
            alarmManager, time.toEpochMilli(),
            showIntent,
            intent
        )
    }


    private fun doRescheduleAlarms(startId: Int) {
        applicationScope.launch(dispatcher) {
            addStartCommand(startId)
            try {
                rescheduleReminders()
                stopSelfIfLast(startId)
            } catch (e: Exception) {
                Timber.e(e)
                stopSelfIfLast(startId)
                if (e is CancellationException) {
                    throw e
                }
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun rescheduleReminders(): Result<Int> =
        rescheduleReminders(
            this,
            reminderRepository,
            alarmRepository,
            historyRepository
        )

    inner class Binder : ReminderService.Binder() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ReminderService> getService(): T? = this@AlarmSchedulingService as? T
    }

    companion object {
        private const val TIME_PER_SCHEDULE_NEXT_MILLIS = 3 * 60 * 1000L // 3 minutes

        /**
         * Starts the service to save a new alarm for a reminder, and then schedule the next.
         */
        fun startScheduleNewAlarm(context: Context, reminder: Reminder) {
            Intent(context, AlarmSchedulingService::class.java).apply {
                action = ACTION_ALARM_SAVE_NEW
                putExtra(EXTRA_REMINDER, ReminderParcel(reminder))
            }.also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                } else {
                    context.startService(it)
                }
            }
        }

        /**
         * Starts the service to perform a scheduleNext action.
         */
        fun startScheduleNext(context: Context) {
            Intent(context, AlarmSchedulingService::class.java).apply {
                action = ACTION_ALARM_SCHEDULE_NEXT
            }.also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                } else {
                    context.startService(it)
                }
            }
        }

        /**
         * Starts the service to perform a rescheduleAll action.
         */
        fun startRescheduleAll(context: Context) {
            Intent(context, AlarmSchedulingService::class.java).apply {
                action = ACTION_ALARM_RESCHEDULE_ALL
            }.also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                } else {
                    context.startService(it)
                }
            }
        }

        private const val FOREGROUND_NOTIFICATION_ID = NotificationId.FIRST_ID
    }
}