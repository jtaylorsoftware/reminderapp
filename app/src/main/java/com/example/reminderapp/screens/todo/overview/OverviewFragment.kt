package com.example.reminderapp.screens.todo.overview

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.example.reminderapp.databinding.FragmentOverviewBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class OverviewFragment : Fragment() {
    private val viewModel: OverviewViewModel by viewModels()
    private lateinit var binding: FragmentOverviewBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOverviewBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val reminderListAdapter = ReminderListAdapter()
        binding.recyclerOverviewReminderList.adapter = reminderListAdapter

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.fabOverviewAdd.setOnClickListener {
            viewModel.addReminder()
        }
    }

}