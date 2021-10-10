package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.MainActivity
import com.github.jeremyt135.reminderapp.data.domain.SnoozeRules
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.DayOfWeekDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.SnoozeOptionsDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.SoundPickerDialogFragment
import com.github.jeremyt135.reminderapp.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import java.time.DayOfWeek
import java.util.*

@AndroidEntryPoint
class ReminderEditorActivity : SettingsActivity(), SoundPickerDialogFragment.DialogListener,
    SnoozeOptionsDialogFragment.DialogListener, DayOfWeekDialogFragment.DialogListener {
    private val viewModel: ReminderEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder_editor)

        setSupportActionBar(requireNotNull(findViewById(R.id.top_app_bar)))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel.navigateToReminders.asLiveData().observe(this) { event ->
            event.value?.let {
                // When "Cancel" button is pressed in editor, finish activity
                finish()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            // "Up" button is pressed
            android.R.id.home -> {
                if (isTaskRoot) {
                    // If only activity, start new MainActivity on up
                    // (this typically happens through alarm clock's showIntent or
                    // notification snoozed intent
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onVibrateOptionChanged(vibrate: Boolean) {
        viewModel.onVibrateChanged(vibrate)
    }

    override fun onSoundUriChanged(soundUri: String) {
        viewModel.onSoundUriChanged(soundUri)
    }

    override fun onSnoozeRulesChanged(snoozeRules: SnoozeRules) {
        viewModel.onSnoozeRulesChanged(snoozeRules)
    }

    override fun onDaysOfWeekChanged(newValue: SortedSet<DayOfWeek>) {
        viewModel.onDaysOfWeekChanged(newValue)
    }
}