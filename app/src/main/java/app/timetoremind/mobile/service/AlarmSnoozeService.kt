package app.timetoremind.mobile.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.timetoremind.mobile.broadcast.ACTION_ALARM_SNOOZE
import app.timetoremind.mobile.broadcast.EXTRA_REMINDER
import app.timetoremind.mobile.broadcast.showSkipNotification
import app.timetoremind.mobile.broadcast.showSnoozeNotification
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.ReminderStatus
import app.timetoremind.mobile.data.domain.SchedulingType
import app.timetoremind.mobile.data.domain.parcel.ReminderParcel
import app.timetoremind.mobile.util.NotificationId
import app.timetoremind.mobile.util.currentLocalDateTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Service for snoozing alarms.
 */
@AndroidEntryPoint
class AlarmSnoozeService : ReminderService() {
    @Inject
    lateinit var alarmRepository: AlarmRepository

    override val binder = Binder()

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
            val reminder = intent.getParcelableExtra<ReminderParcel>(EXTRA_REMINDER)
            if (reminder == null) {
                tryStopSelf(startId)
            } else {
                snoozeAlarmForReminder(startId, reminder.data)
            }
        } else {
            tryStopSelf(startId)
        }
        return START_REDELIVER_INTENT
    }

    private fun snoozeAlarmForReminder(startId: Int, reminder: Reminder) {
        applicationScope.launch(dispatcher) {
            addStartCommand(startId)
            try {
                val alarm = alarmRepository.findByReminderId(reminder.id)
                    ?: throw IllegalStateException("Alarm for reminder ${reminder.id} did not exist to snooze")

                cancelPreviousNotification(alarm.notificationId)
                alarmRepository.delete(alarm)

                if (
                    alarm.snoozeRules.neverRepeats ||
                    !alarm.snoozeRules.canSnooze(alarm.snoozeCount)
                ) {
                    // Can't snooze again, never snoozes or out of attempts
                    Timber.d("Not snoozing Alarm: beyond allowed limit")
                    // Save new alarm if applicable
                    val newAlarm = saveNewAlarm(reminder)

                    // Check if old reminder should be deleted
                    if (reminder.schedulingRules.type == SchedulingType.SINGLE) {
                        // Single reminders are deleted after they have completed - this is a design decision
                        // to avoid confusion that a reminder is in the list but never rescheduled.
                        reminderRepository.delete(reminder)
                    }

                    AlarmHistoryService.start(
                        this@AlarmSnoozeService,
                        reminder,
                        ReminderStatus.SKIPPED
                    )
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
                withContext(NonCancellable) {
                    removeStartCommand(startId)
                    AlarmSchedulingService.startScheduleNext(this@AlarmSnoozeService)
                }
                if (e is CancellationException) {
                    throw e
                }
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

    inner class Binder : ReminderService.Binder() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ReminderService> getService(): T? = this@AlarmSnoozeService as? T
    }

    companion object {
        /**
         * Starts the service to perform a snooze action.
         */
        fun start(context: Context, reminder: Reminder) {
            Intent(context, AlarmSnoozeService::class.java).apply {
                action = ACTION_ALARM_SNOOZE
                putExtra(EXTRA_REMINDER, ReminderParcel(reminder))
            }.also {
                context.startService(it)
            }
        }
    }
}