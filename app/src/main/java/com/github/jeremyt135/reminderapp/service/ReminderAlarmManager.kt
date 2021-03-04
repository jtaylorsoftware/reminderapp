package com.github.jeremyt135.reminderapp.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.os.*
import androidx.core.app.NotificationManagerCompat
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.di.MainDispatcher
import com.github.jeremyt135.reminderapp.util.EventFlow
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.time.*
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@AndroidEntryPoint
class ReminderAlarmManager : Service() {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    @MainDispatcher
    @Inject
    lateinit var mainDispatcher: CoroutineDispatcher

    private val serviceScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + mainDispatcher + CoroutineName("ReminderAlarmManager"))
    }

    private val commands = mutableMapOf<Long, Command>()

    private var activeBindings = 0

    override fun onBind(intent: Intent): IBinder {
        activeBindings += 1

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
        // Return an appropriate binder if reminderId != 0, otherwise null to reject bind
        return commands[reminderId]?.let {
            LocalBinderImpl(it)
        } ?:
        // Return a default Binder to clients that bind with invalid parameters.
        // Mostly used for testing.
        Binder()
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
            serviceScope.launch {
                handleCommand(intent, action, startId)
            }
        } else {
            Timber.w("onStartCommand called with null action in Intent")
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        val vibrator = this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()
        Timber.d("Service destroyed")
    }

    private suspend fun handleCommand(intent: Intent, action: String, startId: Int) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)

        // Do not handle command at all if no reminderId is given
        if (reminderId == 0L) {
            Timber.e("handleCommand (startId: $startId) received 0 for EXTRA_REMINDER_ID")
            stopSelf(startId)
            return
        }

        when (action) {
            ACTION_ALARM_SET_NOTIF -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
                val description = intent.getStringExtra(EXTRA_REMINDER_DESCRIPTION) ?: ""
                val vibrate = intent.getBooleanExtra(EXTRA_REMINDER_VIBRATE, false)
                handleStart(
                    Reminder(id = reminderId, description = description, vibrate = vibrate),
                    alarmId,
                    startId
                )
            }
            ACTION_ALARM_SNOOZE -> handleSnooze(reminderId, startId)
            ACTION_ALARM_SKIP -> handleSkip(reminderId, startId)
            ACTION_ALARM_COMPLETE -> handleCompleted(reminderId, startId)
        }
    }

    private suspend fun handleStart(reminder: Reminder, alarmId: Long, startId: Int) {
        // Set notification using extras so it can get to foreground ASAP
        setNotification(
            reminder,
            alarmId
        )

        // Store a Command with the startId from the current onStartCommand so
        // it can be modified later
        val snoozeTimeout = MutableEventFlow<Unit>()
        val snoozeJob = serviceScope.launch {
            delay(DELAY_UNTIL_SNOOZE_SEC * 1000)
            snoozeTimeout.value = Unit
            Timber.d("Reminder alarm ${reminder.id} auto-snoozing")
            handleSnooze(reminder.id, null)
        }
        val command = Command(
            reminderId = reminder.id,
            alarmId = alarmId,
            notificationStartId = startId,
            snoozeTimeout = snoozeTimeout,
            snoozeJob = snoozeJob
        )
        commands[reminder.id] = command
    }

    private fun setNotification(reminder: Reminder, alarmId: Long) {
        val vibrator =
            this@ReminderAlarmManager.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (reminder.vibrate && vibrator.hasVibrator()) {
            vibrator.start()
        }
        startForeground(
            alarmId.toInt(),
            createForegroundNotification(reminder)
        )
    }

    private fun createForegroundNotification(reminder: Reminder): Notification {
        val context: Context = this@ReminderAlarmManager
        return buildNotificationForAlarm(
            context,
            getIntentForAlarmActivity(context, reminder.id), reminder
        )
    }

    private suspend fun handleSkip(reminderId: Long, startId: Int?) =
        handleCompletion(reminderId, ReminderStatus.SKIPPED, startId)

    private suspend fun handleCompleted(reminderId: Long, startId: Int?) =
        handleCompletion(reminderId, ReminderStatus.COMPLETED, startId)

    private suspend fun handleCompletion(
        reminderId: Long,
        completionStatus: ReminderStatus,
        startId: Int?
    ) {
        Timber.d("Completing reminder $reminderId")
        commands[reminderId]?.let { command ->
            reminderRepository.getReminderAndAlarm(command.reminderId)?.let { (reminder, alarm) ->
                val context: Context = this@ReminderAlarmManager
                NotificationManagerCompat.from(context).cancel(alarm.id.toInt())

                saveHistoryEntry(reminder, completionStatus)

                // Remove old alarm
                alarmRepository.delete(alarm)

                // Save a new alarm if appropriate
                if (!reminder.isOneOff) {
                    val today = LocalDate.now().dayOfWeek
                    reminder.getNextTimeForAlarm(today, false)?.let {
                        val nextAlarm = alarm.copy(triggerAt = it.toEpochMilli())
                        nextAlarm.id = alarmRepository.save(nextAlarm)
                    }
                }

                scheduleNextAlarm(context, alarmRepository, reminderRepository)
            }
            command.snoozeJob.cancel()
            startId?.let { stopSelf(it) }
            stopSelf(command.notificationStartId)
        }
        commands.remove(reminderId)
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

    private suspend fun handleSnooze(reminderId: Long, startId: Int?) {
        Timber.d("Handle snooze: $reminderId")
        commands[reminderId]?.let { command ->
            val (reminder, alarm) = requireNotNull(
                reminderRepository.getReminderAndAlarm(command.reminderId)
            )

            snoozeAlarm(reminder, alarm)

            command.snoozeJob.cancel()
            startId?.let { stopSelf(it) }
            stopSelf(command.notificationStartId)
        }
        commands.remove(reminderId)
    }

    private suspend fun snoozeAlarm(reminder: Reminder, alarm: Alarm) {
        val context: Context = this@ReminderAlarmManager
        NotificationManagerCompat.from(context).cancel(alarm.id.toInt())

        // Determine next triggerAt
        val nextAlarm = alarm.copy(
            id = 0,
            triggerAt = alarm.triggerAt.toLocalDateTime().plusMinutes(SNOOZE_TIME_MIN)
                .truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
        )

        // Delete previous, save changes
        alarmRepository.delete(alarm)
        nextAlarm.id = alarmRepository.save(nextAlarm)

        // Schedule new alarm
        scheduleNextAlarm(context, alarmRepository, reminderRepository)

        withContext(mainDispatcher) {
            showToast(context, "Alarm snoozed until 2 minutes from now")
            triggerSnoozeNotification(
                context,
                reminder,
                nextAlarm
            )
        }
    }

    abstract class LocalBinder(val snoozeTimeout: EventFlow<Unit>) :
        Binder() {
        abstract fun snooze()
        abstract fun skip()
        abstract fun complete()
    }

    /**
     * Interface that can be used to snooze, skip, or complete the current alarm.
     */
    private inner class LocalBinderImpl(
        private val command: Command,
    ) :
        LocalBinder(command.snoozeTimeout) {

        override fun snooze() {
            serviceScope.launch {
                handleSnooze(command.reminderId, null)
            }
        }

        override fun skip() {
            serviceScope.launch {
                handleSkip(command.reminderId, null)
            }
        }

        override fun complete() {
            serviceScope.launch {
                handleCompleted(command.reminderId, null)
            }
        }

    }

    companion object {
        /**
         * Bind to the ReminderAlarmManager service with the id for a Reminder.
         * If reminderId is not the same as the one that started the service,
         * onBind will return a null Binder
         */
        fun bind(context: Context, connection: ServiceConnection, reminderId: Long) {
            Intent(context, ReminderAlarmManager::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminderId)
            }.also {
                context.bindService(it, connection, 0)
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
    val alarmId: Long = 0L,
    val notificationStartId: Int = 0,
    val snoozeTimeout: EventFlow<Unit>,
    var snoozeJob: Job,
)

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
