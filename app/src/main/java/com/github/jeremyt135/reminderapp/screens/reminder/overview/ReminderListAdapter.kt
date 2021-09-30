package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.databinding.ItemReminderBinding
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import com.google.android.flexbox.AlignSelf
import com.google.android.flexbox.FlexboxLayoutManager
import timber.log.Timber

open class ReminderListAdapter(protected val clickListener: OnClickListener) :
    ListAdapter<ReminderAndHistory, ReminderListAdapter.ReminderViewHolder>(
        ReminderDiffCallback
    ) {

    var selectedContextReminder: Reminder? = null
        protected set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        return ReminderViewHolderImpl(
            ItemReminderBinding.inflate(
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

        (holder as ReminderViewHolderImpl).bind(reminderAndHistory)
    }

    abstract class ReminderViewHolder(root: View) :
        RecyclerView.ViewHolder(root) {
        abstract var reminder: Reminder?
            protected set
    }

    private class ReminderViewHolderImpl(private val binding: ItemReminderBinding) :
        ReminderListAdapter.ReminderViewHolder(binding.root) {
        override var reminder: Reminder? = null

        fun bind(reminderAndHistory: ReminderAndHistory) {
            this.reminder = reminderAndHistory.reminder
            binding.apply {
                this.data = reminderAndHistory
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