package com.example.reminderapp

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderapp.data.domain.Reminder
import com.example.reminderapp.screens.todo.overview.ReminderListAdapter

@BindingAdapter("reminderListData")
fun bindReminderRecyclerView(recyclerView: RecyclerView, data: List<Reminder>?){
    (recyclerView.adapter as ReminderListAdapter).submitList(data)
}
