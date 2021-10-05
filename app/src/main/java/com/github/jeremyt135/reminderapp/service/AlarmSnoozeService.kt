package com.github.jeremyt135.reminderapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.domain.SchedulingType
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import com.github.jeremyt135.reminderapp.util.NotificationId
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject


/**
 * Service for snoozing alarms.
 */
@AndroidEntryPoint
class AlarmSnoozeService : Service() {
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

    private val mutex = Mutex()
    private val startCommands = ArrayDeque<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("Created AlarmSnoozeService")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("AlarmSnoozeService destroyed")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Timber.d("Started for ${intent.action}")
        if (intent.action == ACTION_ALARM_SNOOZE) {
            val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: ""
            if (reminderId.isEmpty()) {
                tryStopSelf(startId)
            } else {
                snoozeAlarmForReminder(startId, reminderId)
            }
        } else {
            tryStopSelf(startId)
        }
        return START_REDELIVER_INTENT
    }

    private fun snoozeAlarmForReminder(startId: Int, reminderId: String) {
        applicationScope.launch(dispatcher) {
            mutex.withLock { startCommands.addFirst(startId) }
            try {
                val reminder = reminderRepository.findById(reminderId)
                    ?: throw IllegalArgumentException("Reminder did not exist with id $reminderId")
                val alarm = alarmRepository.findByReminderId(reminder.id)
                    ?: throw IllegalStateException("Alarm for reminder $reminderId did not exist to snooze")

                cancelPreviousNotification(alarm.notificationId)
                alarmRepository.delete(alarm)

                if (
                    alarm.snoozeRules.neverRepeats ||
                    !alarm.snoozeRules.canSnooze(alarm.snoozeCount)
                ) {
                    // Can't snooze again, never snoozes or out of attempts
                    Timber.d("Not snoozing Alarm: beyond allowed limit")
                    val newAlarm = saveNewAlarm(reminder)
                    AlarmHistoryService.start(this@AlarmSnoozeService, reminder, ReminderStatus.SKIPPED)
                    showSkipNotification(reminder, newAlarm ?: alarm)
                } else {
                    // Determine next triggerAt for a snoozing alarm
                    val nextAlarm = alarm.copy(
                        id = 0,
                        scheduled = null,
                        notificationId = NotificationId.next,
                        snoozeCount = alarm.snoozeCount + 1,

                        // Always save the snooze as an offset from the current time to avoid
                        // this alarm getting backed up behind others.
                        triggerAt = currentLocalDateTime().plusMinutes(alarm.snoozeDuration),
                    )

                    // Save next alarm
                    nextAlarm.id = alarmRepository.save(nextAlarm)
                    Timber.d("Saved next alarm: $nextAlarm")

                    showSnoozeNotification(reminder, nextAlarm)
                }

                AlarmSchedulingService.startScheduleNext(this@AlarmSnoozeService)
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                // Notify this one finished
                stopSelfIfLast(startId)
            }
        }
    }

    /**
     * Saves a new alarm for a Reminder.
     * @return New alarm if one could be saved
     */
    private suspend fun saveNewAlarm(reminder: Reminder): Alarm? {
        // Try to save a new alarm, if reminder's type is repeating
        return if (reminder.schedulingRules.type != SchedulingType.SINGLE) {
            // Get next time for alarm, save if there is a next time
            reminder.schedulingRules.getNextTimeForAlarm(currentLocalDateTime())
                ?.let { nextDateTime ->
                    val alarm = Alarm(
                        reminderId = reminder.id,
                        triggerAt = nextDateTime,
                        maxSnoozeRepeats = reminder.maxSnoozeRepeats,
                        snoozeDuration = reminder.snoozeDuration
                    )
                    alarmRepository.save(alarm)
                    alarm
                }
        } else {
            // Single reminders are deleted after they have completed - this is a design decision
            // to avoid confusion that a reminder is in the list but never rescheduled.
            reminderRepository.delete(reminder)
            null
        }
    }

    private fun cancelPreviousNotification(notificationId: Int) {
        Timber.d("Cancelling previous notification: $notificationId")
        NotificationManagerCompat.from(this).cancel(notificationId)
    }

    private fun showSnoozeNotification(reminder: Reminder, alarm: Alarm) {
        Timber.d("Creating snooze notification: ${alarm.notificationId}")
        showSnoozeNotification(this, reminder, alarm)
    }

    private fun showSkipNotification(reminder: Reminder, alarm: Alarm) {
        Timber.d("Creating skip notification: ${alarm.notificationId}")
        showSkipNotification(this, reminder, alarm)
    }

    /**
     * Non suspending way to both add a new startId to the queue and also try to stop it immediately.
     * Use in replacement of stopSelf() in onStartCommand when the start intent should not
     * be rejected.
     */
    private fun tryStopSelf(startId: Int) {
        applicationScope.launch {
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
        /**
         * Starts the service to perform a snooze action.
         */
        fun start(context: Context, reminder: Reminder) {
            Intent(context, AlarmSnoozeService::class.java).apply {
                action = ACTION_ALARM_SNOOZE
                putExtra(EXTRA_REMINDER_ID, reminder.id)
            }.also {
                context.startService(it)
            }
        }
    }
}