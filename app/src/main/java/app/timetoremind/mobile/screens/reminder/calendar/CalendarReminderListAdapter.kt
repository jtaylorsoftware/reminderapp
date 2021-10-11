package app.timetoremind.mobile.screens.reminder.calendar

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.timetoremind.mobile.databinding.ItemReminderCalendarBinding
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.ReminderAndHistory

class CalendarReminderListAdapter(
    private val clickListener: OnClickListener
) :
    ListAdapter<ReminderAndColor, CalendarReminderListAdapter.ReminderViewHolder>(
        ReminderDiffCallback
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        return ReminderViewHolder(
            ItemReminderCalendarBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    @SuppressLint("WrongConstant")
    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminderAndColor = getItem(position)

        holder.itemView.let {
            it.setOnClickListener {
                clickListener.onClick(reminderAndColor.data)
            }
        }

        holder.bind(reminderAndColor, ColorDrawable(reminderAndColor.color))
    }

    class ReminderViewHolder(private val binding: ItemReminderCalendarBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var reminder: Reminder? = null
            private set

        fun bind(reminder: ReminderAndColor, color: ColorDrawable) {
            this.reminder = reminder.data.reminder
            binding.apply {
                data = reminder.data
                viewItemReminderCalendarColorIndicator.background = color
                executePendingBindings()
            }
        }
    }

    companion object ReminderDiffCallback : DiffUtil.ItemCallback<ReminderAndColor>() {
        override fun areItemsTheSame(oldItem: ReminderAndColor, newItem: ReminderAndColor) =
            oldItem.data.reminder.id == newItem.data.reminder.id

        override fun areContentsTheSame(oldItem: ReminderAndColor, newItem: ReminderAndColor) =
            oldItem == newItem
    }

    class OnClickListener(val clickListener: (reminder: ReminderAndHistory) -> Unit) {
        fun onClick(reminder: ReminderAndHistory) = clickListener(reminder)
    }
}