package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.util.getShortLocaleDisplay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarAdapter(
    yearMonth: YearMonth,
    private val daysWithReminders: List<Boolean> = emptyList(),
    private val daySelectedListener: OnDaySelectedListener
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Date adjusted for yearMonth
    private val yearMonthDate = LocalDate.of(yearMonth.year, yearMonth.month, 1)

    // Days in selected month
    private val daysInMonth: Int = yearMonth.lengthOfMonth()

    // First day of week of selected month
    private val firstDayOfWeek: DayOfWeek = yearMonthDate.dayOfWeek

    // First position in the adapter that contains a day of the selected month
    private val firstPositionOfMonth: Int = HEADING_SLOTS - 1 + firstDayOfWeek.value % 7

    // Last position of a day of the current month
    private val lastPositionOfMonth: Int = firstPositionOfMonth + daysInMonth - 1

    // Last calendar day (not position) of previous month
    private val lastDayOfPreviousMonth: Int = yearMonth.minusMonths(1).lengthOfMonth()

    init {
        if (daysWithReminders.isNotEmpty() && daysWithReminders.size != daysInMonth) {
            throw IllegalArgumentException("DaysWithReminders must be same size as number of days (${daysWithReminders.size} vs $daysInMonth)")
        }
    }

    var selectedDay: DayViewHolder? = null
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (ViewType.fromValue(viewType)) {
            ViewType.HEADING -> HeadingViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.item_calendar_heading, parent, false)
            )
            ViewType.DAY -> DayViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.item_calendar_day, parent, false)
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (ViewType.fromPosition(position)) {
            ViewType.HEADING -> {
                val heading = holder as HeadingViewHolder
                heading.setDayOfWeek(DayOfWeek.of(if (position == 0) 7 else position))
            }
            ViewType.DAY -> {
                val dayViewHolder = holder as DayViewHolder

                var isCurrentMonth = false
                var hasReminder = false

                // Get a month and dayOfMonth from position, plugging into LocalDate of current year
                val day = when {
                    position in firstPositionOfMonth..lastPositionOfMonth -> {
                        isCurrentMonth = true
                        yearMonthDate.withDayOfMonth(position - firstPositionOfMonth + 1)
                    }
                    position < firstPositionOfMonth -> yearMonthDate.minusMonths(1)
                        .withDayOfMonth(lastDayOfPreviousMonth - firstPositionOfMonth + position + 1)
                    position > lastPositionOfMonth -> yearMonthDate.plusMonths(1)
                        .withDayOfMonth(position - lastPositionOfMonth)
                    else -> throw IllegalStateException("Invalid position $position")
                }

                if (isCurrentMonth) {
                    hasReminder =
                        daysWithReminders.elementAtOrNull(position - firstPositionOfMonth) ?: false
                    // Only allow selection if part of current month
                    dayViewHolder.itemView.setOnClickListener {
                        onDaySelected(dayViewHolder)
                    }
                }

                dayViewHolder.bind(day, isCurrentMonth, hasReminder)

                // Select the viewHolder if its day is today
                if (day == LocalDate.now()) {
                    setSelectedDay(dayViewHolder)
                }
            }
        }
    }

    override fun getItemCount(): Int = ITEM_COUNT

    override fun getItemViewType(position: Int): Int {
        return ViewType.fromPosition(position).value
    }

    private fun onDaySelected(dayViewHolder: DayViewHolder) {
        daySelectedListener(dayViewHolder.day)
        setSelectedDay(dayViewHolder)
    }

    private fun setSelectedDay(dayViewHolder: DayViewHolder) {
        // Unselect previous
        selectedDay?.toggleSelected()

        // Select new
        dayViewHolder.toggleSelected()
        selectedDay = dayViewHolder
    }

    /**
     * ViewHolder for a date on the calendar. Displays numeric dayOfMonth and
     * a background image that changes if it is today's date or the selected date.
     */
    class DayViewHolder(private val view: View) :
        RecyclerView.ViewHolder(view) {

        lateinit var day: LocalDate

        private val textView: TextView = view.findViewById(R.id.text_calendar_dayOfMonth)
        private val defaultTextColor: Int = textView.currentTextColor
        private val selectedImage: ImageView = view.findViewById(R.id.image_calendar_dayOfMonth)
        private val reminderIndicator: ImageView =
            view.findViewById(R.id.image_calendar_reminderIndicator)
        private var selected = false

        private val isToday: Boolean by lazy {
            day == LocalDate.now()
        }

        /**
         * Binds this ViewHolder to a day of the month.
         * @param day the day of the month as a LocalDate
         * @param isCurrentMonth flag to indicate if this date is part of the current month. If
         * true, the view's day of month text will use a secondary, muted color.
         * @param hasReminder flag to indicate if this date has at least one reminder. If true,
         * the view will display an indicator.
         */
        fun bind(day: LocalDate, isCurrentMonth: Boolean, hasReminder: Boolean) {
            this.day = day
            textView.text = day.dayOfMonth.toString()

            if (isToday) {
                useOutlineImage()
            }

            if (!isCurrentMonth) {
                // Use muted text color for other month days
                textView.setTextColor(ContextCompat.getColor(view.context, R.color.text_secondary))
            }

            if (hasReminder) {
                showIndicator()
            }
        }

        fun toggleSelected() {
            selected = !selected
            if (selected) {
                onSelect()
            } else {
                onUnselect()
            }
        }

        private fun onSelect() {
            useFilledImage()
//            val colorId = MaterialColors.getColor(view, R.attr.colorOnSecondary)
            val colorId = ContextCompat.getColor(view.context, R.color.white)
            textView.setTextColor(colorId)
        }

        private fun onUnselect() {
            if (isToday) {
                useOutlineImage()
            } else {
                hideImage()
            }
            textView.setTextColor(defaultTextColor)
        }

        private fun useOutlineImage() {
            selectedImage.setImageResource(R.drawable.bg_calendar_day_outline)
            selectedImage.visibility = View.VISIBLE
        }

        private fun useFilledImage() {
            selectedImage.setImageResource(R.drawable.bg_calendar_day_filled)
            selectedImage.visibility = View.VISIBLE
        }

        private fun hideImage() {
            selectedImage.setImageResource(0)
            selectedImage.visibility = View.INVISIBLE
        }

        private fun showIndicator() {
            reminderIndicator.setImageResource(R.drawable.ic_calendar_reminder_indicator)
            reminderIndicator.visibility = View.VISIBLE
        }
    }

    class HeadingViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun setDayOfWeek(dayOfWeek: DayOfWeek) {
            view.findViewById<TextView>(R.id.text_calendar_dayOfWeek)!!.text =
                dayOfWeek.getShortLocaleDisplay()
        }
    }

    enum class ViewType(val value: Int) {
        HEADING(0), DAY(1);

        companion object {
            fun fromValue(value: Int) = when (value) {
                0 -> HEADING
                1 -> DAY
                else -> throw IllegalArgumentException("Invalid value for ViewType enum.")
            }

            fun fromPosition(position: Int) = when {
                position < 7 -> HEADING
                else -> DAY
            }
        }
    }

    companion object {
        private const val HEADING_SLOTS = 7
        private const val DAY_SLOTS = 35

        /**
         * The fixed number of items in the adapter - 7 headings and enough items for 35 days
         * (5 weeks displayed at once).
         */
        const val ITEM_COUNT = HEADING_SLOTS + DAY_SLOTS
    }
}

typealias OnDaySelectedListener = (day: LocalDate) -> Unit