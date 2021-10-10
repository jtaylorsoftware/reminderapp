package com.github.jeremyt135.reminderapp.screens.reminder.history

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jeremyt135.databinding.ItemHistoryBinding
import com.github.jeremyt135.reminderapp.data.domain.History

class HistoryListAdapter(val spanCount: Int) :
    ListAdapter<History, HistoryListAdapter.HistoryViewHolder>(HistoryDiffUtil) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder =
        HistoryViewHolder(
            ItemHistoryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    @SuppressLint("WrongConstant")
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
        configureSize(holder.binding)
    }

    private fun configureSize(binding: ItemHistoryBinding) {
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
            binding.layoutCardItemHistory.let {
                it.layoutParams = it.layoutParams.apply {
                    this.width = ViewGroup.LayoutParams.MATCH_PARENT
                    this.height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
            binding.textItemHistoryDescription.minLines = 1
            binding.textItemHistoryDescription.maxLines = 5
        } else {
            val widthRatioInColumn = 0.90f
            val width = (widthRatioInColumn / spanCount * screenWidth * density + 0.5f).toInt()
            view.layoutParams = view.layoutParams.apply {
                this.width = width
                this.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.layoutCardItemHistory.let {
                it.layoutParams = it.layoutParams.apply {
                    this.width = ViewGroup.LayoutParams.MATCH_PARENT
                    this.height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
            val maxLines = if (screenHeight >= 600) 5 else 4
            binding.textItemHistoryDescription.minLines = maxLines
            binding.textItemHistoryDescription.maxLines = maxLines
        }
    }

    class HistoryViewHolder(val binding: ItemHistoryBinding) :
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