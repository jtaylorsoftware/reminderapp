package app.timetoremind.mobile.screens.alarm

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import app.timetoremind.mobile.R
import app.timetoremind.mobile.databinding.FragmentAlarmBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * The main UI for the alarm's data with buttons to manipulate it.
 * TODO: it may display dialogs to tune actions like snoozing, or navigate to new screens
 */
@AndroidEntryPoint
class AlarmFragment : Fragment(R.layout.fragment_alarm) {
    private val viewModel: AlarmViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FragmentAlarmBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            model = viewModel
        }
    }
}