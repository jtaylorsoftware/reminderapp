package com.github.jeremyt135.reminderapp.screens.reminder.history

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentHistoryBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class HistoryFragment : Fragment(R.layout.fragment_history){
    private val viewModel: HistoryViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        FragmentHistoryBinding.bind(view).apply {
            Timber.d("Binding FragmentHistory")
            lifecycleOwner = viewLifecycleOwner
            model = viewModel

            recyclerHistoryList.adapter = HistoryListAdapter()
        }
    }
}