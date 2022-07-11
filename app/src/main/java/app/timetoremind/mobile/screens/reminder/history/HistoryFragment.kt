package app.timetoremind.mobile.screens.reminder.history

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.timetoremind.mobile.R
import app.timetoremind.mobile.databinding.FragmentHistoryBinding
import app.timetoremind.mobile.screens.reminder.getLayoutSpanCount
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class HistoryFragment : Fragment(R.layout.fragment_history) {
    private val viewModel: HistoryViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBinding(view)
        setupMenu()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.history_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                R.id.item_history_clear -> {
                    buildClearDialog().also {
                        if (it == null) {
                            Timber.e("AlertDialog was null")
                        }
                    }?.show()
                    true
                }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupBinding(view: View) {
        FragmentHistoryBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            model = viewModel

            val spanCount = getLayoutSpanCount()
            recyclerHistoryList.layoutManager = GridLayoutManager(requireContext(), spanCount)
            recyclerHistoryList.adapter = HistoryListAdapter(spanCount)
        }
    }

    private fun buildClearDialog(): AlertDialog? {
        return activity?.let {
            AlertDialog.Builder(it).apply {
                setMessage(R.string.dialog_clearHistory_message)
                setTitle(R.string.dialog_clearHistory_title)
                setPositiveButton(R.string.button_ok) { _, _ ->
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