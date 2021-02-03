package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.navigation.fragment.findNavController
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

            buttonEditorCancel.setOnClickListener {
                goToReminders()
            }
            buttonEditorSave.setOnClickListener {
                this@ReminderEditorFragment.viewModel.saveReminder()
            }
        }

        viewModel.navigateToReminders.asLiveData().observe(viewLifecycleOwner) {
            goToReminders()
        }
    }

    private fun goToReminders(){
        findNavController().navigateUp()
    }


}