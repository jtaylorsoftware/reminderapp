package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.databinding.ItemReminderBinding
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import timber.log.Timber

class ReminderListAdapter(private val clickListener: OnClickListener) :
    ListAdapter<ReminderAndHistory, ReminderListAdapter.ReminderViewHolder>(
        ReminderDiffCallback
    ) {

    var selectedContextReminder: Reminder? = null
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        return ReminderViewHolder(
            ItemReminderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminderAndHistory = getItem(position)
        holder.itemView.setOnClickListener {
            clickListener.onClick(reminderAndHistory)
        }

        holder.itemView.setOnCreateContextMenuListener { _, _, _ ->
            Timber.d("Selected $position with reminder $reminderAndHistory")
            selectedContextReminder = reminderAndHistory.reminder
        }
        holder.bind(reminderAndHistory)
    }

    class ReminderViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val reminder: Reminder?
            get() = binding.data?.reminder

        fun bind(reminder: ReminderAndHistory) {
            binding.apply {
                this.data = reminder
                executePendingBindings()
            }
        }
    }

    companion object ReminderDiffCallback : DiffUtil.ItemCallback<ReminderAndHistory>() {
        override fun areItemsTheSame(oldItem: ReminderAndHistory, newItem: ReminderAndHistory) =
            oldItem.reminder.id == newItem.reminder.id

        override fun areContentsTheSame(oldItem: ReminderAndHistory, newItem: ReminderAndHistory) =
            oldItem == newItem
    }

    class OnClickListener(val clickListener: (reminder: ReminderAndHistory) -> Unit) {
        fun onClick(reminder: ReminderAndHistory) = clickListener(reminder)
    }
}