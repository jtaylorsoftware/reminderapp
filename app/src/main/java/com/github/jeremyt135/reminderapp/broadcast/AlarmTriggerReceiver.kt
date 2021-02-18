package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.github.jeremyt135.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime


/**
 * Receives app ALARM_START for an individual Reminder in order to immediately create the full-screen
 * notification for it
 */
class AlarmTriggerReceiver : AlarmReceiver(ACTION_ALARM_START) {

    override fun doReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        coroutineScope.launch {
            val (reminder, alarm) = requireNotNull(reminderRepository.getReminderAndAlarm(reminderId))

            Timber.d("AlarmTriggerReceiver for alarm with reminder ${alarm.reminderId}")

            val now = LocalDateTime.now()
            if (reminder.alarmCanBeSetToday(now.dayOfWeek)) {
                Timber.d("Alarms for reminder ${reminder.id} should fire soon")
                triggerNotification(context, reminder)

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