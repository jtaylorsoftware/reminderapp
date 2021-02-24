package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.os.Bundle
import android.view.View
import android.widget.TimePicker
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentReminderEditorBinding
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.util.time
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ReminderEditorFragment : Fragment(R.layout.fragment_reminder_editor) {
    val viewModel: ReminderEditorViewModel by activityViewModels()
        @VisibleForTesting get

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(requireActivity()) {
            actionBar?.title = if (intent.getLongExtra(EXTRA_REMINDER_ID, 0L) != 0L) {
                getString(R.string.editor_heading_edit)
            } else {
                getString(R.string.editor_heading_add)
            }
        }

        val binding = FragmentReminderEditorBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            model = viewModel
        }

        /**
         * Set the TimePicker's time and onTimeChangedListener once the Reminder has been loaded.
         *
         * Would just use two-way binding but it seems API level 21 has issues with the
         * android:hour & android:minute data binding attributes
         */
        viewModel.reminder.observe(viewLifecycleOwner) {
            setTimePickerTime(binding.timePickerEditor, it.hour, it.minute)

            binding.timePickerEditor.setOnTimeChangedListener { view, hour, minute ->
                Timber.d("Time has changed to $hour hr $minute min")
                viewModel.onTimeChanged(hour, minute)
            }
        }
    }

    private fun setTimePickerTime(timePicker: TimePicker, hour: Int, minute: Int) {
        Timber.d("Setting TimePicker's time")
        timePicker.time = Pair(hour, minute)
    }
}