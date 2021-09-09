package com.github.jeremyt135.reminderapp.screens.reminder.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.databinding.ItemHistoryBinding
import com.github.jeremyt135.reminderapp.data.domain.History
import timber.log.Timber

class HistoryListAdapter : ListAdapter<History, HistoryListAdapter.HistoryViewHolder>(HistoryDiffUtil) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder =
        HistoryViewHolder(
            ItemHistoryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        Timber.d("Binding HistoryViewHolder $position")
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: History) {
            binding.history = item
        }
    }

    object HistoryDiffUtil : DiffUtil.ItemCallback<History>() {
        override fun areItemsTheSame(oldItem: History, newItem: History): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: History, newItem: History): Boolean =
            oldItem == newItem
    }
}