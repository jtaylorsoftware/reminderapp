package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.AttrRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.github.jeremyt135.databinding.ViewCalendarBinding
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderListAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import java.time.YearMonth

@AndroidEntryPoint
class CalendarView : ConstraintLayout {
    private val viewModel: CalendarViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!).get(CalendarViewModel::class.java)
    }
    private var binding: ViewCalendarBinding? =
        ViewCalendarBinding.inflate(LayoutInflater.from(context), this, true)
    private var reminderListAdapter: ReminderListAdapter? = null
    private var calendarAdapter: CalendarAdapter? = null
    private var coroutineScope: CoroutineScope? = null


    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int)
            : super(context, attrs, defStyleAttr)

    init {
        setupBindingViews()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachBinding()
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        startJobs()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachBinding()
        cancelJobs()
    }

    private fun setupBindingViews() {
        reminderListAdapter = ReminderListAdapter(ReminderListAdapter.OnClickListener {
//            viewModel.editReminder(it.reminder)
        })

        binding?.apply {
            recyclerCalendarReminders.adapter = reminderListAdapter

            buttonCalendarBackward.setOnClickListener {
                viewModel.goToPreviousMonth()
            }
            buttonCalendarForward.setOnClickListener {
                viewModel.goToNextMonth()
            }
        }
        setBindingCalendarAdapter(YearMonth.now())
    }

    private fun attachBinding() {
        binding?.apply {
            model = viewModel
            lifecycleOwner = findViewTreeLifecycleOwner()
        }
    }

    private fun detachBinding() {
        binding?.apply {
            lifecycleOwner = null
            model = null
        }
        binding = null
    }

    private fun setBindingCalendarAdapter(
        yearMonth: YearMonth,
        daysWithReminders: List<Boolean> = emptyList()
    ) {
        calendarAdapter = CalendarAdapter(yearMonth, daysWithReminders) {
            viewModel.setSelectedDate(it)
        }
        binding?.recyclerCalendar?.adapter = calendarAdapter
    }

    private fun startJobs() {
        coroutineScope?.launch {
            viewModel.daysWithReminders.collect {
                Timber.d("Setting daysWithReminders $it")
                setBindingCalendarAdapter(viewModel.selectedYearMonth.value, it)
            }
        }
        coroutineScope?.launch {
            viewModel.selectedYearMonth.collect {
                Timber.d("Setting selectedYearMonth $it")
                setBindingCalendarAdapter(it, viewModel.daysWithReminders.value)
            }
        }
    }

    private fun cancelJobs() {
        coroutineScope?.cancel()
        coroutineScope = null
    }
}