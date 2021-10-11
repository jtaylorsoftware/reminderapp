package app.timetoremind.mobile.screens.reminder.alarm

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.timetoremind.mobile.R
import app.timetoremind.mobile.databinding.ItemAlarmBinding
import app.timetoremind.mobile.data.domain.ReminderAndAlarm
import app.timetoremind.mobile.util.getShortLocalDateDisplay
import app.timetoremind.mobile.util.getShortLocalTimeDisplay
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Separator between the next & upcoming alarms
 */
data class AlarmListSeparator(val titleText: String)

typealias OnAlarmClickListener = (ReminderAndAlarm) -> Unit

class AlarmListAdapter(val spanCount: Int, private val onAlarmClickListener: OnAlarmClickListener) :
    ListAdapter<Any, RecyclerView.ViewHolder>(
        AlarmDiffCallback
    ) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (ViewType.fromValue(viewType)) {
            ViewType.SEPARATOR -> SeparatorViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_alarm_separator, parent, false)
            )
            ViewType.ALARM -> ReminderAndAlarmViewHolder(
                ItemAlarmBinding.inflate(
                    LayoutInflater.from(
                        parent.context
                    ), parent, false
                )
            )
        }

    @SuppressLint("WrongConstant")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (position.asViewType) {
            ViewType.SEPARATOR -> {
                (holder as SeparatorViewHolder).bind(getItem(position) as AlarmListSeparator)
                configureSeparatorSize(holder.itemView)
            }
            ViewType.ALARM -> {
                holder as ReminderAndAlarmViewHolder
                val reminderAndAlarm = getItem(position) as ReminderAndAlarm
                val isNext = if (currentList.isEmpty()) false else {
                    val firstAlarmIndex = if (currentList[0] is ReminderAndAlarm) 0 else 1
                    currentList.getOrNull(firstAlarmIndex) == reminderAndAlarm
                }
                holder.bind(reminderAndAlarm, isNext, spanCount > 1)

                holder.itemView.let {
                    it.setOnClickListener {
                        onAlarmClickListener(reminderAndAlarm)
                    }
                }
                configureSize(holder.binding)
            }
        }
    }

    private fun configureSeparatorSize(view: View) {
        val density = view.resources.displayMetrics.density
        val screenWidth = view.resources.configuration.screenWidthDp
        val widthRatioInColumn = 0.80f
        val width = (widthRatioInColumn * screenWidth * density + 0.5f).toInt()
        view.layoutParams = view.layoutParams.apply {
            this.width = width
        }
    }

    private fun configureSize(binding: ItemAlarmBinding) {
        val view = binding.root
        val density = view.resources.displayMetrics.density
        val screenWidth = view.resources.configuration.screenWidthDp
        val screenHeight = view.resources.configuration.screenHeightDp
        if (spanCount == 1) {
            val widthRatioInColumn = 0.80f
            val width = (widthRatioInColumn * screenWidth * density + 0.5f).toInt()

            view.layoutParams = view.layoutParams.apply {
                this.width = width
                this.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.layoutCardItemAlarm.let {
                it.layoutParams = it.layoutParams.apply {
                    this.width = ViewGroup.LayoutParams.MATCH_PARENT
                    this.height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
            binding.textItemAlarmDaysUntil.visibility = View.GONE
            binding.textItemAlarmDescription.minLines = 1
            binding.textItemAlarmDescription.maxLines = 5
        } else {
            val widthRatioInColumn = 0.90f
            val width = (widthRatioInColumn / spanCount * screenWidth * density + 0.5f).toInt()
            view.layoutParams = view.layoutParams.apply {
                this.width = width
                this.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.layoutCardItemAlarm.let {
                it.layoutParams = it.layoutParams.apply {
                    this.width = ViewGroup.LayoutParams.MATCH_PARENT
                    this.height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
            binding.textItemAlarmDaysUntil.visibility = View.VISIBLE
            val maxLines = if (screenHeight >= 600) 5 else 4
            binding.textItemAlarmDescription.minLines = maxLines
            binding.textItemAlarmDescription.maxLines = maxLines
        }
    }

    override fun getItemViewType(position: Int): Int = position.asViewType.value

    class SeparatorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<TextView>(R.id.text_item_alarmSeparator_title)

        fun bind(alarmListSeparator: AlarmListSeparator) {
            textView.text = alarmListSeparator.titleText
        }
    }

    class ReminderAndAlarmViewHolder(val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val textSecondary = binding.textItemAlarmDaysUntil.currentTextColor

        private val colorPrimary =
            ContextCompat.getColor(binding.root.context, R.color.reminder_day_of_week_tint)

        fun bind(reminderAndAlarm: ReminderAndAlarm, isNext: Boolean, displayDaysUntil: Boolean) {
            binding.apply {
                description = reminderAndAlarm.reminder.description
                val alarmDateTime = requireNotNull(reminderAndAlarm.alarm).triggerAt

                date = alarmDateTime.getShortLocalDateDisplay()
                time = alarmDateTime.getShortLocalTimeDisplay()

                val daysUntil = LocalDate.now().until(alarmDateTime.toLocalDate(), ChronoUnit.DAYS)
                setTextDaysUntil(daysUntil, isNext, displayDaysUntil)

                executePendingBindings()
            }
        }

        private fun setTextDaysUntil(daysUntil: Long, isNext: Boolean, daysUntilVisible: Boolean) {
            if (daysUntilVisible) {
                val daysUntilText = if (isNext) "Next" else
                    when (daysUntil) {
                        -1L -> ""
                        0L -> "Today"
                        in (1L..7L) -> "< 1 week"
                        else -> "Future"
                    }

                binding.textItemAlarmDaysUntil.apply {
                    if (isNext) {
                        setTextColor(colorPrimary)
                    } else {
                        setTextColor(textSecondary)
                    }
                    text = daysUntilText
                    visibility = View.VISIBLE
                }
            } else {
                binding.textItemAlarmDaysUntil.visibility = View.GONE
            }
        }
    }

    object AlarmDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem.javaClass != newItem.javaClass) return false

            return when (oldItem) {
                is AlarmListSeparator -> {
                    newItem as AlarmListSeparator
                    oldItem.titleText == newItem.titleText
                }
                is ReminderAndAlarm -> {
                    newItem as ReminderAndAlarm
                    oldItem.reminder.id == newItem.reminder.id
                }
                else -> throw IllegalStateException("Unknown item type")
            }
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem.javaClass != newItem.javaClass) return false

            return when (oldItem) {
                is AlarmListSeparator -> {
                    oldItem == (newItem as AlarmListSeparator)
                }
                is ReminderAndAlarm -> {
                    oldItem == (newItem as ReminderAndAlarm)
                }
                else -> throw IllegalStateException("Unknown item type")
            }
        }
    }

    enum class ViewType(val value: Int) {
        SEPARATOR(0), ALARM(1);

        companion object {
            fun fromValue(value: Int) = when (value) {
                0 -> SEPARATOR
                1 -> ALARM
                else -> throw IllegalArgumentException("Invalid value for ViewType enum.")
            }
        }
    }

    private val Int.asViewType: ViewType
        get() = when (getItem(this)) {
            is AlarmListSeparator -> ViewType.SEPARATOR
            is ReminderAndAlarm -> ViewType.ALARM
            else -> throw IllegalStateException("Unknown ViewType")
        }
}