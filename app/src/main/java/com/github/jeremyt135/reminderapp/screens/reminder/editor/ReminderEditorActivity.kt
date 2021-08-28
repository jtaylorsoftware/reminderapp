package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.lifecycle.asLiveData
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ReminderEditorActivity : AppCompatActivity() {
    private val viewModel: ReminderEditorViewModel by viewModels()

    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder_editor)

        viewModel.navigateToReminders.asLiveData().observe(this) { event ->
            event.value?.let{ NavUtils.navigateUpFromSameTask(this) }
        }
    }
}