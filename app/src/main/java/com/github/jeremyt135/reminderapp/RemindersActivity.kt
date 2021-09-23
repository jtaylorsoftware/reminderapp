package com.github.jeremyt135.reminderapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.broadcast.NOTIFICATION_LED_ARGB
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderFilterRules
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderSortRules
import com.github.jeremyt135.reminderapp.screens.reminder.overview.RemindersViewModel
import com.github.jeremyt135.reminderapp.screens.reminder.overview.dialog.RemindersFilterDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.overview.dialog.RemindersSortDialogFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RemindersActivity : AppCompatActivity(), RemindersSortDialogFragment.DialogListener,
    RemindersFilterDialogFragment.DialogListener {
    private lateinit var appBarConfiguration: AppBarConfiguration

    private val viewModel: RemindersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_main) as NavHostFragment?
        val navController = navHostFragment?.navController
        appBarConfiguration =
            AppBarConfiguration(
                setOf(
                    R.id.fragment_reminders,
                    R.id.fragment_history,
                    R.id.fragment_calendar,
                    R.id.fragment_alarm_list
                )
            )
        if (navController != null) {
            setupActionBarWithNavController(navController, appBarConfiguration)
            val bottomNav: BottomNavigationView? = findViewById(R.id.bottom_navigation)
            if (bottomNav != null) {
                bottomNav.setOnItemReselectedListener {
                    // ignore reselection
                }
                NavigationUI.setupWithNavController(bottomNav, navController)
            }
        }

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

    override fun onSortRulesChange(sortRules: ReminderSortRules) {
        viewModel.changeSortRules(sortRules)
    }

    override fun onFilterRulesChange(filterRules: ReminderFilterRules) {
        viewModel.changeFilterRules(filterRules)
    }
}