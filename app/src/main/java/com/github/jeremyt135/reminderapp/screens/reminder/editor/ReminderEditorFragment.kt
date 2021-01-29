package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.github.jeremyt135.databinding.FragmentReminderEditorBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReminderEditorFragment : Fragment() {

    private val viewModel: ReminderEditorViewModel by viewModels()
    private lateinit var binding: FragmentReminderEditorBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentReminderEditorBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = this@ReminderEditorFragment.viewModel
        }

    }

}