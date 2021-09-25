package com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog

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
import com.github.jeremyt135.databinding.DialogDayBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.time.DayOfWeek
import java.util.*

/**
 * Dialog body for selecting days of week for a reminder to repeat on.
 */
class DayOfWeekDialogFragment() :
    DialogFragment() {
    private lateinit var listener: DialogListener
    private lateinit var binding: DialogDayBinding
    private var previousDaysOfWeek: SortedSet<DayOfWeek> = sortedSetOf()
    private val viewModel: DialogDayViewModel by viewModels {
        DialogDayViewModel.Factory(
            previousDaysOfWeek
        )
    }

    constructor(previousDaysOfWeek: SortedSet<DayOfWeek>) : this() {
        this.previousDaysOfWeek = previousDaysOfWeek
    }

    /**
     * Listener interface for host activity to implement.
     */
    interface DialogListener {
        fun onDaysOfWeekChanged(newValue: SortedSet<DayOfWeek>)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = LayoutInflater.from(context)
        binding = DialogDayBinding.inflate(inflater)

        return builder.apply {
            setView(binding.root)
            setTitle(R.string.dialog_dayOfWeek_title)
            setPositiveButton(R.string.dialog_apply) { _, _ ->
                listener.onDaysOfWeekChanged(viewModel.daysOfWeek.value)
            }
            setNegativeButton(R.string.dialog_cancel) { _, _ ->
                Timber.d("DayOfWeekDialog cancel pressed")
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
        setupBinding(binding)
    }

    private fun setupBinding(binding: DialogDayBinding) {
        binding.apply {
            lifecycleOwner = requireActivity()
            model = viewModel
            daysOfWeek = viewModel.daysOfWeek.asLiveData()

            listOf(
                checkboxDialogDayMonday to DayOfWeek.MONDAY,
                checkboxDialogDayTuesday to DayOfWeek.TUESDAY,
                checkboxDialogDayWednesday to DayOfWeek.WEDNESDAY,
                checkboxDialogDayThursday to DayOfWeek.THURSDAY,
                checkboxDialogDayFriday to DayOfWeek.FRIDAY,
                checkboxDialogDaySaturday to DayOfWeek.SATURDAY,
                checkboxDialogDaySunday to DayOfWeek.SUNDAY
            ).forEach { (checkbox, dayOfWeek) ->
                checkbox.setOnClickListener {
                    viewModel.toggleDay(dayOfWeek)
                }
            }

            checkboxDialogDayEveryday.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleEveryDay(isChecked)
            }
        }
    }
}

class DialogDayViewModel constructor(daysOfWeek: SortedSet<DayOfWeek>) : ViewModel() {
    private val daysInWeek = DayOfWeek.values().size

    /**
     * Days of week selected before checking & unchecking "Every day"
     */
    private var savedDaysOfWeek: SortedSet<DayOfWeek> = daysOfWeek

    private val _daysOfWeek = MutableStateFlow(daysOfWeek)
    val daysOfWeek = _daysOfWeek.asStateFlow()

    private val _isEveryday = MutableStateFlow(daysOfWeek.size == daysInWeek)
    val isEveryDay = _isEveryday.asLiveData()

    fun toggleEveryDay(enabled: Boolean) {
        _isEveryday.value = enabled
        if (enabled) {
            savedDaysOfWeek = _daysOfWeek.value
            _daysOfWeek.value = DayOfWeek.values().toSortedSet()
        } else {
            _daysOfWeek.value = savedDaysOfWeek
            savedDaysOfWeek = sortedSetOf()
        }
    }

    fun toggleDay(dayOfWeek: DayOfWeek) {
        _daysOfWeek.value = _daysOfWeek.value.toSortedSet().apply {
            if (dayOfWeek !in this) {
                add(dayOfWeek)
            } else {
                remove(dayOfWeek)
            }
        }
        _isEveryday.value = _daysOfWeek.value.size == daysInWeek
    }

    class Factory(private val previousDaysOfWeek: SortedSet<DayOfWeek>) :
        ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            (DialogDayViewModel(previousDaysOfWeek) as T)
    }
}