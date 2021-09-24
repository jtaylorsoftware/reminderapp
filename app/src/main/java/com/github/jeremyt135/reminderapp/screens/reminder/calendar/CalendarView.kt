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
    private var previouslyVisible: Boolean = false // Track if View has ever been visible

    var onReminderSelectedListener: OnReminderSelectedListener? = null
    var onAddReminderListener: OnAddReminderListener? = null

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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachBinding()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)

        if (visibility != View.VISIBLE) {
            clearSelectedDay()
        } else {
            // Only refresh if View has ever been visible
            if (previouslyVisible) {
                viewModel.refreshData()
            }
            previouslyVisible = true
        }
    }

    /**
     * Deselects the currently selected date.
     */
    private fun clearSelectedDay() {
        viewModel.setSelectedDate(null)
        calendarAdapter?.clearSelectedDay()
    }

    private fun setupBindingViews() {
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
        binding?.apply {
            model = viewModel
            lifecycleOwner = findViewTreeLifecycleOwner()
        }
    }

    private fun detachBinding() {
        binding = null
    }
}