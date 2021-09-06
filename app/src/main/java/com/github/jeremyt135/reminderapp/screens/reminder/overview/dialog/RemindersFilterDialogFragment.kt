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
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        // custom dialog body
        val view = inflater.inflate(R.layout.dialog_filter_reminders, null)
        setupView(view)
        return builder.apply {
            setView(view)
            setTitle(R.string.dialog_reminderFilter_title)
            setPositiveButton(R.string.dialog_apply) { _, _ ->
                listener.onFilterRulesChange(this@RemindersFilterDialogFragment)
            }
            setNegativeButton(R.string.dialog_cancel) { _, _ ->
                Timber.d("User cancelled sorting")
            }
        }.create()
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
        // set up initial value and listener for "today" checkbox
        view.findViewById<CheckBox>(R.id.checkbox_dialogReminderFilter_today).let {
            it.isChecked = reminderFilterRules.includeToday
            it.setOnCheckedChangeListener { _, isChecked ->
                reminderFilterRules = reminderFilterRules.copy(includeToday = isChecked)
            }
        }

        // set up initial value and listeners for day of week checkboxes
        listOf(
            (R.id.checkbox_dialogReminderFilter_monday to DayOfWeek.MONDAY),
            (R.id.checkbox_dialogReminderFilter_tuesday to DayOfWeek.TUESDAY),
            (R.id.checkbox_dialogReminderFilter_wednesday to DayOfWeek.WEDNESDAY),
            (R.id.checkbox_dialogReminderFilter_thursday to DayOfWeek.THURSDAY),
            (R.id.checkbox_dialogReminderFilter_friday to DayOfWeek.FRIDAY),
            (R.id.checkbox_dialogReminderFilter_saturday to DayOfWeek.SATURDAY),
            (R.id.checkbox_dialogReminderFilter_sunday to DayOfWeek.SUNDAY)
        ).forEach { (id, dayOfWeek) ->
            view.findViewById<CheckBox>(id).let {
                it.isChecked = reminderFilterRules.daysOfWeek.contains(dayOfWeek)
                it.setOnCheckedChangeListener { _, isChecked ->
                    onDayOfWeekChecked(dayOfWeek, isChecked)
                }
            }
        }
    }

    private fun onDayOfWeekChecked(dayOfWeek: DayOfWeek, checked: Boolean) {
        reminderFilterRules = if (checked) {
            val daysOfWeek = reminderFilterRules.daysOfWeek + setOf(dayOfWeek)
            reminderFilterRules.copy(daysOfWeek = daysOfWeek)
        } else {
            reminderFilterRules.copy(
                daysOfWeek = reminderFilterRules.daysOfWeek.filterTo(mutableSetOf()) {
                    it != dayOfWeek
                })
        }
    }

    private fun setupStatus(view: View) {
        // set up default value and listeners for each status enum
        listOf(
            (R.id.checkbox_dialogReminderFilter_pending to ReminderStatus.PENDING),
            (R.id.checkbox_dialogReminderFilter_skipped to ReminderStatus.SKIPPED),
            (R.id.checkbox_dialogReminderFilter_completed to ReminderStatus.COMPLETED)
        ).forEach { (id, status) ->
            view.findViewById<CheckBox>(id).let {
                it.isChecked = reminderFilterRules.reminderStatuses.contains(status)
                it.setOnCheckedChangeListener { _, isChecked ->
                    onStatusChecked(
                        status,
                        isChecked
                    )
                }
            }
        }
    }

    private fun onStatusChecked(status: ReminderStatus, checked: Boolean) {
        reminderFilterRules = if (checked) {
            val statuses = reminderFilterRules.reminderStatuses + setOf(status)
            reminderFilterRules.copy(reminderStatuses = statuses)
        } else {
            reminderFilterRules.copy(
                reminderStatuses = reminderFilterRules.reminderStatuses.filterTo(mutableSetOf()) {
                    it != status
                })
        }
    }
}
