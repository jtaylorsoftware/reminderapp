package com.github.jeremyt135.reminderapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.broadcast.VIBRATION_PATTERN
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RemindersActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.alarm_notification_channel_name)
            val descriptionText = getString(R.string.alarm_notification_channel_description)
            val channel = NotificationChannel(
                getString(R.string.alarm_notification_channel_id),
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = descriptionText
                vibrationPattern = null
                enableVibration(false)
                setSound(null, null)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}