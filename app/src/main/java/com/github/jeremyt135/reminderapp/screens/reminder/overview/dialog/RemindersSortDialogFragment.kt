package com.github.jeremyt135.reminderapp.screens.reminder.overview.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.DialogSortRemindersBinding
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderSortRules
import com.github.jeremyt135.reminderapp.screens.reminder.overview.SortRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class RemindersSortDialogFragment() : DialogFragment() {
    private lateinit var listener: DialogListener
    private lateinit var binding: DialogSortRemindersBinding

    private var previousSortRules = ReminderSortRules()
    private val viewModel: ReminderSortViewModel by viewModels {
        ReminderSortViewModel.Factory(previousSortRules)
    }

    /**
     * Constructs a new DialogFragment for controlling how to sort a reminder list.
     * @param previousSortRules The previously set rules to initialize the form with.
     */
    constructor(previousSortRules: ReminderSortRules) : this() {
        this.previousSortRules = previousSortRules
    }

    /**
     * Interface for parent activity/fragment to implement in order to
     * observe changes.
     */
    interface DialogListener {
        /**
         * Called when the sorting rules have been updated.
         * @param sortRules The updated sorting rules
         */
        fun onSortRulesChange(sortRules: ReminderSortRules)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = LayoutInflater.from(context)
        binding = DialogSortRemindersBinding.inflate(inflater)

        return builder.apply {
            setView(binding.root)
            setTitle(R.string.dialog_reminderSort_title)
            setPositiveButton(R.string.dialog_apply) { _, _ ->
                listener.onSortRulesChange(viewModel.sortRules.value)
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

    override fun onStart() {
        super.onStart()
        setupBinding()
    }

    private fun setupBinding() {
        binding.apply {
            lifecycleOwner = requireActivity()
            model = viewModel
        }

        setupTimeRadioGroup()
        setupDescriptionRadioGroup()
    }

    private fun setupTimeRadioGroup() {
        binding.apply {
            radioGroupDialogReminderSortTime.check(
                when (viewModel.sortRules.value.time) {
                    SortRule.NONE -> R.id.radioButton_dialogReminderSort_time_none
                    SortRule.ASCENDING -> R.id.radioButton_dialogReminderSort_time_asc
                    SortRule.DESCENDING -> R.id.radioButton_dialogReminderSort_time_desc
                }
            )

            radioGroupDialogReminderSortTime.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.radioButton_dialogReminderSort_time_none -> {
                        viewModel.changeTimeSortRule(SortRule.NONE)
                    }
                    R.id.radioButton_dialogReminderSort_time_asc -> {
                        viewModel.changeTimeSortRule(SortRule.ASCENDING)
                    }
                    R.id.radioButton_dialogReminderSort_time_desc -> {
                        viewModel.changeTimeSortRule(SortRule.DESCENDING)
                    }
                }
            }

        }
    }

    private fun setupDescriptionRadioGroup() {
        binding.apply {
            radioGroupDialogReminderSortDescription.check(
                when (viewModel.sortRules.value.description) {
                    SortRule.NONE -> R.id.radioButton_dialogReminderSort_description_none
                    SortRule.ASCENDING -> R.id.radioButton_dialogReminderSort_description_alpha
                    SortRule.DESCENDING -> R.id.radioButton_dialogReminderSort_description_revAlpha
                }
            )

            radioGroupDialogReminderSortDescription.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.radioButton_dialogReminderSort_description_none -> {
                        viewModel.changeDescriptionSortRule(SortRule.NONE)
                    }
                    R.id.radioButton_dialogReminderSort_description_alpha -> {
                        viewModel.changeDescriptionSortRule(SortRule.ASCENDING)
                    }
                    R.id.radioButton_dialogReminderSort_description_revAlpha -> {
                        viewModel.changeDescriptionSortRule(SortRule.DESCENDING)
                    }
                }
            }
        }
    }
}


class ReminderSortViewModel constructor(previousSortRules: ReminderSortRules) : ViewModel() {
    private val _sortRules = MutableStateFlow(previousSortRules)
    val sortRules = _sortRules.asStateFlow()

    fun changeTimeSortRule(sortRule: SortRule) {
        _sortRules.value = _sortRules.value.copy(time = sortRule)
    }

    fun changeDescriptionSortRule(sortRule: SortRule) {
        _sortRules.value = _sortRules.value.copy(description = sortRule)
    }

    class Factory(private val previousSortRules: ReminderSortRules) :
        ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            ReminderSortViewModel(previousSortRules) as T
    }
}