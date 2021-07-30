package com.github.jeremyt135.reminderapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.broadcast.NOTIFICATION_LED_ARGB
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RemindersActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_main) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmChannel = NotificationChannel(
                getString(R.string.alarm_notification_channel_id),
                getString(R.string.alarm_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.alarm_notification_channel_description)

                vibrationPattern = null
                enableVibration(false)

                setSound(null, null)

                enableLights(true)
                lightColor = NOTIFICATION_LED_ARGB
            }
            val schedulingChannel = NotificationChannel(
                getString(R.string.scheduling_notification_channel_id),
                getString(R.string.scheduling_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.scheduling_notification_channel_description)
            }
            with((getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)) {
                createNotificationChannel(alarmChannel)
                createNotificationChannel(schedulingChannel)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        NavigationUI.navigateUp(
            findNavController(R.id.nav_host_fragment_main),
            appBarConfiguration
        ) || super.onSupportNavigateUp()

}