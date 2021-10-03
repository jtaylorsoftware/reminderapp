package com.github.jeremyt135.reminderapp.screens.reminder.history

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentHistoryBinding
import com.google.android.flexbox.*
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class HistoryFragment : Fragment(R.layout.fragment_history) {
    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.setHasOptionsMenu(true)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        FragmentHistoryBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            model = viewModel

            recyclerHistoryList.layoutManager = FlexboxLayoutManager(requireContext()).apply {
                flexDirection = FlexDirection.COLUMN
                flexWrap = FlexWrap.NOWRAP
                alignItems = AlignItems.CENTER
                justifyContent = JustifyContent.FLEX_START
            }
            recyclerHistoryList.adapter = HistoryListAdapter()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.history_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.item_history_clear -> {
            buildClearDialog().also {
                if (it == null) {
                    Timber.e("AlertDialog was null")
                }
            }?.show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun buildClearDialog(): AlertDialog? {
        return activity?.let {
            AlertDialog.Builder(it).apply {
                setMessage(R.string.dialog_clearHistory_message)
                setTitle(R.string.dialog_clearHistory_title)
                setPositiveButton(R.string.ok) { _, _ ->
                    Timber.d("User confirmed clear history")
                    viewModel.clearHistory()
                }
                setNegativeButton(R.string.cancel) { _, _ ->
                    Timber.d("User cancelled clear history")
                }
            }.create()
        }
    }
}