package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.AttrRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.recyclerview.widget.GridLayoutManager
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.ViewCalendarBinding
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderListAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth

typealias OnReminderSelectedListener = (Reminder) -> Unit
typealias OnAddReminderListener = (LocalDate?) -> Unit

@AndroidEntryPoint
class CalendarView : ConstraintLayout {
    private val viewModel: CalendarViewModel by lazy {
        ViewModelProvider(requireNotNull(findViewTreeViewModelStoreOwner())).get(CalendarViewModel::class.java)
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

            buttonCalendarForward.let {
                it.setOnClickListener {
                    viewModel.goToNextMonth()
                }

                ImageViewCompat.setImageTintList(
                    it,
                    ColorStateList.valueOf(
                        ContextCompat.getColor(
                            context,
                            R.color.icon_button_tint_enable
                        )
                    )
                )
                ImageViewCompat.setImageTintMode(it, PorterDuff.Mode.SRC_ATOP)
            }

            buttonCalendarAdd.setOnClickListener {
                onAddReminderListener?.invoke(calendarAdapter?.selectedDate)
            }
        }
    }

    private var coroutineScope: CoroutineScope? = CoroutineScope(Dispatchers.Main)

    private fun attachBinding() {
        Timber.d("attachBinding")
        val lifecycleOwner = requireNotNull(findViewTreeLifecycleOwner())

        binding?.model = viewModel
        binding?.lifecycleOwner = lifecycleOwner

        coroutineScope = CoroutineScope(Dispatchers.Main).apply {
            launch {
                viewModel.selectedYearMonth.collectLatest { yearMonth ->
                    // Disable button if current month is selected, so user cannot go into past
                    val enabled = yearMonth > YearMonth.now()
                    Timber.d("buttonCalendarBackward enabled = $enabled")

                    binding?.buttonCalendarBackward?.isEnabled = enabled

                    // Tint the button lighter to indicate disabled status
                    val tintColor =
                        if (enabled) R.color.icon_button_tint_enable else R.color.icon_button_tint_disable
                    binding?.buttonCalendarBackward?.let {
                        ImageViewCompat.setImageTintList(
                            it,
                            ColorStateList.valueOf(ContextCompat.getColor(context, tintColor))
                        )
                        ImageViewCompat.setImageTintMode(it, PorterDuff.Mode.SRC_ATOP)
                    }
                }
            }
        }
    }

    private fun detachBinding() {
        Timber.d("detachBinding")
        binding = null
        coroutineScope?.cancel()
        coroutineScope = null
    }
}