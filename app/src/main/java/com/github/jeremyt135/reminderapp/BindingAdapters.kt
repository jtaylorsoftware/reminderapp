package com.github.jeremyt135.reminderapp

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.screens.reminder.alarm.AlarmListAdapter
import com.github.jeremyt135.reminderapp.screens.reminder.history.HistoryListAdapter
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderListAdapter

@BindingAdapter("reminderListData")
fun bindReminderRecyclerView(recyclerView: RecyclerView, data: List<ReminderAndHistory>?) {
    (recyclerView.adapter as ReminderListAdapter).submitList(data)
}

@BindingAdapter("historyListData")
fun bindHistoryRecyclerView(recyclerView: RecyclerView, data: List<History>?) {
    (recyclerView.adapter as HistoryListAdapter).submitList(data)
}

@BindingAdapter("alarmListData")
fun bindAlarmRecyclerView(recyclerView: RecyclerView, data: List<Any>?) {
    (recyclerView.adapter as AlarmListAdapter).submitList(data)
}

@BindingAdapter("statusForIcon")
fun bindReminderStatusIcon(imageView: ImageView, history: History?) {
    val resId = history?.let {
        when (it.completionStatus) {
            ReminderStatus.PENDING -> R.drawable.ic_notification_pending
            ReminderStatus.SKIPPED -> R.drawable.ic_notification_skip
            ReminderStatus.COMPLETED -> R.drawable.ic_notification_complete
        }
    } ?: R.drawable.ic_notification_pending
    imageView.setImageResource(resId)
    imageView.tag = resId
}