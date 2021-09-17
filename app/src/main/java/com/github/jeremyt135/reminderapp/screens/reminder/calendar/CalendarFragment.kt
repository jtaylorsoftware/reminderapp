package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CalendarFragment : Fragment(R.layout.fragment_calendar) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()

        view.findViewById<CalendarView>(R.id.calendarView).apply {
            onReminderSelectedListener = {
                navigateToEditor(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (requireActivity() as AppCompatActivity).supportActionBar?.show()
    }

    private fun navigateToEditor(reminder: Reminder) {
        findNavController().navigate(
            CalendarFragmentDirections.actionFragmentCalendarToActivityReminderEditor(reminder.id)
        )
    }
}