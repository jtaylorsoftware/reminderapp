package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.app.AlertDialog
import android.content.res.Configuration
import android.os.Bundle
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

    private val startDatePickerTag = "startDatePicker"
    private val endDatePickerTag = "endDatePicker"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("onViewCreated")
        with(activity as? AppCompatActivity) {
            this?.supportActionBar?.title =
                if ((this?.intent?.getStringExtra(EXTRA_REMINDER_ID) ?: "").isNotEmpty()) {
                    getString(R.string.editor_heading_edit)
                } else {
                    getString(R.string.editor_heading_add)
                }
        }

        setupBinding(FragmentReminderEditorBinding.bind(view))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        Timber.d("onConfigurationChanged")

        // Recreate pickers because orientation changes remove their listeners. Also
        // handling configChanges="orientation|keyboardHidden|screenSize" makes them not resize appropriately.
        recreateStartDatePicker()
        recreateEndDatePicker()
        recreateTimePicker()
    }

    @Suppress("UNCHECKED_CAST")
    private fun recreateStartDatePicker() {
        val startDatePicker =
            childFragmentManager.findFragmentByTag("startDatePicker") as? MaterialDatePicker<Long>
        Timber.d("startDatePicker: $startDatePicker")
        startDatePicker?.addOnDismissListener {
            createStartDatePicker().show(childFragmentManager, startDatePickerTag)
        }
        startDatePicker?.dismiss()
    }

    @Suppress("UNCHECKED_CAST")
    private fun recreateEndDatePicker() {
        val endDatePicker =
            childFragmentManager.findFragmentByTag("endDatePicker") as? MaterialDatePicker<Long>
        Timber.d("endDatePicker: $endDatePicker")
        endDatePicker?.addOnDismissListener {
            createEndDatePicker().show(childFragmentManager, endDatePickerTag)
        }
        endDatePicker?.dismiss()
    }

    private fun recreateTimePicker() {
        val timePicker = childFragmentManager.findFragmentByTag("timePicker") as? MaterialTimePicker
        Timber.d("timePicker: $timePicker")
        timePicker?.addOnDismissListener {
            createTimePicker().show(childFragmentManager, "timePicker")
        }
        timePicker?.dismiss()
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

            buttonEditorDelete.setOnClickListener {
                buildDeleteDialog()?.show()
            }
            buttonEditorSave.setOnClickListener {
                viewModel.saveReminder()
            }
        }
    }

    private fun buildDeleteDialog(): AlertDialog? {
        return activity?.let {
            AlertDialog.Builder(it).apply {
                setMessage(R.string.dialog_editorDelete_message)
                setTitle(R.string.dialog_editorDelete_title)
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    viewModel.deleteReminder()
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            }.create()
        }
    }

    private fun selectDate(view: View) {
        val tag: String
        val datePicker = when (view.id) {
            R.id.textinput_editor_startDate -> {
                tag = startDatePickerTag
                createStartDatePicker()
            }
            R.id.textinput_editor_endDate -> {
                tag = endDatePickerTag
                createEndDatePicker()
            }
            else -> throw IllegalStateException("selectDate called with unexpected view")
        }

        datePicker.show(childFragmentManager, tag)
    }

    private fun createStartDatePicker(): MaterialDatePicker<*> {
        val datePickerBuilder =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select start date")

        // Only allow start dates beginning from today
        val constraintsBuilder =
            CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now())
                .build()

        datePickerBuilder.setSelection(
            viewModel.reminder.value.schedulingRules.startDate.atTime(
                LocalTime.now()
            ).toEpochMilli(ZoneId.of("Z"))
        ).setCalendarConstraints(constraintsBuilder)
        return datePickerBuilder.build().apply {
            addOnPositiveButtonClickListener {
                viewModel.onStartDateChanged(it.toLocalDate(ZoneId.of("Z")))
            }
        }
    }

    private fun createEndDatePicker(): MaterialDatePicker<*> {
        val datePickerBuilder =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select end date")

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
        return datePickerBuilder.build().apply {
            addOnPositiveButtonClickListener {
                viewModel.onEndDateChanged(it.toLocalDate(ZoneId.of("Z")))
            }
        }
    }

    private fun selectTime() {
        createTimePicker().show(childFragmentManager, "timePicker")
    }

    private fun createTimePicker(): MaterialTimePicker {
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

        return timePicker
    }

    private fun selectDaysOfWeek() {
        val dialog =
            DayOfWeekDialogFragment(
                if (viewModel.isEditing || viewModel.daysOfWeekState.value?.dirty == true) {
                    viewModel.reminder.value.schedulingRules.daysOfWeek
                } else sortedSetOf()
            )
        dialog.show(childFragmentManager, "dialogDaysOfWeek")
    }

    private fun selectSound() {
        val dialog = SoundPickerDialogFragment(
            viewModel.reminder.value.vibrate,
            viewModel.reminder.value.notifSoundUri
        )
        dialog.show(childFragmentManager, "dialogSoundPicker")
    }

    private fun selectSnooze() {
        val dialog = SnoozeOptionsDialogFragment(
            viewModel.reminder.value.snoozeRules
        )
        dialog.show(childFragmentManager, "dialogSnooze")
    }
}
