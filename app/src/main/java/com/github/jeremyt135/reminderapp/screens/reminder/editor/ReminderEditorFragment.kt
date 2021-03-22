package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.launch
import androidx.activity.result.registerForActivityResult
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentReminderEditorBinding
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.data.domain.SnoozeDuration
import com.github.jeremyt135.reminderapp.data.domain.SnoozeInterval
import com.github.jeremyt135.reminderapp.util.time
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class ReminderEditorFragment : Fragment(R.layout.fragment_reminder_editor) {
    val viewModel: ReminderEditorViewModel by activityViewModels()
        @VisibleForTesting get

    private lateinit var timePickerEditor: TimePicker
    private lateinit var snoozeIntervalText: AutoCompleteTextView
    private lateinit var snoozeDurationText: AutoCompleteTextView

    private val getAlarmSound =
        registerForActivityResult(PickRingtone(), RingtoneManager.TYPE_ALL) { uri ->
            Timber.d("Finished ringtone activity: $uri")
            uri?.let {
                viewModel.setAlarmSound(it)
            }
        }

    init {
        lifecycleScope.launch {
            whenStarted {
                /**
                 * Set the TimePicker's onTimeChangedListener programmatically
                 *
                 * Would just use two-way binding but it seems API level 21 may have issues with the
                 * android:hour & android:minute data binding attributes
                 */
                timePickerEditor.setOnTimeChangedListener { _, hour, minute ->
                    Timber.d("Time has changed to $hour hr $minute min")
                    viewModel.onTimeChanged(hour, minute)
                }

                launch {
                    viewModel.reminder.collect { reminder ->
                        // Set values for Views that don't provide easy access to data binding
                        timePickerEditor.time = Pair(reminder.hour, reminder.minute)
                        snoozeIntervalText.setText(reminder.snoozeInterval.toString(), false)
                        snoozeDurationText.setText(reminder.snoozeDuration.toString(), false)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("onViewCreated")
        with(requireActivity()) {
            (this as AppCompatActivity).supportActionBar?.title =
                if ((intent.getStringExtra(EXTRA_REMINDER_ID) ?: "").isNotEmpty()) {
                    getString(R.string.editor_heading_edit)
                } else {
                    getString(R.string.editor_heading_add)
                }
        }

        setupBinding(FragmentReminderEditorBinding.bind(view))
    }

    private fun setupBinding(binding: FragmentReminderEditorBinding) {
        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            model = viewModel
            reminder = viewModel.reminder.asLiveData(lifecycleScope.coroutineContext)

            val snoozeIntervalItems = SnoozeInterval.values()
            val intervalAdapter =
                ArrayAdapter(requireContext(), R.layout.item_dropdown_snooze, snoozeIntervalItems)
            textinputEditorSnoozeInterval.let { textInputLayout ->
                textInputLayout.editText?.let { editText ->
                    snoozeIntervalText = editText as AutoCompleteTextView
                    snoozeIntervalText.setAdapter(intervalAdapter)
                    editText.doAfterTextChanged { text ->
                        text?.toString()?.let {
                            viewModel.onSnoozeIntervalChanged(SnoozeInterval.fromString(it))
                        }
                    }
                }
            }

            val snoozeDurationItems = SnoozeDuration.values()
            val durationAdapter =
                ArrayAdapter(requireContext(), R.layout.item_dropdown_snooze, snoozeDurationItems)
            textinputEditorSnoozeDuration.let { textInputLayout ->
                textInputLayout.editText?.let { editText ->
                    snoozeDurationText = editText as AutoCompleteTextView
                    snoozeDurationText.setAdapter(durationAdapter)
                    editText.doAfterTextChanged { text ->
                        text?.toString()?.let {
                            viewModel.onSnoozeDurationChanged(SnoozeDuration.fromString(it))
                        }
                    }
                }
            }

            switchEditorSound.setOnClickListener { getAlarmSound.launch() }

            this@ReminderEditorFragment.timePickerEditor = timePickerEditor
        }
    }

    class PickRingtone : ActivityResultContract<Int, Uri?>() {
        override fun createIntent(context: Context, ringtoneType: Int) =
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.EMPTY)
            }

        override fun parseResult(resultCode: Int, result: Intent?): Uri? {
            if (resultCode != Activity.RESULT_OK) {
                Timber.e("Result was $resultCode")
                return null
            }
            return result?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                ?.also {
                    Timber.d("Result was $it")
                }
        }
    }
}