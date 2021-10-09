package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.LayoutInflater
import androidx.annotation.AttrRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.findFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.transition.TransitionManager
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.ViewCalendarBinding
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth

typealias OnReminderSelectedListener = (Reminder) -> Unit
typealias OnAddReminderListener = (LocalDate?) -> Unit

@ExperimentalCoroutinesApi
@AndroidEntryPoint
class CalendarView : ConstraintLayout {
    private val viewModel: CalendarViewModel by lazy {
        ViewModelProvider(requireNotNull(findViewTreeViewModelStoreOwner())).get(CalendarViewModel::class.java)
    }
    private var binding: ViewCalendarBinding? =
        ViewCalendarBinding.inflate(LayoutInflater.from(context), this, true)
    private var calendarAdapter: CalendarAdapter? = null
    private var coroutineScope: CoroutineScope? = MainScope() + CoroutineName("CalendarView")

    var onReminderSelectedListener: OnReminderSelectedListener? = null
    var onAddReminderListener: OnAddReminderListener? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int)
            : super(context, attrs, defStyleAttr)


    init {
        Timber.d("Init")
    }

    override fun onAttachedToWindow() {
        Timber.d("onAttached")
        super.onAttachedToWindow()
        attachBinding()
    }

    override fun onDetachedFromWindow() {
        Timber.d("onDetached")
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
     * Sets the selected year & month
     */
    fun setYearMonth(yearMonth: YearMonth){
        viewModel.setSelectedMonth(yearMonth)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        Timber.d("onConfigurationChanged")
        super.onConfigurationChanged(newConfig)
        removeAllViews()
        binding = ViewCalendarBinding.inflate(LayoutInflater.from(context), this, true)
        attachBinding()
    }

    private fun attachBinding() {
        Timber.d("attachBinding")
        val lifecycleOwner = requireNotNull(findViewTreeLifecycleOwner())

        binding?.model = viewModel
        binding?.lifecycleOwner = lifecycleOwner

        setupBindingViews()

        startCoroutines()
    }

    private fun setupBindingViews() {
        Timber.d("setupBindingViews")
        // Set calendar adapter and immediately begin observing calendarData for layout updates
        calendarAdapter =
            CalendarAdapter(viewModel.selectedDate.value, viewModel.indicatorColors) {
                viewModel.setSelectedDate(it)
            }
        // Use a GLM with Calendar's RecyclerView that has disabled scrolling in both directions
        binding?.recyclerCalendar?.layoutManager =
            object : GridLayoutManager(context, CalendarAdapter.COLUMNS) {
                override fun canScrollVertically() = false
                override fun canScrollHorizontally() = false
            }

        binding?.recyclerCalendar?.adapter = calendarAdapter

        observeCalendarData()
        observeSelectedDate()

        binding?.textCalendarMonthYear?.let {
            findFragment<CalendarFragment>().registerForContextMenu(it)
        }

        binding?.recyclerCalendarReminders?.adapter =
            CalendarReminderListAdapter(
                CalendarReminderListAdapter.OnClickListener {
                    onReminderSelectedListener?.invoke(it.reminder)
                })

        binding?.buttonCalendarBackward?.setOnClickListener {
            viewModel.goToPreviousMonth()
            calendarAdapter?.clearSelectedDate()
        }
        binding?.buttonCalendarForward?.setOnClickListener {
            viewModel.goToNextMonth()
            calendarAdapter?.clearSelectedDate()
        }

        binding?.buttonCalendarAdd?.setOnClickListener {
            onAddReminderListener?.invoke(calendarAdapter?.selectedDate)
        }
    }

    private fun startCoroutines() {
        coroutineScope = (CoroutineScope(Dispatchers.Main) + CoroutineName("CalendarView"))
        collectLatestMonth()
        setForwardButtonTint()
    }

    private fun collectLatestMonth() {
        coroutineScope?.launch {
            viewModel.selectedYearMonth.collectLatest { yearMonth ->
                // Disable button if current month is selected, so user cannot go into past
                val enabled = yearMonth != null && yearMonth > YearMonth.now()
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

    private fun setForwardButtonTint() {
        coroutineScope?.launch {
            binding?.buttonCalendarForward?.let {
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
        }
    }

    private fun observeCalendarData() {
        viewModel.calendarData.observe(
            requireNotNull(findViewTreeLifecycleOwner()),
            object : Observer<List<Any>?> {
                override fun onChanged(data: List<Any>?) {
                    if (data != null) {
                        Timber.d("Applying layout transition (${data.size} elements")
                        val constraintSet = ConstraintSet()
                        constraintSet.load(context, R.layout.view_calendar_loaded)
                        TransitionManager.beginDelayedTransition(this@CalendarView)
                        constraintSet.applyTo(this@CalendarView)
                        viewModel.calendarData.removeObserver(this)
                    }
                }

            })
    }

    private fun observeSelectedDate() {
        viewModel.selectedDate.observe(
            requireNotNull(findViewTreeLifecycleOwner())
        ) { selectedDate ->
            val hint = if (selectedDate == null) {
                context.getString(R.string.calendar_reminder_select_hint)
            } else {
                context.getString(R.string.calendar_reminder_empty_hint)
            }
            binding?.textCalendarReminderHint?.text = hint
        }
    }

    private fun detachBinding() {
        Timber.d("detachBinding")
        coroutineScope?.cancel()
        coroutineScope = null
        binding = null
    }
}