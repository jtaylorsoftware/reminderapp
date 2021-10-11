package app.timetoremind.mobile.screens.reminder.alarm

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import app.timetoremind.mobile.R
import app.timetoremind.mobile.databinding.FragmentAlarmListBinding
import app.timetoremind.mobile.data.domain.ReminderAndAlarm
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.floor

@AndroidEntryPoint
class AlarmListFragment : Fragment(R.layout.fragment_alarm_list) {
    private val viewModel: AlarmListViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBinding(view)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val layoutView =
            LayoutInflater.from(requireContext()).inflate(R.layout.fragment_alarm_list, null)
        val root = view as ViewGroup
        root.removeAllViews()
        root.addView(layoutView)
        setupBinding(layoutView)
    }

    private fun setupBinding(view: View) {
        FragmentAlarmListBinding.bind(view).apply {
            model = viewModel
            lifecycleOwner = viewLifecycleOwner

            val spanCount = getSpanCount()
            viewModel.setWideLayout(spanCount > 1)

            recyclerAlarmList.layoutManager = GridLayoutManager(requireContext(), spanCount)
            recyclerAlarmList.adapter = AlarmListAdapter(spanCount) {
                navigateToEditor(it)
            }
        }
    }

    private fun getSpanCount(): Int {
        val screenWidthDp = resources.configuration.screenWidthDp
        return if (screenWidthDp >= 600) {
            val minItemWidthDp = 200
            val maxFittedColumns = floor(screenWidthDp.toFloat() / minItemWidthDp).toInt()
            maxFittedColumns
        } else {
            1
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