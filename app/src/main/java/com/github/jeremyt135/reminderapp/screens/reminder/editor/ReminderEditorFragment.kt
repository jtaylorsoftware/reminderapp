package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.launch
import androidx.activity.result.registerForActivityResult
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentReminderEditorBinding
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.data.domain.SnoozeRepetitionOption
import com.github.jeremyt135.reminderapp.data.domain.SnoozeRules
import com.github.jeremyt135.reminderapp.util.time
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class ReminderEditorFragment : Fragment(R.layout.fragment_reminder_editor) {
    val viewModel: ReminderEditorViewModel by activityViewModels()
        @VisibleForTesting get

    private lateinit var timePicker: TimePicker
    private lateinit var snoozeRepetitionsText: AutoCompleteTextView
    private lateinit var snoozeRepetitionGroup: Group

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
                timePicker.setOnTimeChangedListener { _, hour, minute ->
                    Timber.d("Time has changed to $hour hr $minute min")
                    viewModel.onTimeChanged(hour, minute)
                }

                launch {
                    viewModel.reminder.collect { reminder ->
                        // Initialize timePicker from reminder
                        timePicker.time = Pair(reminder.schedulingRules.hour, reminder.schedulingRules.minute)
                    }
                }

                launch {
                    viewModel.snoozeRepetitionOption.collect {
                        // Initialize option from saved state
                        snoozeRepetitionsText.setText(it.toString(), false)
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

            snoozeRepetitionGroup = groupEditorMaxSnoozeRepeats

            // Provide options for the dropdown menu
            val intervalAdapter =
                ArrayAdapter(
                    requireContext(),
                    R.layout.item_dropdown_snooze,
                    SnoozeRepetitionOption.values()
                )
            textinputEditorMaxSnoozeRepeats.apply {
                editText?.let { editText ->
                    snoozeRepetitionsText = editText as AutoCompleteTextView
                    snoozeRepetitionsText.setAdapter(intervalAdapter)
                    editText.doAfterTextChanged { editable ->
                        editable?.toString()?.let { text ->
                            viewModel.onSnoozeRepetitionOptionChanged(text)
                        }
                    }
                }
            }

            // When the "sound" switch is toggled, open sound selector
            switchEditorSound.setOnClickListener { getAlarmSound.launch() }

            timePicker = timePickerEditor

            // Set filters on the numeric text inputs
            editEditorMaxSnoozeRepeats.filters =
                arrayOf(RangeInputFilter(0, Long.MAX_VALUE))
            editEditorSnoozeDuration.filters = arrayOf(
                RangeInputFilter(
                    SnoozeRules.MAX_SNOOZE_DURATION,
                    SnoozeRules.MIN_SNOOZE_DURATION,
                )
            )

            // Add listeners to repopulate the text input if they're left empty
            editEditorMaxSnoozeRepeats.setOnFocusChangeListener { editText, hasFocus ->
                if (!hasFocus) {
                    (editText as EditText).setText(viewModel.reminder.value.snoozeRules.maxRepeats.toString())
                }
            }
            editEditorSnoozeDuration.setOnFocusChangeListener { editText, hasFocus ->
                if (!hasFocus) {
                    (editText as EditText).setText(viewModel.reminder.value.snoozeRules.duration.toString())
                }
            }
        }
    }


}

private class PickRingtone : ActivityResultContract<Int, Uri?>() {
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

/**
 * Filter for snooze EditText that limits values to acceptable range
 * TODO - Put an error state in the ViewModel that notifies user of accepted range?
 */
private class RangeInputFilter(private var min: Long, private var max: Long) : InputFilter {
    init {
        if (max < min) {
            val t = min
            min = max
            max = t
        }
    }

    override fun filter(
        source: CharSequence?, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int
    ): CharSequence? {
        return try {
            val src = source.toString()
            val dst = dest.toString()
            val input = (dst + src).toLong()
            if (input in min..max) {
                null
            } else {
                ""
            }
        } catch (e: NumberFormatException) {
            ""
        }
    }
}
