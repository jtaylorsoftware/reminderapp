package app.timetoremind.mobile.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import app.timetoremind.mobile.broadcast.*
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.parcel.ReminderParcel
import app.timetoremind.mobile.di.AppCoroutineScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Service that creates the notification and plays vibration & sound for an Alarm.
 */
@AndroidEntryPoint
class AlarmMediaService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var timerManager: AlarmTimerManager

    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    private var mediaPlayer: MediaPlayer? = null
    private val binder = AlarmServiceBinderImpl()

    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Timber.d("Created service")

        // Stop the service & its notification when timer ends
        if (timerManager.timer != null){
            timerJob = applicationScope.launch {
                timerManager.timer?.timerFinished?.flow?.collect { finished ->
                    finished.peek()?.let {
                        Timber.d("AlarmService received AlarmTimer finished event")
                        stopSelf()
                    }
                }
            }
        } else {
            Timber.e("AlarmTimerManager.timer was null when creating AlarmService")
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Timber.d("AlarmService onStartCommand for ${intent.action}")
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
        timerJob?.cancel()
        stopVibrateAndPlayback()
        _running.set(false)
    }

    private fun start(intent: Intent) {
        if (!_running.compareAndSet(false, true)) {
            Timber.w("Service already running")
            return
        }

        val reminder = intent.getParcelableExtra<ReminderParcel>(EXTRA_REMINDER)
        if (reminder == null){
            stopSelf()
        } else {
            startForeground(
                reminder.data.id.hashCode(),
                createNotificationForAlarm(
                    this,
                    reminder.data
                )
            )

            startVibrateAndPlayback(reminder.data.vibrate, reminder.data.notifSoundUri)
        }
    }

    private fun createNotificationForAlarm(context: Context, reminder: Reminder) =
        buildNotificationForAlarm(
            context,
            getIntentForAlarmActivity(context, reminder.id),
            reminder
        )

    private fun startVibrateAndPlayback(vibrate: Boolean, soundUri: String) {
        if (vibrate) {
            startVibrate()
        }
        if (soundUri.isNotEmpty()) {
            startPlayback(soundUri)
        }
    }


    private fun startVibrate() {
        Timber.d("Starting vibrate if available")
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.startScheduleNext()
        }
    }

    private fun startPlayback(soundUri: String) {
        Timber.d("Starting playback")
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener(this@AlarmMediaService)
            setOnErrorListener(this@AlarmMediaService)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
            isLooping = true
            setDataSource(this@AlarmMediaService, Uri.parse(soundUri))
            prepareAsync()
        }
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        mediaPlayer.start()
    }

    override fun onError(mediaPlayer: MediaPlayer, what: Int, extra: Int): Boolean {
        Timber.e("Error using MediaPlayer: $what $extra")
        mediaPlayer.reset()
        return true
    }

    private fun stopVibrateAndPlayback() {
        if (!_running.get()) {
            Timber.w("Service was not running")
            return
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
        mediaPlayer?.let {
            it.stop()
            it.release()
        }
    }

    abstract class AlarmServiceBinder : Binder() {
        /**
         * Stops the Service and its currently running vibration & playback.
         */
        abstract fun stop()
    }

    private inner class AlarmServiceBinderImpl : AlarmServiceBinder() {
        override fun stop() {
            with(this@AlarmMediaService) {
                stopSelf()
            }
        }
    }

    companion object {
        // flag to track if AlarmService has started
        private val _running = AtomicBoolean(false)

        /**
         * Indicates that the AlarmService has started
         */
        val running: Boolean
            get() = _running.get()

        /**
         * Binds to the Service
         */
        fun bind(context: Context, serviceConnection: ServiceConnection) {
            context.bindService(
                Intent(context, AlarmMediaService::class.java),
                serviceConnection,
                0
            )
        }

        /**
         * Starts the Service using a Reminder to determine vibration & sound settings.
         */
        fun start(context: Context, reminder: Reminder) {
            Intent(context, AlarmMediaService::class.java).apply {
                action = ACTION_ALARM_START
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
         * Requests the Service stops.
         */
        fun requestStop(context: Context) {
            context.startService(
                Intent(context, AlarmMediaService::class.java).apply {
                    action = ACTION_ALARM_STOP
                }
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun Vibrator.startScheduleNext() {
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