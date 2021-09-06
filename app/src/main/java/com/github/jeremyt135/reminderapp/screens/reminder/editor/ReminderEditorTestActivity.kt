package com.github.jeremyt135.reminderapp.screens.reminder.editor

import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import com.github.jeremyt135.reminderapp.data.domain.SnoozeRules
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.DayOfWeekDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.SnoozeOptionsDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.SoundPickerDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.time.DayOfWeek
import java.util.*

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
@AndroidEntryPoint
class ReminderEditorTestActivity : AppCompatActivity(), SoundPickerDialogFragment.DialogListener,
    SnoozeOptionsDialogFragment.DialogListener, DayOfWeekDialogFragment.DialogListener {
    val viewModel: ReminderEditorViewModel by viewModels()

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