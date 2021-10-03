package com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.view.LayoutInflater
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.DialogSnoozeOptionsBinding
import com.github.jeremyt135.reminderapp.data.domain.SnoozeRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Dialog body for choosing snooze options.
 */
class SnoozeOptionsDialogFragment() :
    DialogFragment() {
    private lateinit var listener: DialogListener
    private lateinit var binding: DialogSnoozeOptionsBinding
    private var previousRules = SnoozeRules()
    private val viewModel: SnoozeOptionsViewModel by viewModels() {
        SnoozeOptionsViewModel.Factory(previousRules)
    }

    /**
     * Constructs a SnoozeOptionsDialogFragment.
     * @param previousRules SnoozeRules to initialize the fragment with.
     */
    constructor(previousRules: SnoozeRules) : this() {
        this.previousRules = previousRules
    }

    /**
     * Listener interface for host activity to implement.
     */
    interface DialogListener {
        fun onSnoozeRulesChanged(snoozeRules: SnoozeRules)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(context)
        binding = DialogSnoozeOptionsBinding.inflate(inflater)

        return builder.apply {
            setView(binding.root)
            setTitle(R.string.dialog_snooze_title)
            setPositiveButton(R.string.apply) { _, _ ->
                listener.onSnoozeRulesChanged(viewModel.snoozeRules.value)
            }
            setNegativeButton(R.string.cancel) { _, _ ->
                Timber.d("Snooze options cancel pressed")
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

    private fun setupBinding(binding: DialogSnoozeOptionsBinding) {
        binding.apply {
            lifecycleOwner = activity
            model = viewModel
            snoozeRules = viewModel.snoozeRules.asLiveData()

            textDialogViewDurationCaption.text = getString(
                R.string.dialogSnooze_duration_caption,
                SnoozeRules.MIN_SNOOZE_DURATION,
                SnoozeRules.MAX_SNOOZE_DURATION
            )

            radioGroupDialogSnoozeSnoozeOptions.check(
                when (viewModel.snoozeRules.value.maxRepeats) {
                    0L -> R.id.radioButton_dialogSnooze_never
                    Long.MAX_VALUE -> R.id.radioButton_dialogSnooze_always
                    else -> R.id.radioButton_dialogSnooze_number
                }
            )
            radioGroupDialogSnoozeSnoozeOptions.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.radioButton_dialogSnooze_never -> {
                        viewModel.setMaxRepeats(0)
                    }
                    R.id.radioButton_dialogSnooze_always -> {
                        viewModel.setMaxRepeats(Long.MAX_VALUE)
                    }
                    R.id.radioButton_dialogSnooze_number -> {
                        viewModel.setMaxRepeats(SnoozeRules.DEFAULT_SNOOZE_REPEATS)
                        viewModel.setSnoozeDuration(SnoozeRules.DEFAULT_SNOOZE_DURATION)
                    }
                }
            }

            editDialogSnoozeMaxSnoozeRepeats.filters = arrayOf(RangeInputFilter(1, Long.MAX_VALUE))
            editDialogSnoozeMaxSnoozeRepeats.doAfterTextChanged { editable ->
                editable?.let {
                    if (it.toString().isNotEmpty()) {
                        viewModel.setMaxRepeats(it.toString().toLong())
                    }
                }
            }

            editDialogSnoozeSnoozeDuration.filters = arrayOf(
                RangeInputFilter(SnoozeRules.MIN_SNOOZE_DURATION, SnoozeRules.MAX_SNOOZE_DURATION)
            )
            editDialogSnoozeSnoozeDuration.doAfterTextChanged { editable ->
                editable?.let {
                    if (it.toString().isNotEmpty()) {
                        viewModel.setSnoozeDuration(it.toString().toLong())
                    }
                }
            }
        }
    }
}

class SnoozeOptionsViewModel(previousRules: SnoozeRules) : ViewModel() {
    private val _snoozeRules = MutableStateFlow(previousRules)
    val snoozeRules = _snoozeRules.asStateFlow()

    val canSnooze = _snoozeRules.map { it.maxRepeats != 0L }.asLiveData()

    fun setMaxRepeats(maxRepeats: Long) {
        _snoozeRules.value = _snoozeRules.value.copy(maxRepeats = maxRepeats)
    }

    fun setSnoozeDuration(duration: Long) {
        _snoozeRules.value = _snoozeRules.value.copy(duration = duration)
    }

    class Factory(private val previousRules: SnoozeRules) :
        ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            SnoozeOptionsViewModel(previousRules) as T
    }
}

/**
 * Filter for snooze EditText that limits values to acceptable range
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