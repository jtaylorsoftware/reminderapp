package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.navigation.fragment.findNavController
import com.github.jeremyt135.databinding.FragmentReminderOverviewBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ReminderOverviewFragment : Fragment() {
    private val viewModel: ReminderOverviewViewModel by viewModels()
    private lateinit var binding: FragmentReminderOverviewBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentReminderOverviewBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val reminderListAdapter = ReminderListAdapter(ReminderListAdapter.OnClickListener {
            viewModel.editReminder(it)
        })

        binding.apply {
            recyclerOverviewReminderList.adapter = reminderListAdapter

            lifecycleOwner = viewLifecycleOwner
            viewModel = this@ReminderOverviewFragment.viewModel

            fabOverviewAdd.setOnClickListener {
                this@ReminderOverviewFragment.viewModel.addReminder()
            }
        }

        viewModel.navigateToEditor.asLiveData().observe(viewLifecycleOwner) { reminder ->
            Timber.d("navigateToEditor received reminder: $reminder")
            findNavController().navigate(
                ReminderOverviewFragmentDirections.actionFragmentReminderOverviewToFragmentReminderEditor(
                    reminder?.id ?: 0L
                )
            )
        }
    }
}