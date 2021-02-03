package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.navigation.fragment.findNavController
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentRemindersBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class RemindersFragment : Fragment(R.layout.fragment_reminders) {
    private val viewModel: RemindersViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val reminderListAdapter = ReminderListAdapter(ReminderListAdapter.OnClickListener {
            viewModel.editReminder(it)
        })

        FragmentRemindersBinding.bind(view).apply {
            recyclerRemindersList.adapter = reminderListAdapter

            lifecycleOwner = viewLifecycleOwner
            viewModel = this@RemindersFragment.viewModel

            fabRemindersAdd.setOnClickListener {
                this@RemindersFragment.viewModel.addReminder()
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
}