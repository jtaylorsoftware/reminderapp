package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TimePicker
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.navigation.fragment.findNavController
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentReminderEditorBinding
import com.github.jeremyt135.reminderapp.util.time
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ReminderEditorFragment : Fragment(R.layout.fragment_reminder_editor) {
    private val viewModel: ReminderEditorViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentReminderEditorBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            model = viewModel

            buttonEditorCancel.setOnClickListener {
                goToReminders()
            }
            buttonEditorSave.setOnClickListener {
                viewModel.saveReminder()
            }
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

        viewModel.navigateToReminders.asLiveData().observe(viewLifecycleOwner) {
            goToReminders()
        }
    }

    private fun goToReminders() {
        findNavController().navigateUp()
    }

    private fun setTimePickerTime(timePicker: TimePicker, hour: Int, minute: Int) {
        Timber.d("Setting TimePicker's time")
        timePicker.time = Pair(hour, minute)
    }
}