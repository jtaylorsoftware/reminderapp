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
import androidx.lifecycle.asLiveData
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.DialogFilterRemindersBinding
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderFilterRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.time.DayOfWeek
import java.util.*

class RemindersFilterDialogFragment() : DialogFragment() {
    private lateinit var listener: DialogListener
    private lateinit var binding: DialogFilterRemindersBinding

    private var previousFilterRules: ReminderFilterRules = ReminderFilterRules()
    private val viewModel: ReminderFilterViewModel by viewModels {
        ReminderFilterViewModel.Factory(previousFilterRules)
    }

    /**
     * Constructs a new DialogFragment for controlling how to filter a reminder list.
     * @param previousFilterRules The previously set rules to initialize the form with.
     */
    constructor(previousFilterRules: ReminderFilterRules) : this() {
        this.previousFilterRules = previousFilterRules
    }

    /**
     * Interface for parent activity/fragment to implement in order to
     * observe changes.
     */
    interface DialogListener {
        /**
         * Called when the filtering rules have been updated.
         * @param dialog This dialog instance
         */
        fun onFilterRulesChange(filterRules: ReminderFilterRules)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = LayoutInflater.from(context)
        binding = DialogFilterRemindersBinding.inflate(inflater)

        return builder.apply {
            setView(binding.root)
            setTitle(R.string.dialog_reminderFilter_title)
            setPositiveButton(R.string.dialog_apply) { _, _ ->
                listener.onFilterRulesChange(viewModel.filterRules.value)
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
            filterRules = viewModel.filterRules.asLiveData()
        }

        setupDaysOfWeek()
        setupStatus()
    }

    private fun setupDaysOfWeek() {
        binding.apply {
            // set up initial value and listener for "today" checkbox
            checkboxDialogReminderFilterToday.setOnClickListener {
                viewModel.toggleToday()
            }

            // set up initial value and listeners for day of week checkboxes
            listOf(
                checkboxDialogReminderFilterMonday to DayOfWeek.MONDAY,
                checkboxDialogReminderFilterTuesday to DayOfWeek.TUESDAY,
                checkboxDialogReminderFilterWednesday to DayOfWeek.WEDNESDAY,
                checkboxDialogReminderFilterThursday to DayOfWeek.THURSDAY,
                checkboxDialogReminderFilterFriday to DayOfWeek.FRIDAY,
                checkboxDialogReminderFilterSaturday to DayOfWeek.SATURDAY,
                checkboxDialogReminderFilterSunday to DayOfWeek.SUNDAY
            ).forEach { (checkbox, dayOfWeek) ->
                checkbox.setOnClickListener {
                    viewModel.toggleDayOfWeek(dayOfWeek)
                }
            }
        }
    }

    private fun setupStatus() {
        binding.apply {
            // set up default value and listeners for each status enum
            listOf(
                checkboxDialogReminderFilterPending to ReminderStatus.PENDING,
                checkboxDialogReminderFilterSkipped to ReminderStatus.SKIPPED,
                checkboxDialogReminderFilterCompleted to ReminderStatus.COMPLETED
            ).forEach { (checkbox, status) ->
                checkbox.setOnClickListener {
                    viewModel.toggleStatus(status)
                }
            }
        }
    }
}

class ReminderFilterViewModel constructor(previousFilterRules: ReminderFilterRules) : ViewModel() {
    private val _filterRules = MutableStateFlow(previousFilterRules)
    val filterRules = _filterRules.asStateFlow()

    fun toggleToday() {
        _filterRules.value = _filterRules.value.let {
            it.copy(includeToday = !it.includeToday)
        }
    }

    fun toggleStatus(status: ReminderStatus) {
        _filterRules.value = _filterRules.value.let {
            it.copy(reminderStatuses = it.reminderStatuses.toMutableSet().apply {
                if (status !in this) {
                    add(status)
                } else {
                    remove(status)
                }
            })
        }
    }

    fun toggleDayOfWeek(dayOfWeek: DayOfWeek) {
        _filterRules.value = _filterRules.value.let {
            it.copy(daysOfWeek = it.daysOfWeek.toMutableSet().apply {
                if (dayOfWeek !in this) {
                    add(dayOfWeek)
                } else {
                    remove(dayOfWeek)
                }
            })
        }
    }

    class Factory(private val previousFilterRules: ReminderFilterRules) :
        ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            ReminderFilterViewModel(previousFilterRules) as T
    }
}