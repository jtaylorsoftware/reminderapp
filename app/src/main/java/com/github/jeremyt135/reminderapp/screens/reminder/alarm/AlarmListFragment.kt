package com.github.jeremyt135.reminderapp.screens.reminder.alarm

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentAlarmListBinding
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlarmListFragment : Fragment(R.layout.fragment_alarm_list) {
    private val viewModel: AlarmListViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FragmentAlarmListBinding.bind(view).apply {
            model = viewModel
            lifecycleOwner = viewLifecycleOwner

            recyclerHistoryList.adapter = AlarmListAdapter {
                navigateToEditor(it)
            }
        }
    }

    private fun navigateToEditor(reminderAndAlarm: ReminderAndAlarm) {
        findNavController().navigate(
            AlarmListFragmentDirections.actionAlarmListFragmentToActivityReminderEditor(
                reminderAndAlarm.reminder.id
            )
        )
    }
}