package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.databinding.ItemReminderBinding
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory

class ReminderListAdapter(private val clickListener: OnClickListener) :
    ListAdapter<ReminderAndHistory, ReminderListAdapter.ReminderViewHolder>(
        ReminderDiffCallback
    ) {

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
        val reminder = getItem(position)
        holder.itemView.setOnClickListener {
            clickListener.onClick(reminder)
        }
        holder.bind(reminder)
    }

    class ReminderViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {
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