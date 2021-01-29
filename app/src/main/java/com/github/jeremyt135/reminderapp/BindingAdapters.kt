package com.github.jeremyt135.reminderapp

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderListAdapter

@BindingAdapter("reminderListData")
fun bindReminderRecyclerView(recyclerView: RecyclerView, data: List<Reminder>?){
    (recyclerView.adapter as ReminderListAdapter).submitList(data)
}
