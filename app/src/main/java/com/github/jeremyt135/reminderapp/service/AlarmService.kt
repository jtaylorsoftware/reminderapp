package com.github.jeremyt135.reminderapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.*
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Service that creates the notification and plays vibration & sound for an Alarm.
 */
@AndroidEntryPoint
class AlarmService : Service() {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    private var mediaPlayer: MediaPlayer? = null
    private val binder = AlarmServiceBinderImpl()

    private val running = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Timber.d("Created service")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            ACTION_ALARM_START -> {
                start(intent)
            }
            ACTION_ALARM_STOP -> {
                stopSelf()
            }
            null -> {
                Timber.e("onStartCommand called with null action")
                stopSelf(startId)
            }
            else -> {
                Timber.e("onStartCommand called with unknown action ${intent.action}")
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("Service destroyed")
        stopVibrateAndPlayback()
    }

    private fun start(intent: Intent) {
        if(!running.compareAndSet(false, true)){
            Timber.w("Service already running")
            return
        }

        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: ""
        val description = intent.getStringExtra(EXTRA_REMINDER_DESCRIPTION) ?: ""
        val vibrate = intent.getBooleanExtra(EXTRA_REMINDER_VIBRATE, false)
        val sound = intent.getStringExtra(EXTRA_REMINDER_SOUND)

        startForeground(
            reminderId.hashCode(),
            createNotificationForAlarm(
                this,
                Reminder(id = reminderId, description = description, vibrate = vibrate)
            )
        )

        startVibrateAndPlayback(vibrate, sound)
    }

    private fun createNotificationForAlarm(context: Context, reminder: Reminder) =
        buildNotificationForAlarm(
            context,
            getIntentForAlarmActivity(context, reminder.id),
            reminder
        )

    private fun startVibrateAndPlayback(vibrate: Boolean, sound: String?) {
        if (vibrate) {
            startVibrate()
        }
        if (sound != null) {
            startPlayback(sound)
        }
    }


    private fun startVibrate() {
        Timber.d("Starting vibrate if available")
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.start()
        }
    }

    private fun startPlayback(sound: String) {
        Timber.d("Starting playback")
        mediaPlayer = MediaPlayer().apply {
            // TODO
        }
    }

    private fun stopVibrateAndPlayback() {
        if(!running.get()){
            Timber.w("Service was not running")
        }

        stopVibrate()
        stopPlayback()
    }

    private fun stopVibrate() {
        Timber.d("Stopping vibrate")
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()
    }

    private fun stopPlayback() {
        Timber.d("Stopping playback")
        mediaPlayer?.stop()
    }

    abstract class AlarmServiceBinder : Binder() {
        /**
         * Stops the Service and its currently running vibration & playback.
         */
        abstract fun stop()
    }

    private inner class AlarmServiceBinderImpl : AlarmServiceBinder() {
        override fun stop() {
            with(this@AlarmService) {
                stopSelf()
            }
        }
    }

    companion object {
        /**
         * Binds to the Service
         */
        fun bind(context: Context, serviceConnection: ServiceConnection) {
            context.bindService(
                Intent(context, AlarmService::class.java),
                serviceConnection,
                0
            )
        }

        /**
         * Starts the Service using a Reminder to determine vibration & sound settings.
         */
        fun start(context: Context, reminder: Reminder) {
            Intent(context, AlarmService::class.java).apply {
                action = ACTION_ALARM_START
                putExtra(EXTRA_REMINDER_ID, reminder.id)
                putExtra(EXTRA_REMINDER_DESCRIPTION, reminder.description)
                putExtra(EXTRA_REMINDER_VIBRATE, reminder.vibrate)
                putExtra(EXTRA_REMINDER_SOUND, "")
            }.also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                } else {
                    context.startService(it)
                }
            }
        }

        /**
         * Requests the Service stops, allowing for cleanup to happen if necessary.
         */
        fun requestStop(context: Context) {
            context.startService(
                Intent(context, AlarmService::class.java).apply {
                    action = ACTION_ALARM_STOP
                }
            )
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