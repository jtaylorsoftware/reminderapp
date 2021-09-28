package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.util.getShortLocaleDisplay
import com.github.jeremyt135.reminderapp.util.uuid
import com.google.android.material.color.MaterialColors
import java.time.DayOfWeek
import java.time.LocalDate

typealias OnDaySelectedListener = (day: LocalDate?) -> Unit

class CalendarAdapter(private val daySelectedListener: OnDaySelectedListener) :
    ListAdapter<Any, RecyclerView.ViewHolder>(CalendarDiffCallback) {

    val selectedDate: LocalDate?
        get() = selectedDateViewHolder?.date

    private var selectedDateViewHolder: DateViewHolder? = null

    fun clearSelectedDay() {
        selectedDateViewHolder?.toggleSelected()
        selectedDateViewHolder = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (ViewType.fromValue(viewType)) {
            ViewType.HEADING -> HeadingViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.item_calendar_heading, parent, false)
            )
            ViewType.DAY -> DateViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.item_calendar_day, parent, false)
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (ViewType.fromPosition(position)) {
            ViewType.HEADING -> {
                item as DayOfWeek
                (holder as HeadingViewHolder).setDayOfWeek(item)
            }
            ViewType.DAY -> {
                item as CalendarDateItem
                (holder as DateViewHolder).bind(position, item)
                if (item.isNotInPast && item.isCurrentMonth) {
                    holder.itemView.setOnClickListener {
                        onDaySelected(holder)
                    }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return ViewType.fromPosition(position).value
    }

    private fun onDaySelected(dateViewHolder: DateViewHolder) {
        daySelectedListener(dateViewHolder.date)
        setSelectedDay(dateViewHolder)
    }

    private fun setSelectedDay(dateViewHolder: DateViewHolder) {
        // Unselect previous
        selectedDateViewHolder?.toggleSelected()

        // Select new
        dateViewHolder.toggleSelected()
        selectedDateViewHolder = dateViewHolder
    }

    /**
     * ViewHolder for a date on the calendar. Displays numeric dayOfMonth and
     * a background image that changes if it is today's date or the selected date.
     */
    class DateViewHolder(private val view: View) :
        RecyclerView.ViewHolder(view) {
        val id = uuid()

        var date: LocalDate? = null
            private set

        private val textView: TextView = view.findViewById(R.id.text_calendar_dayOfMonth)
        private val defaultTextColor: Int = MaterialColors.getColor(view, R.attr.colorOnSurface)
        private val selectedImage: ImageView = view.findViewById(R.id.image_calendar_dayOfMonth)
        private val reminderIndicator: ImageView =
            view.findViewById(R.id.image_calendar_reminderIndicator)
        private var selected = false

        private val isToday: Boolean by lazy {
            date == LocalDate.now()
        }

        /**
         * Binds this ViewHolder to a day of the month.
         * @param calendarDateItem Day of month data to bind
         */
        fun bind(position: Int, calendarDateItem: CalendarDateItem) {
            reset()

            this.date = calendarDateItem.date
            if (calendarDateItem.date != null) {
                textView.text = date?.dayOfMonth.toString()
            } else {
                textView.text = (position - HEADING_SLOTS + 1).toString()
            }

            if (isToday) {
                useOutlineImage()
            }

            if (!calendarDateItem.isCurrentMonth || !calendarDateItem.isNotInPast) {
                // Use muted text color for other month days
                textView.setTextColor(ContextCompat.getColor(view.context, R.color.text_secondary))
            }

            if (calendarDateItem.hasReminder && calendarDateItem.isNotInPast) {
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
            val colorId = MaterialColors.getColor(view, R.attr.colorOnPrimary)
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

        private fun hideIndicator() {
            reminderIndicator.setImageResource(0)
            reminderIndicator.visibility = View.INVISIBLE
        }

        private fun reset() {
            hideIndicator()
            hideImage()
            textView.setTextColor(defaultTextColor)
        }
    }

    class HeadingViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        val id = uuid()

        lateinit var dayOfWeek: DayOfWeek
            private set

        fun setDayOfWeek(dayOfWeek: DayOfWeek) {
            this.dayOfWeek = dayOfWeek
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
                position < HEADING_SLOTS -> HEADING
                position in HEADING_SLOTS until ITEM_COUNT -> DAY
                else -> throw IllegalArgumentException("Invalid position for ViewType enum.")
            }
        }
    }

    companion object {
        const val COLUMNS = 7 // Same as no. days in week

        const val HEADING_SLOTS = 7 // Same as no. days in week
        private const val DAY_SLOTS =
            42 // 6 weeks of values to account for any start & end dates in a month

        /**
         * The fixed number of items in the adapter
         */
        const val ITEM_COUNT = HEADING_SLOTS + DAY_SLOTS
    }

    object CalendarDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem.javaClass != newItem.javaClass) return false

            return when (oldItem) {
                is CalendarDateItem -> {
                    false
                }
                is DayOfWeek -> {
                    newItem as DayOfWeek
                    oldItem == newItem
                }
                else -> throw IllegalStateException("Unknown item type")
            }
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem.javaClass != newItem.javaClass) return false

            return when (oldItem) {
                is CalendarDateItem -> {
                    // return valid comparison even though areItemsTheSame currently always returns false
                    newItem as CalendarDateItem
                    oldItem == newItem
                }
                is DayOfWeek -> {
                    newItem as DayOfWeek
                    oldItem == newItem
                }
                else -> throw IllegalStateException("Unknown item type")
            }
        }
    }
}

/**
 * Backing data for calendar
 */
open class CalendarDateItem(
    /**
     * The date for the item, can be null to indicate a loading or placeholder status
     */
    val date: LocalDate?,

    /**
     * Flag indicating whether this item is in the current month
     */
    val isCurrentMonth: Boolean,

    /**
     * Flag indicating whether this item has a reminder on its date
     */
    val hasReminder: Boolean
) {
    val isNotInPast = date?.let { date >= LocalDate.now() } ?: false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CalendarDateItem

        if (date != other.date) return false
        if (isCurrentMonth != other.isCurrentMonth) return false
        if (hasReminder != other.hasReminder) return false

        return true
    }

    override fun hashCode(): Int {
        var result = date?.hashCode() ?: 0
        result = 31 * result + isCurrentMonth.hashCode()
        result = 31 * result + hasReminder.hashCode()
        return result
    }
}

object PlaceholderDateItem : CalendarDateItem(null, false, false)