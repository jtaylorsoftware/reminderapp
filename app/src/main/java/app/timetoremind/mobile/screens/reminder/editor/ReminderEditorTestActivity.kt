package app.timetoremind.mobile.screens.reminder.editor

import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import app.timetoremind.mobile.data.domain.SnoozeRules
import app.timetoremind.mobile.screens.reminder.editor.dialog.DayOfWeekDialogFragment
import app.timetoremind.mobile.screens.reminder.editor.dialog.SnoozeOptionsDialogFragment
import app.timetoremind.mobile.screens.reminder.editor.dialog.SoundPickerDialogFragment
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