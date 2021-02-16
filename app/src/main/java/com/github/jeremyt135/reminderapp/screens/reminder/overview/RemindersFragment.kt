package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentRemindersBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class RemindersFragment : Fragment() {
    private val viewModel: RemindersViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_reminders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val reminderListAdapter = ReminderListAdapter(ReminderListAdapter.OnClickListener {
            viewModel.editReminder(it.reminder)
        })

        FragmentRemindersBinding.bind(view).apply {
            recyclerRemindersList.adapter = reminderListAdapter

            lifecycleOwner = viewLifecycleOwner
            model = viewModel

            fabRemindersAdd.setOnClickListener {
                viewModel.addReminder()
            }
        }

        viewModel.navigateToEditor.asLiveData().observe(viewLifecycleOwner) { reminder ->
            Timber.d("navigateToEditor received reminder: $reminder")
            findNavController().navigate(
                RemindersFragmentDirections.actionFragmentRemindersToFragmentReminderEditor(
                    reminder?.id ?: 0L
                )
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.reminders_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return NavigationUI.onNavDestinationSelected(
            item,
            findNavController()
        ) || super.onOptionsItemSelected(item)
    }
}