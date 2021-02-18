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
import androidx.navigation.ui.navigateUp
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
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, arguments ->
            if (destination.id == R.id.fragment_reminder_editor){
                supportActionBar?.title = if(requireNotNull(arguments).getLong("reminderId") != 0L){
                    getString(R.string.editor_heading_edit)
                } else {
                    getString(R.string.editor_heading_add)
                }
            }
        }

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.alarm_notification_channel_name)
            val descriptionText = getString(R.string.alarm_notification_channel_description)
            val alarmChannel = NotificationChannel(
                getString(R.string.alarm_notification_channel_id),
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = descriptionText

                vibrationPattern = null
                enableVibration(false)

                setSound(null, null)

                enableLights(true)
                lightColor = NOTIFICATION_LED_ARGB
            }
            with((getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)) {
                createNotificationChannel(alarmChannel)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        findNavController(R.id.nav_host_fragment).navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

}