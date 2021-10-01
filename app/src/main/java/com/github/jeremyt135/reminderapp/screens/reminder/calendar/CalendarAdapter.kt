package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.util.getShortLocaleDisplay
import com.github.jeremyt135.reminderapp.util.uuid
import com.google.android.material.color.MaterialColors
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.min

typealias OnDaySelectedListener = (day: LocalDate?) -> Unit

class CalendarAdapter(
    initialSelectedDate: LocalDate?,
    private val indicatorColors: List<Int>,
    private val daySelectedListener: OnDaySelectedListener
) :
    ListAdapter<Any, RecyclerView.ViewHolder>(CalendarDiffCallback) {

    var selectedDate: LocalDate? = initialSelectedDate
        private set

    private var selectedDateViewHolder: DateViewHolder? = null

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
            ) {
                onDateViewClicked(it)
            }
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
                (holder as DateViewHolder).bind(position, item, selectedDate, indicatorColors)
                if (!item.isInPast && item.isCurrentMonth) {
                    holder.itemView.setOnClickListener {
                        daySelectedListener(holder.date)
                        holder.select()
                    }
                }
            }
        }
    }

    fun clearSelectedDate() {
        selectedDateViewHolder?.deselect()
        selectedDateViewHolder = null
        selectedDate = null
    }

    private fun onDateViewClicked(dateViewHolder: DateViewHolder) {
        selectedDateViewHolder?.deselect()
        selectedDateViewHolder = dateViewHolder
        selectedDate = dateViewHolder.date
    }

    override fun getItemViewType(position: Int): Int {
        return ViewType.fromPosition(position).value
    }

    /**
     * ViewHolder for a date on the calendar. Displays numeric dayOfMonth and
     * a background image that changes if it is today's date or the selected date.
     */
    class DateViewHolder(
        private val view: View,
        private val onSelected: (DateViewHolder) -> Unit
    ) :
        RecyclerView.ViewHolder(view) {
        val id = uuid()

        var date: LocalDate? = null
            private set

        private val textView: TextView = view.findViewById(R.id.text_calendar_dayOfMonth)
        private val defaultTextColor: Int = MaterialColors.getColor(view, R.attr.colorOnSurface)
        private val selectedImage: ImageView = view.findViewById(R.id.image_calendar_dayOfMonth)
        private var reminders: List<Reminder>? = null
        private val reminderIndicators: List<ImageView> =
            listOf(
                view.findViewById(R.id.image_calendar_reminderIndicator0),
                view.findViewById(R.id.image_calendar_reminderIndicator1),
                view.findViewById(R.id.image_calendar_reminderIndicator2)
            )
        private var selected = false

        private val isToday: Boolean
            get() = date == LocalDate.now()


        /**
         * Binds this ViewHolder to a day of the month.
         * @param calendarDateItem Day of month data to bind
         */
        fun bind(
            position: Int,
            calendarDateItem: CalendarDateItem,
            selectedDate: LocalDate?,
            indicatorColors: List<Int>
        ) {
            reset()

            this.date = calendarDateItem.date
            this.reminders = calendarDateItem.reminders
            if (calendarDateItem.date != null) {
                textView.text = date?.dayOfMonth.toString()
            } else {
                textView.text = (position - HEADING_SLOTS + 1).toString()
            }

            if (isToday) {
                useOutlineImage()
            }

            if (!calendarDateItem.isCurrentMonth || calendarDateItem.isInPast) {
                // Use muted text color for other month days
                textView.setTextColor(ContextCompat.getColor(view.context, R.color.text_secondary))
            }

            if (calendarDateItem.reminders.isNotEmpty() && !calendarDateItem.isInPast) {
                showIndicators(calendarDateItem.reminders, indicatorColors)
            }

            if (date != null && selectedDate == date && !selected) {
                select()
            }
        }

        fun select() {
            selected = true
            onSelected(this)
            onSelect()
        }

        fun deselect() {
            selected = false
            onDeselect()
        }

        private fun onSelect() {
            useFilledImage()
            val colorId = MaterialColors.getColor(view, R.attr.colorOnPrimary)
            textView.setTextColor(colorId)
        }

        private fun onDeselect() {
            if (isToday) {
                useOutlineImage()
            } else {
                hideBackgroundImage()
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

        private fun hideBackgroundImage() {
            selectedImage.setImageResource(0)
            selectedImage.visibility = View.INVISIBLE
        }

        private fun showIndicators(reminders: List<Reminder>, indicatorColors: List<Int>) {
            val numIndicators = min(reminders.size, 3)
            (0 until numIndicators).forEach { i ->
                showIndicator(i, indicatorColors[i])
            }
        }

        private fun showIndicator(index: Int, indicatorColor: Int) {
            reminderIndicators.elementAtOrNull(index)?.let { indicator ->
                ImageViewCompat.setImageTintMode(indicator, PorterDuff.Mode.SRC_ATOP)
                ImageViewCompat.setImageTintList(
                    indicator,
                    ColorStateList.valueOf(indicatorColor)
                )
                indicator.visibility = View.VISIBLE
            }
        }

        private fun hideIndicator(index: Int) {
            reminderIndicators.elementAtOrNull(index)?.let { indicator ->
                indicator.visibility = View.GONE
            }
        }

        private fun hideAllIndicators() {
            hideIndicator(0)
            hideIndicator(1)
            hideIndicator(2)
        }

        private fun reset() {
            hideAllIndicators()
            hideBackgroundImage()
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
                    newItem as CalendarDateItem
                    oldItem.date == newItem.date
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
                    oldItem.date == newItem.date && oldItem.reminders.size == newItem.reminders.size
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
     * Reminders on the selected date
     */
    val reminders: List<Reminder>
) {
    val isInPast = date?.let { date < LocalDate.now() } ?: true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CalendarDateItem

        if (date != other.date) return false
        if (isCurrentMonth != other.isCurrentMonth) return false
        if (reminders != other.reminders) return false

        return true
    }

    override fun hashCode(): Int {
        var result = date?.hashCode() ?: 0
        result = 31 * result + isCurrentMonth.hashCode()
        result = 31 * result + reminders.hashCode()
        return result
    }
}
