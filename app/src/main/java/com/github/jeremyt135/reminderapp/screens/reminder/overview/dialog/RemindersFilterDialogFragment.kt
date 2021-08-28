package com.github.jeremyt135.reminderapp.screens.reminder.overview.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.fragment.app.DialogFragment
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderFilterRules
import timber.log.Timber
import java.time.DayOfWeek

class RemindersFilterDialogFragment
/**
 * Constructs a new DialogFragment for controlling how to filter a reminder list.
 * @param previousFilterRules The previously set rules to initialize the form with.
 */
constructor(previousFilterRules: ReminderFilterRules) : DialogFragment() {
    private lateinit var listener: DialogListener

    var reminderFilterRules = previousFilterRules
        private set

    /**
     * Interface for parent activity/fragment to implement in order to
     * observe changes.
     */
    interface DialogListener {
        /**
         * Called when the filtering rules have been updated.
         * @param dialog This dialog instance
         */
        fun onFilterRulesChange(dialog: RemindersFilterDialogFragment)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            // custom dialog body
            val view = inflater.inflate(R.layout.dialog_filter_reminders, null)
            setupView(view)
            builder.apply {
                setView(view)
                setTitle(R.string.dialog_reminderSort_title)
                setPositiveButton(R.string.dialog_apply) { _, _ ->
                    listener.onFilterRulesChange(this@RemindersFilterDialogFragment)
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ ->
                    Timber.d("User cancelled sorting")
                }
            }.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        try {
            listener = context as DialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement DialogListener")
        }
    }

    private fun setupView(view: View) {
        setupDaysOfWeek(view)
        setupStatus(view)
    }

    private fun setupDaysOfWeek(view: View) {
        // TODO - programatically make checkboxes or set from a viewmodel
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_today).let {
            it.isChecked = reminderFilterRules.includeToday
            it.setOnCheckedChangeListener { _, isChecked ->
                reminderFilterRules = reminderFilterRules.copy(includeToday = isChecked)
            }
        }
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_monday).let {
            it.isChecked = reminderFilterRules.daysOfWeek.contains(DayOfWeek.MONDAY)
            it.setOnCheckedChangeListener { _, isChecked ->
                onDayOfWeekChecked(
                    DayOfWeek.MONDAY,
                    isChecked
                )
            }
        }
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_tuesday).let {
            it.isChecked = reminderFilterRules.daysOfWeek.contains(DayOfWeek.TUESDAY)
            it.setOnCheckedChangeListener { _, isChecked ->
                onDayOfWeekChecked(
                    DayOfWeek.TUESDAY,
                    isChecked
                )
            }
        }
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_wednesday).let {
            it.isChecked = reminderFilterRules.daysOfWeek.contains(DayOfWeek.WEDNESDAY)
            it.setOnCheckedChangeListener { _, isChecked ->
                onDayOfWeekChecked(
                    DayOfWeek.WEDNESDAY,
                    isChecked
                )
            }
        }
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_thursday).let {
            it.isChecked = reminderFilterRules.daysOfWeek.contains(DayOfWeek.THURSDAY)
            it.setOnCheckedChangeListener { _, isChecked ->
                onDayOfWeekChecked(
                    DayOfWeek.THURSDAY,
                    isChecked
                )
            }
        }
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_friday).let {
            it.isChecked = reminderFilterRules.daysOfWeek.contains(DayOfWeek.FRIDAY)
            it.setOnCheckedChangeListener { _, isChecked ->
                onDayOfWeekChecked(
                    DayOfWeek.FRIDAY,
                    isChecked
                )
            }
        }
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_saturday).let {
            it.isChecked = reminderFilterRules.daysOfWeek.contains(DayOfWeek.SATURDAY)
            it.setOnCheckedChangeListener { _, isChecked ->
                onDayOfWeekChecked(
                    DayOfWeek.SATURDAY,
                    isChecked
                )
            }
        }
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_sunday).let {
            it.isChecked = reminderFilterRules.daysOfWeek.contains(DayOfWeek.SUNDAY)
            it.setOnCheckedChangeListener { _, isChecked ->
                onDayOfWeekChecked(
                    DayOfWeek.SUNDAY,
                    isChecked
                )
            }
        }
    }

    private fun onDayOfWeekChecked(dayOfWeek: DayOfWeek, checked: Boolean) {
        reminderFilterRules = if (checked) {
            val daysOfWeek = reminderFilterRules.daysOfWeek.toMutableSet()
            daysOfWeek.add(dayOfWeek)
            reminderFilterRules.copy(daysOfWeek = daysOfWeek)
        } else {
            reminderFilterRules.copy(
                daysOfWeek = reminderFilterRules.daysOfWeek.filterTo(mutableSetOf()) {
                    it != dayOfWeek
                })
        }
    }

    private fun setupStatus(view: View) {
        // TODO - programatically make checkboxes or set from a viewmodel
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_pending).let {
            it.isChecked = reminderFilterRules.reminderStatuses.contains(ReminderStatus.PENDING)
            it.setOnCheckedChangeListener { _, isChecked ->
                onStatusChecked(
                    ReminderStatus.PENDING,
                    isChecked
                )
            }
        }
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_skipped).let {
            it.isChecked = reminderFilterRules.reminderStatuses.contains(ReminderStatus.SKIPPED)
            it.setOnCheckedChangeListener { _, isChecked ->
                onStatusChecked(
                    ReminderStatus.SKIPPED,
                    isChecked
                )
            }
        }
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_completed).let {
            it.isChecked = reminderFilterRules.reminderStatuses.contains(ReminderStatus.COMPLETED)
            it.setOnCheckedChangeListener { _, isChecked ->
                onStatusChecked(
                    ReminderStatus.COMPLETED,
                    isChecked
                )
            }
        }
    }

    private fun onStatusChecked(status: ReminderStatus, checked: Boolean) {
        reminderFilterRules = if (checked) {
            val statuses = reminderFilterRules.reminderStatuses.toMutableSet()
            statuses.add(status)
            reminderFilterRules.copy(reminderStatuses = statuses)
        } else {
            reminderFilterRules.copy(
                reminderStatuses = reminderFilterRules.reminderStatuses.filterTo(mutableSetOf()) {
                    it != status
                })
        }
    }
}
