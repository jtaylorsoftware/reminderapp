package com.github.jeremyt135.reminderapp.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.os.*
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.github.jeremyt135.BuildConfig
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.util.EventFlow
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class ReminderAlarmManager : Service() {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    /**
     * Scope for doing Room persistence & alarm scheduling that must not be cancelled on Service
     * being destroyed
     */
    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    /**
     * Commands stored by ReminderId
     */
    private val commands = mutableMapOf<Long, Command>()

    private var activeBindings = 0

    override fun onBind(intent: Intent): IBinder {
        activeBindings += 1

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
        // Return an appropriate binder if reminderId != 0, otherwise null to reject bind
        return commands[reminderId]?.let {
            LocalBinderImpl(it)
        } ?: run {
            Timber.w("Service onBind called without matching reminder")
            // Return a default Binder to clients that bind with invalid parameters.
            // Mostly used for testing.
            Binder()
        }
    }

    override fun onUnbind(intent: Intent): Boolean {
        activeBindings -= 1
        return false
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("Service created")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Timber.d("Service onStart called (startId:$startId)")
        val action = intent.action
        if (action != null) {
            Timber.d("onStart action: $action")
            handleAction(intent, action, startId)
        } else {
            Timber.w("onStartCommand called with null action in Intent")
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val vibrator = this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()
        Timber.d("Service destroyed")
    }

    private fun handleAction(intent: Intent, action: String, startId: Int) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)

        // Do not handle command at all if no reminderId is given
        if (reminderId == 0L) {
            Timber.e("handleCommand (startId: $startId) received 0 for EXTRA_REMINDER_ID")
            stopSelf(startId)
            return
        }

        if (action == ACTION_ALARM_SET_NOTIF) {
            val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
            val description = intent.getStringExtra(EXTRA_REMINDER_DESCRIPTION) ?: ""
            val vibrate = intent.getBooleanExtra(EXTRA_REMINDER_VIBRATE, false)
            handleStart(
                Reminder(id = reminderId, description = description, vibrate = vibrate),
                alarmId,
                startId
            )
        } else {
            commands[reminderId]?.let {
                if (it.handled) {
                    return
                }

                applicationScope.launch {
                    handleActionWithCommand(action, it, startId)
                }
            } ?: run {
                if (BuildConfig.DEBUG) {
                    val msg = "handleAction: could not handle non-start command, command == null"
                    Timber.e(msg)
                    showToast(this, msg, Toast.LENGTH_LONG)
                }
            }
        }
    }

    // Calls setNotification and creates a Command
    private fun handleStart(reminder: Reminder, alarmId: Long, startId: Int) {
        // Set notification using extras, and in main thread w/o dispatch, so it can get to foreground ASAP
        bringToForeground(
            reminder,
            alarmId
        )

        // Store a Command with the startId from the current onStartCommand so
        // it can be modified later

        val command = Command(
            reminderId = reminder.id,
            notificationAlarmId = alarmId,
            notificationStartId = startId,
        )
        Timber.d("Created command: $command")
        commands[reminder.id] = command
        command.startSnoozeTimeout(applicationScope) {
            handleActionWithCommand(ACTION_ALARM_SNOOZE, it, null)
        }
    }

    private fun bringToForeground(reminder: Reminder, alarmId: Long) {
        val vibrator =
            this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (reminder.vibrate && vibrator.hasVibrator()) {
            vibrator.start()
        }
        startForeground(
            alarmId.toInt(),
            createForegroundNotification(reminder)
        )
    }

    private fun createForegroundNotification(reminder: Reminder): Notification {
        return buildNotificationForAlarm(
            this,
            getIntentForAlarmActivity(this, reminder.id), reminder
        )
    }

    private suspend fun handleActionWithCommand(action: String, command: Command, startId: Int?) {
        Timber.d("Doing action $action for command $command")
        reminderRepository.getReminderAndAlarm(command.reminderId)
            ?.let { (reminder, alarm) ->
                command.handled = true

                when (action) {
                    ACTION_ALARM_SNOOZE -> handleSnooze(reminder, alarm)
                    ACTION_ALARM_SKIP -> handleSkip(reminder, alarm)
                    ACTION_ALARM_COMPLETE -> handleCompleted(reminder, alarm)
                }

                scheduleNextAlarm(
                    this@ReminderAlarmManager,
                    alarmRepository,
                    reminderRepository
                )

                cleanupCommand(command, startId)

            } ?: run {
            if (BuildConfig.DEBUG) {
                showToast(
                    this@ReminderAlarmManager,
                    "ReminderAlarmManager.handleActionWithCommand: null reminder/alarm",
                    Toast.LENGTH_LONG
                )
            }
        }

    }

    // Cleanup the notification Command for a Reminder and the current handler command
    private fun cleanupCommand(command: Command, startId: Int?) {
        commands.remove(command.reminderId)

        startId?.let { stopSelf(it) }

        NotificationManagerCompat.from(this).cancel(command.notificationAlarmId.toInt())
        stopSelf(command.notificationStartId)
    }

    private suspend fun handleSkip(reminder: Reminder, alarm: Alarm) {
        saveHistoryWithStatus(reminder, alarm, ReminderStatus.SKIPPED)
        showToast(
            this,
            "Skipped Reminder",
        )
    }

    private suspend fun handleCompleted(reminder: Reminder, alarm: Alarm) {
        saveHistoryWithStatus(reminder, alarm, ReminderStatus.COMPLETED)
        showToast(
            this,
            "Completed Reminder",
        )
    }

    // Performs completion of a Reminder - cancels alarms, saves a new alarm and history entry,
    // schedules next alarm
    private suspend fun saveHistoryWithStatus(
        reminder: Reminder,
        alarm: Alarm,
        completionStatus: ReminderStatus
    ) {
        saveHistoryEntry(reminder, completionStatus)

        // Save a new alarm if appropriate
        if (!reminder.isOneOff) {
            val today = LocalDate.now().dayOfWeek
            reminder.getNextTimeForAlarm(today, false)?.let {
                val nextAlarm = Alarm(
                    reminderId = alarm.reminderId,
                    triggerAt = it.toEpochMilli()
                )
                nextAlarm.id = alarmRepository.save(nextAlarm)
            }
        }

        alarmRepository.delete(alarm)
    }

    private suspend fun saveHistoryEntry(
        reminder: Reminder,
        completionStatus: ReminderStatus
    ) {
        val history = History(
            reminderId = reminder.id,
            description = reminder.description,
            completionStatus = completionStatus,
            completedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
        )
        historyRepository.save(history)
    }

    private suspend fun handleSnooze(reminder: Reminder, alarm: Alarm) {
        Timber.d("Handle snooze: ${reminder.id}")
        snoozeAlarm(this, reminder, alarm)
        alarmRepository.delete(alarm)
    }

    private suspend fun snoozeAlarm(context: Context, reminder: Reminder, alarm: Alarm) {
        // Determine next triggerAt for a snoozing alarm
        val nextAlarm = Alarm(
            reminderId = alarm.reminderId,
            triggerAt = alarm.triggerAt.toLocalDateTime().plusMinutes(SNOOZE_TIME_MIN)
                .truncatedTo(ChronoUnit.MINUTES).toEpochMilli(),
        )

        // Delete previous, save changes
        nextAlarm.id = alarmRepository.save(nextAlarm)
        Timber.d("Saved next alarm: $nextAlarm")

        NotificationManagerCompat.from(context).cancel(alarm.id.toInt())
        showSnoozeNotifications(context, reminder, nextAlarm)
    }

    private fun showSnoozeNotifications(context: Context, reminder: Reminder, alarm: Alarm) {
        showToast(
            context,
            "Alarm snoozed until ${
                alarm.triggerAt.toLocalDateTime().format(
                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                )
            }"
        )

        triggerSnoozeNotification(
            context,
            reminder,
            alarm
        )
    }

    /**
     * Interface that can be used to snooze, skip, or complete the current alarm.
     */
    abstract class LocalBinder(val snoozeTimeout: EventFlow<Unit>) :
        Binder() {
        abstract fun snooze()
        abstract fun skip()
        abstract fun complete()
    }

    private inner class LocalBinderImpl(
        private val command: Command,
    ) :
        LocalBinder(command.snoozeTimeout) {

        override fun snooze() {
            handleAction(ACTION_ALARM_SNOOZE)
        }

        override fun skip() {
            handleAction(ACTION_ALARM_SKIP)
        }

        override fun complete() {
            handleAction(ACTION_ALARM_COMPLETE)
        }

        private fun handleAction(action: String) {
            if (command.handled) {
                return
            }

            applicationScope.launch {
                handleActionWithCommand(action, command, null)
            }
        }
    }

    companion object {
        /**
         * Bind to the ReminderAlarmManager service with the id for a Reminder.
         * If reminderId is not the same as the one that started the service,
         * onBind will return a null Binder
         */
        fun bind(context: Context, connection: ServiceConnection, reminderId: Long, flags: Int) {
            Intent(context, ReminderAlarmManager::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminderId)
            }.also {
                context.bindService(it, connection, flags)
            }
        }

        /**
         * Starts the service for a Reminder with the given id.
         */
        fun start(
            context: Context,
            alarm: Alarm,
            reminder: Reminder
        ) {
            getStartIntent(context, alarm, reminder).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                } else {
                    context.startService(it)
                }
            }
        }

        /**
         * Gets an intent for starting the service, using reminder.id, reminder.description,
         * and reminder.time for performing the action.
         */
        fun getStartIntent(context: Context, alarm: Alarm, reminder: Reminder): Intent =
            Intent(context, ReminderAlarmManager::class.java).apply {
                action = ACTION_ALARM_SET_NOTIF
                putExtra(EXTRA_ALARM_ID, alarm.id)
                putExtra(EXTRA_REMINDER_ID, reminder.id)
                putExtra(EXTRA_REMINDER_DESCRIPTION, reminder.description)
                putExtra(EXTRA_REMINDER_VIBRATE, reminder.vibrate)
            }

        /**
         * Gets an intent for starting the service with an action and reminderId
         */
        fun getActionIntent(context: Context, action: String, reminderId: Long): Intent =
            Intent(context, ReminderAlarmManager::class.java).apply {
                this.action = action
                putExtra(EXTRA_REMINDER_ID, reminderId)
            }
    }
}

private data class Command(
    val reminderId: Long = 0L,
    val notificationAlarmId: Long = 0L,
    val notificationStartId: Int = 0,
) {
    private val snoozeHandled: AtomicBoolean = AtomicBoolean(false)
    private val snoozeTimeoutEvent = MutableEventFlow<Unit>()
    val snoozeTimeout: EventFlow<Unit> = snoozeTimeoutEvent

    var handled: Boolean
        get() = snoozeHandled.get()
        set(value) {
            snoozeHandled.compareAndSet(false, value)
        }

    inline fun startSnoozeTimeout(
        scope: CoroutineScope,
        crossinline handleSnooze: suspend (Command) -> Unit,
    ) {
        val command = this
        scope.launch {
            Timber.d("Started command auto-snooze job")
            delay(DELAY_UNTIL_SNOOZE_SEC * 1000)
            if (snoozeHandled.compareAndSet(false, true)) {
                snoozeTimeoutEvent.value = Unit
                Timber.d("Reminder $reminderId alarm $notificationAlarmId auto-snoozing (startId:$notificationStartId)")
                try {
                    handleSnooze(command)
                    Timber.d("Auto-snooze job completed")
                } catch (e: CancellationException) {
                    withContext(NonCancellable) {
                        Timber.d("Auto-snooze job was cancelled")
                    }
                } catch (e: Exception) {
                    Timber.d("Exception in auto-snooze job: $e")
                }
            }
        }
    }
}

private fun Vibrator.start() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
        vibrate(
            VibrationEffect.createWaveform(VIBRATION_PATTERN, -1),
            audioAttributes
        )
    } else {
        vibrate(VIBRATION_PATTERN, -1)
    }
}
