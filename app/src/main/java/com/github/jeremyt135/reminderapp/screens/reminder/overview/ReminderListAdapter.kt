package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.databinding.ItemReminderBinding
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import timber.log.Timber

class ReminderListAdapter(val spanCount: Int, private val clickListener: OnClickListener) :
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

    @SuppressLint("WrongConstant")
    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminderAndHistory = getItem(position)

        holder.itemView.let {
            it.setOnClickListener {
                clickListener.onClick(reminderAndHistory)
            }

            it.setOnCreateContextMenuListener { _, _, _ ->
                Timber.d("Selected $position with reminder $reminderAndHistory")
                selectedContextReminder = reminderAndHistory.reminder
            }
        }

        holder.bind(reminderAndHistory)
        configureSize(holder.binding)
    }

    private fun configureSize(binding: ItemReminderBinding) {
        val view = binding.root
        val density = view.resources.displayMetrics.density
        val screenWidth = view.resources.configuration.screenWidthDp
        if (spanCount == 1) {
            val widthRatioInColumn = 0.80f
            val width = (widthRatioInColumn * screenWidth * density + 0.5f).toInt()

            view.layoutParams = view.layoutParams.apply {
                this.width = width
                this.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.layoutCardItemReminder.let {
                it.layoutParams = it.layoutParams.apply {
                    this.width = ViewGroup.LayoutParams.MATCH_PARENT
                    this.height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
        } else {
            val widthRatioInColumn = 0.90f
            val width = (widthRatioInColumn / spanCount * screenWidth * density + 0.5f).toInt()
            view.layoutParams = view.layoutParams.apply {
                this.width = width
                this.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.layoutCardItemReminder.let {
                it.layoutParams = it.layoutParams.apply {
                    this.width = ViewGroup.LayoutParams.MATCH_PARENT
                    this.height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
        }
    }

    class ReminderViewHolder(val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var reminder: Reminder? = null
            private set

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