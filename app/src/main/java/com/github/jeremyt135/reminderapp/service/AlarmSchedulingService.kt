package com.github.jeremyt135.reminderapp.service

import android.app.AlarmManager
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.data.domain.parcel.ReminderParcel
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import com.github.jeremyt135.reminderapp.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

/**
 * Service for either saving a new alarm or finding and scheduling the next alarm out of all locally saved alarms.
 */
@AndroidEntryPoint
class AlarmSchedulingService : Service() {
    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    @DefaultDispatcher
    @Inject
    lateinit var dispatcher: CoroutineDispatcher

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    private val mutex = Mutex()
    private val pendingScheduleNextCommands = ArrayDeque<Int>()
    private val startCommands = ArrayDeque<Int>()

    private var scheduleNextJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

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
                if (reminder == null){
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
            mutex.withLock { startCommands.addFirst(startId) }
            try {
                val allowSameDay = historyRepository.getLatestForReminder(reminder.id) != null

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
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                mutex.withLock {
                    startCommands.remove(startId)
                }
                startScheduleNext(this@AlarmSchedulingService)
            }
        }
    }

    private fun doScheduleNextAlarm(startId: Int) {
        applicationScope.launch(dispatcher) {
            // Enqueue self (in case waiting)
            mutex.withLock {
                pendingScheduleNextCommands.addFirst(startId)
                startCommands.addFirst(startId)
            }

            scheduleNextJob?.join()
            scheduleNextJob = launch {
                try {
                    scheduleNextAlarm(startId)
                } catch (e: Exception) {
                    Timber.e(e)
                } finally {
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
                }
            }
        }
    }

    private suspend fun scheduleNextAlarm(startId: Int) = withContext(dispatcher) {
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
    private suspend fun scheduleNextAlarmImpl(): Boolean = withContext(NonCancellable) {
        Timber.d("Scheduling next alarm...")

        val alarm = alarmRepository.getFirstByTimeAscending() ?: return@withContext false

        val previousScheduled = alarmRepository.getFirstScheduledAlarm()
        if (previousScheduled != alarm) {
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
                scheduleAlarmForReminder(this@AlarmSchedulingService, alarm.copy(scheduled = true), reminder)
            }
        } // Else the Alarm was the one already scheduled

        true
    }

    /**
     * Schedules a system AlarmClock for the Alarm and Reminder, using the Alarm.triggerAt value
     * for the AlarmClock triggerAt value.
     */
    private fun scheduleAlarmForReminder(
        context: Context,
        alarm: Alarm,
        reminder: Reminder
    ) {
        Timber.d("Scheduling $alarm for ${alarm.triggerAt.getShortLocalDateDisplay()} at ${alarm.triggerAt.getShortLocalTimeDisplay()}")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = createAlarmActionIntent(context, ACTION_ALARM_START, reminder.id)
        val showIntent = getIntentForEditorActivity(context, reminder.id)

        // Sanity check the time in case the system doesn't "Fire immediately" when it's in the past
        val time = if (alarm.triggerAt < currentLocalDateTime()) {
            currentLocalDateTime().plusMinutes(1)
        } else alarm.triggerAt

        AlarmManagerCompat.setAlarmClock(
            alarmManager, time.toEpochMilli(),
            showIntent,
            intent
        )
    }


    private fun doRescheduleAlarms(startId: Int) {
        applicationScope.launch(dispatcher) {
            mutex.withLock { startCommands.addFirst(startId) }
            try {
                rescheduleReminders()
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                // Notify this one finished
                stopSelfIfLast(startId)
            }
        }
    }

    private suspend fun rescheduleReminders() {
        rescheduleReminders(
            this,
            reminderRepository,
            alarmRepository,
            historyRepository
        )
    }

    /**
     * Non suspending way to both add a new startId to the queue and also try to stop it immediately.
     * Use in replacement of stopSelf() in onStartCommand when the start intent should not
     * be rejected.
     */
    private fun tryStopSelf(startId: Int) {
        applicationScope.launch(dispatcher) {
            withContext(NonCancellable) {
                mutex.withLock { startCommands.addFirst(startId) }
                mutex.withLock {
                    startCommands.remove(startId)
                    if (startCommands.lastOrNull() == null) {
                        stopSelf()
                    }
                }
            }
        }
    }

    /**
     * Normal alternative to stopSelf() for startCommands that have finished.
     */
    private suspend fun stopSelfIfLast(startId: Int) = withContext(NonCancellable) {
        mutex.withLock {
            startCommands.remove(startId)
            if (startCommands.lastOrNull() == null) {
                stopSelf()
            }
        }
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