package app.timetoremind.mobile.screens.reminder.alarm

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import app.timetoremind.mobile.R
import app.timetoremind.mobile.data.domain.ReminderAndAlarm
import app.timetoremind.mobile.databinding.FragmentAlarmListBinding
import app.timetoremind.mobile.screens.reminder.getLayoutSpanCount
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlarmListFragment : Fragment(R.layout.fragment_alarm_list) {
    private val viewModel: AlarmListViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBinding(view)
    }

    private fun setupBinding(view: View) {
        FragmentAlarmListBinding.bind(view).apply {
            model = viewModel
            lifecycleOwner = viewLifecycleOwner

            val spanCount = getLayoutSpanCount()
            viewModel.setWideLayout(spanCount > 1)

            recyclerAlarmList.layoutManager = GridLayoutManager(requireContext(), spanCount)
            recyclerAlarmList.adapter = AlarmListAdapter(spanCount) {
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