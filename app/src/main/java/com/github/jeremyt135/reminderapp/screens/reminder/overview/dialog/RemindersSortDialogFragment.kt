package com.github.jeremyt135.reminderapp.screens.reminder.overview.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderSortRules
import com.github.jeremyt135.reminderapp.screens.reminder.overview.SortRule
import timber.log.Timber

class RemindersSortDialogFragment
/**
 * Constructs a new DialogFragment for controlling how to sort a reminder list.
 * @param previousSortRules The previously set rules to initialize the form with.
 */
constructor(previousSortRules: ReminderSortRules) : DialogFragment() {
    private lateinit var listener: DialogListener

    var reminderSortRules = previousSortRules
        private set

    /**
     * Interface for parent activity/fragment to implement in order to
     * observe changes.
     */
    interface DialogListener {
        /**
         * Called when the sorting rules have been updated.
         * @param dialog This dialog instance
         */
        fun onSortRulesChange(dialog: RemindersSortDialogFragment)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            // custom dialog body
            val view = inflater.inflate(R.layout.dialog_sort_reminders, null)
            setupView(view)
            builder.apply {
                setView(view)
                setTitle(R.string.dialog_reminderSort_title)
                setPositiveButton(R.string.dialog_apply) { _, _ ->
                    listener.onSortRulesChange(this@RemindersSortDialogFragment)
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
        val sortTimeGroup: RadioGroup = view.findViewById(R.id.radioGroup_dialogReminderSort_time)!!
        val sortDescriptionGroup: RadioGroup = view.findViewById(R.id.radioGroup_dialogReminderSort_description)!!

        setupTimeRadioGroup(sortTimeGroup)
        setupDescriptionRadioGroup(sortDescriptionGroup)
    }

    private fun setupTimeRadioGroup(group: RadioGroup) {
        group.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioButton_dialogReminderSort_time_none -> {
                    reminderSortRules = reminderSortRules.copy(time = SortRule.NONE)
                }
                R.id.radioButton_dialogReminderSort_time_asc -> {
                    reminderSortRules = reminderSortRules.copy(time = SortRule.ASCENDING)
                }
                R.id.radioButton_dialogReminderSort_time_desc -> {
                    reminderSortRules = reminderSortRules.copy(time = SortRule.DESCENDING)
                }
            }
        }

        group.check(
            when (reminderSortRules.time) {
                SortRule.NONE -> R.id.radioButton_dialogReminderSort_time_none
                SortRule.ASCENDING -> R.id.radioButton_dialogReminderSort_time_asc
                SortRule.DESCENDING -> R.id.radioButton_dialogReminderSort_time_desc
            }
        )
    }

    private fun setupDescriptionRadioGroup(group: RadioGroup) {
        group.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioButton_dialogReminderSort_description_none -> {
                    reminderSortRules = reminderSortRules.copy(description = SortRule.NONE)
                }
                R.id.radioButton_dialogReminderSort_description_alpha -> {
                    reminderSortRules = reminderSortRules.copy(description = SortRule.ASCENDING)
                }
                R.id.radioButton_dialogReminderSort_description_revAlpha -> {
                    reminderSortRules = reminderSortRules.copy(description = SortRule.DESCENDING)
                }
            }
        }

        group.check(
            when (reminderSortRules.description) {
                SortRule.NONE -> R.id.radioButton_dialogReminderSort_description_none
                SortRule.ASCENDING -> R.id.radioButton_dialogReminderSort_description_alpha
                SortRule.DESCENDING -> R.id.radioButton_dialogReminderSort_description_revAlpha
            }
        )
    }
}
