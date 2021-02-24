package com.github.jeremyt135.reminderapp.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.os.*
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import com.github.jeremyt135.reminderapp.di.MainDispatcher
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Groups an action to perform with the reminderId to perform it on.
 */
data class Action(val name: String, val reminderId: Long)

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

    @DefaultDispatcher
    @Inject
    lateinit var defaultDispatcher: CoroutineDispatcher

    /**
     * A CoroutineScope bound to Dispatchers.Main context (used to emit actions)
     */
    private val serviceScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + mainDispatcher + CoroutineName("ReminderAlarmManager"))
    }

    /**
     * SharedFlow of actions to collect on in ActionHandler
     */
    private val actions = MutableSharedFlow<Action>()
    private lateinit var handler: ActionHandler

    /**
     * SharedFlow that is emitted to when the service has waited for the max
     * time before it automatically snoozes the alarm.
     */
    private val _snoozeTimeout = MutableSharedFlow<Unit>()

    /**
     * Provides components an interface to perform basic alarm actions.
     */
    private val binder: IBinder = LocalBinderImpl(_snoozeTimeout)

    /**
     * The id of the Reminder for this service instance. Once created, all actions must match
     * this id.
     */
    private val reminderId = AtomicLong(0)

    /**
     * Flag that is set when the service received and handled an Intent with an action other than
     * START.
     */
    private val isHandled = AtomicBoolean(false)

    private var activeBindings = 0

    override fun onBind(intent: Intent): IBinder {
        Timber.d("Bound AlarmManagerService")
        activeBindings += 1
        if (intent.action != null) {
            serviceScope.launch {
                actions.emit(
                    Action(
                        intent.action!!,
                        intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
                    )
                )
            }
        }
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        activeBindings -= 1
        return false
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("Service created")
        handler = ActionHandler()

        /**
         * Give user time to react to alarm, but then automatically snooze the alarm
         * if it has not been handled
         */
        serviceScope.launch {
            delay(15 * 1000)
            Timber.d("15 seconds since service started")
            Timber.d("Currently $activeBindings bindings")

            if (!isHandled.get()) {
                Timber.d("Not handled - auto snoozing")
                _snoozeTimeout.emit(Unit)
                actions.emit(
                    Action(
                        ACTION_ALARM_SNOOZE,
                        reminderId.get()
                    )
                )
            }

        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("Service starting")

        if (intent != null && intent.action != null) {
            // Emit from main thread for the handler to collect on in another thread
            serviceScope.launch {
                actions.emit(
                    Action(
                        intent.action!!,
                        intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
                    )
                )
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        val vibrator = this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()
        Timber.d("Service destroyed")
    }

    /**
     * Interface that can be used to snooze, skip, or complete the current alarm.
     */
    abstract class LocalBinder(val snoozeTimeout: SharedFlow<Unit>) : Binder() {
        abstract fun snooze()
        abstract fun skip()
        abstract fun complete()
    }

    inner class LocalBinderImpl(snoozeTimeout: SharedFlow<Unit>) : LocalBinder(snoozeTimeout) {
        override fun snooze() {
            serviceScope.launch {
                actions.emit(Action(ACTION_ALARM_SNOOZE, reminderId.get()))
            }
        }

        override fun skip() {
            serviceScope.launch {
                actions.emit(Action(ACTION_ALARM_SKIP, reminderId.get()))
            }
        }

        override fun complete() {
            serviceScope.launch {
                actions.emit(Action(ACTION_ALARM_COMPLETE, reminderId.get()))
            }
        }
    }

    /**
     * Processes actions from the ReminderAlarmManager.
     */
    private inner class ActionHandler {
        /**
         * A CoroutineScope bound on Dispatchers.Default with the lifetime of serviceScope
         */
        private val handlerScope: CoroutineScope by lazy {
            serviceScope + defaultDispatcher + CoroutineName("ReminderAlarmManager.ActionHandler")
        }

        init {
            handlerScope.launch {
                // Collect actions on separate thread
                actions.collect { action ->
                    Timber.d("Received action: ${action.name} for reminder ${action.reminderId}")
                    handleAction(action)
                }
            }
        }

        private suspend fun handleAction(action: Action) {
            when (action.name) {
                /**
                 * Set up the foreground notification - the first action emitted
                 * should always be this, otherwise the service will likely ANR.
                 */
                ACTION_ALARM_START -> handleStart(action)

                /**
                 * Handle the management action (snooze, skip, or complete). Can be done
                 * once per service/alarm.
                 */
                ACTION_ALARM_SNOOZE -> handleSnooze(action)
                ACTION_ALARM_SKIP -> handleCompletion(action, ReminderStatus.SKIPPED)
                ACTION_ALARM_COMPLETE -> handleCompletion(action, ReminderStatus.COMPLETED)
            }
        }

        private suspend fun handleStart(action: Action) {
            if (reminderId.compareAndSet(0L, action.reminderId)) {
                val reminder =
                    requireNotNull(reminderRepository.findById(reminderId.get()))
                val vibrator =
                    this@ReminderAlarmManager.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (reminder.vibrate && vibrator.hasVibrator()) {
                    vibrator.start()
                }
                startForeground(
                    reminderId.toInt(),
                    createForegroundNotification(reminder)
                )
            }
        }

        private fun createForegroundNotification(reminder: Reminder): Notification {
            val context: Context = this@ReminderAlarmManager
            return buildNotificationForAlarm(
                context,
                getIntentForAlarmActivity(context, reminder.id), reminder
            )
        }

        private suspend fun handleCompletion(
            action: Action,
            completionStatus: ReminderStatus
        ) {
            if (action.reminderId == reminderId.get() &&
                isHandled.compareAndSet(false, true)
            ) {
                val (reminder, alarm) = requireNotNull(
                    reminderRepository.getReminderAndAlarm(reminderId.get())
                )
                alarmRepository.delete(alarm)
                completeReminderWithStatus(reminder, completionStatus)
            }
        }

        private suspend fun completeReminderWithStatus(
            reminder: Reminder,
            completionStatus: ReminderStatus
        ) {
            val context: Context = this@ReminderAlarmManager

            // Add a history entry
            val history = History(
                reminderId = reminder.id,
                description = reminder.description,
                completionStatus = completionStatus,
                completedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
            )
            historyRepository.save(history)

            // Schedule a new alarm if appropriate
            if (!reminder.isOneOff) {
                Timber.d("$completionStatus -> scheduling new alarm")
                scheduleAlarmForReminder(
                    context,
                    reminder,
                    LocalDate.now().dayOfWeek,
                    false
                )?.let {
                    Timber.d("Saving new alarm")
                    alarmRepository.save(it)
                } ?: Timber.d("Did not save new alarm")
            }

            stopSelf()
        }

        private suspend fun handleSnooze(action: Action) {
            if (action.reminderId == reminderId.get() &&
                isHandled.compareAndSet(false, true)
            ) {
                val (reminder, alarm) = requireNotNull(
                    reminderRepository.getReminderAndAlarm(reminderId.get())
                )
                snoozeAlarm(reminder, alarm)
            }
        }

        private suspend fun snoozeAlarm(reminder: Reminder, alarm: Alarm) {
            val context: Context = this@ReminderAlarmManager

            // Determine next triggerAt
            val nextAlarm = alarm.copy(
                id = 0,
                triggerAt = alarm.triggerAt.toLocalDateTime().plusMinutes(SNOOZE_TIME_MIN)
                    .truncatedTo(ChronoUnit.MINUTES)
                    .toEpochMilli()
            )

            // Delete previous, save changes
            alarmRepository.delete(alarm)
            nextAlarm.id = alarmRepository.save(nextAlarm)

            // Schedule new alarm
            scheduleAlarm(context, nextAlarm)

            Timber.d("Snoozed and saved next alarm")
            withContext(mainDispatcher) {
                showToast(context, "Alarm snoozed until 2 minutes from now")
                triggerSnoozeNotification(
                    context,
                    reminder,
                    nextAlarm
                )
            }

            stopSelf()
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
        fun start(context: Context, action: String, reminderId: Long) {
            getIntent(context, action, reminderId).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                } else {
                    context.startService(it)
                }
            }
        }

        /**
         * Gets an intent for starting the service with an action and reminderId
         */
        fun getIntent(context: Context, action: String, reminderId: Long): Intent =
            Intent(context, ReminderAlarmManager::class.java).apply {
                this.action = action
                putExtra(EXTRA_REMINDER_ID, reminderId)
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
