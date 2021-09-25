package com.github.jeremyt135.reminderapp.screens.reminder.alarm

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.ItemAlarmBinding
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.util.getShortLocalDateDisplay
import com.github.jeremyt135.reminderapp.util.getShortLocalTimeDisplay

/**
 * Separator between the next & upcoming alarms
 */
data class AlarmListSeparator(val titleText: String)

typealias OnAlarmClickListener = (ReminderAndAlarm) -> Unit

class AlarmListAdapter(private val onAlarmClickListener: OnAlarmClickListener) :
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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) =
        when (ViewType.fromPosition(position)) {
            ViewType.SEPARATOR -> {
                (holder as SeparatorViewHolder).bind(getItem(position) as AlarmListSeparator)
            }
            ViewType.ALARM -> {
                holder as ReminderAndAlarmViewHolder
                val reminderAndAlarm = getItem(position) as ReminderAndAlarm
                holder.bind(reminderAndAlarm)
                holder.itemView.setOnClickListener {
                    onAlarmClickListener(reminderAndAlarm)
                }
            }
        }

    override fun getItemViewType(position: Int): Int = ViewType.fromPosition(position).value

    class SeparatorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<TextView>(R.id.text_item_alarmSeparator_title)

        fun bind(alarmListSeparator: AlarmListSeparator) {
            textView.text = alarmListSeparator.titleText
        }
    }

    class ReminderAndAlarmViewHolder(private val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reminderAndAlarm: ReminderAndAlarm) {
            binding.apply {
                description = reminderAndAlarm.reminder.description
                val alarmDateTime = requireNotNull(reminderAndAlarm.alarm).triggerAt
                date = alarmDateTime.getShortLocalDateDisplay()
                time = alarmDateTime.getShortLocalTimeDisplay()
                executePendingBindings()
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

            fun fromPosition(position: Int) = when (position) {
                0, 2 -> SEPARATOR
                else -> ALARM
            }
        }
    }
}