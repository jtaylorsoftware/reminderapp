package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentReminderEditorBinding
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.DayOfWeekDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.SnoozeOptionsDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.SoundPickerDialogFragment
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDate
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.LocalTime
import java.time.ZoneId

@AndroidEntryPoint
class ReminderEditorFragment : Fragment(R.layout.fragment_reminder_editor) {
    val viewModel: ReminderEditorViewModel by activityViewModels()
        @VisibleForTesting get

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (viewModel.isEditing) {
            inflater.inflate(R.menu.editor_menu_editing, menu)
        } else {
            inflater.inflate(R.menu.editor_menu, menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.item_editor_delete -> {
            buildDeleteDialog().show()
            true
        }
        R.id.item_editor_save -> {
            viewModel.saveReminder()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun buildDeleteDialog(): AlertDialog {
        return AlertDialog.Builder(requireActivity()).apply {
            setMessage(R.string.dialog_editorDelete_message)
            setTitle(R.string.dialog_editorDelete_title)
            setPositiveButton(R.string.dialog_ok) { _, _ ->
                viewModel.deleteReminder()
            }
            setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        }.create()
    }

    private fun setupBinding(binding: FragmentReminderEditorBinding) {
        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            model = viewModel
            reminder = viewModel.reminder.asLiveData(lifecycleScope.coroutineContext)

            viewModel.startDateState.observe(viewLifecycleOwner) {
                if (it.errorEnabled) {
                    textlayoutEditorStartDate.error =
                        "Start date must be in future and after end date"
                } else {
                    textlayoutEditorStartDate.error = ""
                }
            }
            viewModel.endDateState.observe(viewLifecycleOwner) {
                if (it.errorEnabled) {
                    textlayoutEditorEndDate.error =
                        "End date must be in future and after start date"
                } else {
                    textlayoutEditorEndDate.error = ""
                }
            }
            viewModel.timeState.observe(viewLifecycleOwner) {
                if (it.errorEnabled) {
                    if (viewModel.timeState.value?.dirty == true) {
                        textlayoutEditorTime.error = "Time cannot be in the past"
                    }
                } else {
                    textlayoutEditorTime.error = ""
                }
            }
            viewModel.daysOfWeekState.observe(viewLifecycleOwner) {
                if (it.errorEnabled) {
                    textlayoutEditorDaysOfWeek.error = "Select at least one day of week"
                } else {
                    textlayoutEditorDaysOfWeek.error = ""
                }
            }

            textinputEditorStartDate.setOnClickListener {
                selectDate(it)
            }
            textinputEditorEndDate.setOnClickListener {
                selectDate(it)
            }
            textinputEditorTime.setOnClickListener {
                selectTime()
            }
            textinputEditorDaysOfWeek.setOnClickListener {
                selectDaysOfWeek()
            }
            textinputEditorSound.setOnClickListener {
                selectSound()
            }
            textinputEditorSnooze.setOnClickListener {
                selectSnooze()
            }

            checkboxEditorRepeat.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setRepeat(isChecked)
            }
            checkboxEditorEndDate.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setHasEndDate(isChecked)
            }
        }
    }

    private fun selectDate(view: View) {
        val datePickerBuilder =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")

        val datePicker = when (view.id) {
            R.id.textinput_editor_startDate -> {
                // Only allow start dates beginning from today
                val constraintsBuilder =
                    CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now())
                        .build()

                datePickerBuilder.setSelection(
                    viewModel.reminder.value.schedulingRules.startDate.atTime(
                        LocalTime.now()
                    ).toEpochMilli(ZoneId.of("Z"))
                ).setCalendarConstraints(constraintsBuilder)
                datePickerBuilder.build().apply {
                    addOnPositiveButtonClickListener {
                        viewModel.onStartDateChanged(it.toLocalDate(ZoneId.of("Z")))
                    }
                }
            }
            R.id.textinput_editor_endDate -> {
                // Only allow end dates beginning from the start date
                val constraintsBuilder =
                    CalendarConstraints.Builder().setValidator(
                        DateValidatorPointForward.from(
                            viewModel.reminder.value.schedulingRules.startDate.atTime(LocalTime.now())
                                .toEpochMilli(ZoneId.of("Z"))
                        )
                    ).build()

                datePickerBuilder.setSelection(
                    viewModel.reminder.value.schedulingRules.endDate.atTime(
                        LocalTime.now()
                    ).toEpochMilli(ZoneId.of("Z"))
                ).setCalendarConstraints(constraintsBuilder)
                datePickerBuilder.build().apply {
                    addOnPositiveButtonClickListener {
                        viewModel.onEndDateChanged(it.toLocalDate(ZoneId.of("Z")))
                    }
                }
            }
            else -> throw IllegalStateException("selectDate called with unexpected view")
        }

        datePicker.show(parentFragmentManager, "datePicker")
    }

    private fun selectTime() {
        val previousSelectedTime = viewModel.reminder.value.schedulingRules.time
        val timePicker =
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(previousSelectedTime.hour)
                .setMinute(previousSelectedTime.minute)
                .setTitleText("Select time")
                .build()

        timePicker.addOnPositiveButtonClickListener {
            viewModel.onTimeChanged(LocalTime.of(timePicker.hour, timePicker.minute))
        }

        timePicker.show(parentFragmentManager, "timePicker")
    }

    private fun selectDaysOfWeek() {
        val dialog =
            DayOfWeekDialogFragment(
                if (viewModel.isEditing || viewModel.daysOfWeekState.value?.dirty == true) {
                    viewModel.reminder.value.schedulingRules.daysOfWeek
                } else sortedSetOf()
            )
        dialog.show(parentFragmentManager, "dialogDaysOfWeek")
    }

    private fun selectSound() {
        val dialog = SoundPickerDialogFragment(
            viewModel.reminder.value.vibrate,
            viewModel.reminder.value.notifSoundUri
        )
        dialog.show(parentFragmentManager, "dialogSoundPicker")
    }

    private fun selectSnooze() {
        val dialog = SnoozeOptionsDialogFragment(
            viewModel.reminder.value.snoozeRules
        )
        dialog.show(parentFragmentManager, "dialogSnooze")
    }
}
