package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.domain.SnoozeRules
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.DayOfWeekDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.SnoozeOptionsDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.SoundPickerDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.DayOfWeek
import java.util.*

@AndroidEntryPoint
class ReminderEditorActivity : AppCompatActivity(), SoundPickerDialogFragment.DialogListener,
    SnoozeOptionsDialogFragment.DialogListener, DayOfWeekDialogFragment.DialogListener {
    private val viewModel: ReminderEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder_editor)

        viewModel.navigateToReminders.asLiveData().observe(this) { event ->
            event.value?.let { NavUtils.navigateUpFromSameTask(this) }
        }
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
        Timber.d("Days of week: $newValue")
        viewModel.onDaysOfWeekChanged(newValue)
    }
}