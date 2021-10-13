package app.timetoremind.mobile.screens.reminder.overview

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.ReminderAndHistory
import app.timetoremind.mobile.databinding.ItemReminderBinding
import app.timetoremind.mobile.util.dateNoun
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
        val screenHeight = view.resources.configuration.screenHeightDp
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
            binding.textItemReminderDescription.minLines = 1
            binding.textItemReminderDescription.maxLines = 5
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
            val maxLines = if (screenHeight >= 600) 5 else 4
            binding.textItemReminderDescription.minLines = maxLines
            binding.textItemReminderDescription.maxLines = maxLines
        }
    }

    class ReminderViewHolder(val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var reminder: Reminder? = null
            private set

        fun bind(reminderAndHistory: ReminderAndHistory) {
            this.reminder = reminderAndHistory.reminder
            binding.data = reminderAndHistory

            val startDateRes = reminder!!.schedulingRules.startDate.dateNoun()
            if (startDateRes != null) {
                binding.textItemReminderStartDate.setText(startDateRes)
            } else {
                binding.textItemReminderStartDate.text =
                    reminder!!.schedulingRules.startDateDisplayString()
            }
            val endDateRes = reminder!!.schedulingRules.endDate.dateNoun()
            if (endDateRes != null) {
                binding.textItemReminderEndDate.setText(endDateRes)
            } else {
                binding.textItemReminderEndDate.text =
                    reminder!!.schedulingRules.endDateDisplayString()
            }
            binding.executePendingBindings()
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