package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import com.github.jeremyt135.databinding.ItemReminderCalendarBinding
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderListAdapter
import com.google.android.flexbox.AlignSelf
import com.google.android.flexbox.FlexboxLayoutManager
import timber.log.Timber

class CalendarReminderListAdapter(clickListener: OnClickListener) :
    ReminderListAdapter(clickListener) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        return ReminderViewHolderCalendar(
            ItemReminderCalendarBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    @SuppressLint("WrongConstant")
    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminderAndHistory = getItem(position)

        holder.itemView.let {
            (it.layoutParams as? FlexboxLayoutManager.LayoutParams)?.apply {
                flexShrink = 0f
                alignSelf = AlignSelf.CENTER
            }

            it.setOnClickListener {
                clickListener.onClick(reminderAndHistory)
            }

            it.setOnCreateContextMenuListener { _, _, _ ->
                Timber.d("Selected $position with reminder $reminderAndHistory")
                selectedContextReminder = reminderAndHistory.reminder
            }
        }

        (holder as ReminderViewHolderCalendar).bind(reminderAndHistory)
    }

    private class ReminderViewHolderCalendar(private val binding: ItemReminderCalendarBinding) :
        ReminderViewHolder(binding.root) {
        override var reminder: Reminder? = null

        fun bind(reminderAndHistory: ReminderAndHistory) {
            this.reminder = reminderAndHistory.reminder
            binding.apply {
                this.data = reminderAndHistory
                executePendingBindings()
            }
        }
    }
}