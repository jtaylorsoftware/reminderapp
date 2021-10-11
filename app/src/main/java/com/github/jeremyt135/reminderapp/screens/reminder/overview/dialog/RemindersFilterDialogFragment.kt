package com.github.jeremyt135.reminderapp.screens.reminder.overview.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.DialogFilterRemindersBinding
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderFilterRules
import com.github.jeremyt135.reminderapp.util.getShortLocalDateDisplay
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDate
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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
         * @param filterRules The new filter rules
         */
        fun onFilterRulesChange(filterRules: ReminderFilterRules)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(context)
        binding = DialogFilterRemindersBinding.inflate(inflater)

        return builder.apply {
            setView(binding.root)
            setTitle(R.string.dialog_reminderFilter_title)
            setPositiveButton(R.string.apply) { _, _ ->
                listener.onFilterRulesChange(viewModel.filterRules.value)
            }
            setNegativeButton(R.string.cancel) { _, _ ->
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

    override fun onCreateContextMenu(
        menu: ContextMenu,
        view: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, view, menuInfo)
        menu.add(Menu.NONE, view.id, 0, getString(R.string.reset)).apply {
            setOnMenuItemClickListener {
                resetDate(itemId)
                true
            }
        }
    }

    private fun resetDate(viewId: Int) {
        when (viewId) {
            R.id.text_dialogReminderFilter_startDate -> viewModel.setStartDate(null)
            R.id.text_dialogReminderFilter_endDate -> viewModel.setEndDate(null)
        }
    }

    private fun setupBinding() {
        binding.apply {
            lifecycleOwner = activity
            model = viewModel
            filterRules = viewModel.filterRules.asLiveData()
        }

        setupDaysOfWeek()
        setupStatus()
        setupDatePickers()
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

    private fun setupDatePickers() {
        binding.apply {
            // Register date text views to display a context menu with "reset" option
            registerForContextMenu(textDialogReminderFilterStartDate)
            registerForContextMenu(textDialogReminderFilterEndDate)

            // Set onClick listeners to open a date picker
            textDialogReminderFilterStartDate.setOnClickListener {
                selectDate(it)
            }
            textDialogReminderFilterEndDate.setOnClickListener {
                selectDate(it)
            }
        }
    }

    private fun selectDate(view: View) {
        val datePickerBuilder =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")

        val datePicker = when (view.id) {
            R.id.text_dialogReminderFilter_startDate -> {
                datePickerBuilder.run {
                    setSelection(
                        (viewModel.filterRules.value.startDate
                            ?: LocalDate.now()).atTime(LocalTime.now()).toEpochMilli(ZoneId.of("Z"))
                    )
                    build()
                }.apply {
                    addOnPositiveButtonClickListener {
                        viewModel.setStartDate(it.toLocalDate(ZoneId.of("Z")))
                    }
                }
            }
            R.id.text_dialogReminderFilter_endDate -> {
                datePickerBuilder.run {
                    setSelection(
                        (viewModel.filterRules.value.endDate
                            ?: LocalDate.now()).atTime(LocalTime.now()).toEpochMilli(ZoneId.of("Z"))
                    )
                    build()
                }.apply {
                    addOnPositiveButtonClickListener {
                        viewModel.setEndDate(it.toLocalDate(ZoneId.of("Z")))
                    }
                }
            }
            else -> throw IllegalStateException("selectDate called with unexpected view")
        }
        datePicker.show(parentFragmentManager, "datePicker")
    }
}

class ReminderFilterViewModel constructor(previousFilterRules: ReminderFilterRules) :
    ViewModel() {
    private val _filterRules = MutableStateFlow(previousFilterRules)
    val filterRules = _filterRules.asStateFlow()

    val startDateString = _filterRules.map {
        it.startDate?.getShortLocalDateDisplay() ?: "Select date"
    }.asLiveData()

    val endDateString = _filterRules.map {
        it.endDate?.getShortLocalDateDisplay() ?: "Select date"
    }.asLiveData()

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

    fun setStartDate(date: LocalDate?) {
        with(_filterRules.value) {
            if (date != null && endDate != null && date > endDate) {
                // swap new start date to be new end date
                val start = endDate
                _filterRules.value = _filterRules.value.copy(startDate = start, endDate = date)
            } else {
                _filterRules.value = _filterRules.value.copy(startDate = date)
            }
        }
    }

    fun setEndDate(date: LocalDate?) {
        with(_filterRules.value) {
            if (date != null && startDate != null && date < startDate) {
                // swap new end date to be new start date
                val end = startDate
                _filterRules.value = _filterRules.value.copy(startDate = date, endDate = end)
            } else {
                _filterRules.value = _filterRules.value.copy(endDate = date)
            }
        }
    }

    class Factory(private val previousFilterRules: ReminderFilterRules) :
        ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            ReminderFilterViewModel(previousFilterRules) as T
    }
}