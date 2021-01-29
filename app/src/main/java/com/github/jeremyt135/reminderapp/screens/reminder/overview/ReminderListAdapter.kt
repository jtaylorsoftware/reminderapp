package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.databinding.ItemReminderBinding

class ReminderListAdapter(private val clickListener: OnClickListener) : ListAdapter<Reminder, ReminderListAdapter.ReminderViewHolder>(
    ReminderDiffCallback
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        return ReminderViewHolder(ItemReminderBinding.inflate(LayoutInflater.from(parent.context)))
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
        fun bind(reminder: Reminder) {
            binding.apply {
                this.reminder = reminder
                executePendingBindings()
            }
        }
    }

    companion object ReminderDiffCallback : DiffUtil.ItemCallback<Reminder>() {
        override fun areItemsTheSame(oldItem: Reminder, newItem: Reminder) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Reminder, newItem: Reminder) = oldItem == newItem
    }

    class OnClickListener(val clickListener: (reminder: Reminder) -> Unit){
        fun onClick(reminder: Reminder) = clickListener(reminder)
    }
}