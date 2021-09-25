package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.AttrRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.recyclerview.widget.GridLayoutManager
import com.github.jeremyt135.databinding.ViewCalendarBinding
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderListAdapter
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.LocalDate

typealias OnReminderSelectedListener = (Reminder) -> Unit
typealias OnAddReminderListener = (LocalDate?) -> Unit

@AndroidEntryPoint
class CalendarView : ConstraintLayout {
    private val viewModel: CalendarViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!).get(CalendarViewModel::class.java)
    }
    private var binding: ViewCalendarBinding? =
        ViewCalendarBinding.inflate(LayoutInflater.from(context), this, true)
    private var reminderListAdapter: ReminderListAdapter? = null
    private var calendarAdapter: CalendarAdapter? = null

    var onReminderSelectedListener: OnReminderSelectedListener? = null
    var onAddReminderListener: OnAddReminderListener? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int)
            : super(context, attrs, defStyleAttr)

    init {
        Timber.d("Init")
        setupBindingViews()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachBinding()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachBinding()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        Timber.d("onVisibilityChanged")
    }

    /**
     * Refreshes the data in the calendar.
     */
    fun refreshData() {
        viewModel.refreshData()
    }

    /**
     * Deselects the currently selected date.
     */
    fun clearSelectedDay() {
        Timber.d("ClearSelectedDay")
        viewModel.setSelectedDate(null)
        calendarAdapter?.clearSelectedDay()
    }

    private fun setupBindingViews() {
        Timber.d("setupBindingViews")
        reminderListAdapter = ReminderListAdapter(ReminderListAdapter.OnClickListener {
            onReminderSelectedListener?.invoke(it.reminder)
        })

        binding?.apply {
            recyclerCalendarReminders.adapter = reminderListAdapter

            // Use a GLM with Calendar's RecyclerView that has disabled scrolling in both directions
            recyclerCalendar.layoutManager =
                object : GridLayoutManager(context, CalendarAdapter.COLUMNS) {
                    override fun canScrollVertically() = false
                    override fun canScrollHorizontally() = false
                }

            calendarAdapter = CalendarAdapter() {
                viewModel.setSelectedDate(it)
            }
            recyclerCalendar.adapter = calendarAdapter

            buttonCalendarBackward.setOnClickListener {
                viewModel.goToPreviousMonth()
            }
            buttonCalendarForward.setOnClickListener {
                viewModel.goToNextMonth()
            }

            buttonCalendarAdd.setOnClickListener {
                onAddReminderListener?.invoke(calendarAdapter?.selectedDate)
            }
        }
    }

    private fun attachBinding() {
        Timber.d("attachBinding")
        binding?.apply {
            model = viewModel
            lifecycleOwner = findViewTreeLifecycleOwner()
        }
    }

    private fun detachBinding() {
        Timber.d("detachBinding")
        binding = null
    }
}