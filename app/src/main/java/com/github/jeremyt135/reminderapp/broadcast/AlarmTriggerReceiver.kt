package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.github.jeremyt135.BuildConfig
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.local.Alarm
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber


/**
 * Receives app ALARM_START for an individual Reminder in order to immediately create the full-screen
 * notification for it
 */
@AndroidEntryPoint
class AlarmTriggerReceiver : AlarmReceiver() {

    private val coroutineScope = MainScope() + CoroutineName("AlarmTriggerReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action != context.getString(R.string.action_alarm_start)) {
            return
        }
        val alarm = requireNotNull(intent.getParcelableExtra<Alarm>("alarm"))

        val pendingResult = goAsync()
        coroutineScope.launch {
            Timber.d("AlarmTriggerReceiver for alarm with reminder ${alarm.reminderId}")

            val reminder = requireNotNull(reminderRepository.findById(alarm.reminderId))

            if (alarmShouldTriggerNow(reminder, alarm)) {
                Timber.d("Alarms for reminder ${reminder.id} should fire soon")
                triggerNotification(context, alarm, reminder)

                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (reminder.vibrate && vibrator.hasVibrator()) {
                    vibrator.start()
                }

                launch(Dispatchers.Default) {
                    if (BuildConfig.DEBUG) {
                        delay(5 * 1000)
                    } else {
                        delay(30 * 1000)
                    }
                    vibrator.cancel()
                }
            } else {
                Timber.d("Alarms NOT firing for reminder ${reminder.id} - wrong time or date")
            }

            pendingResult.finish()
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
}