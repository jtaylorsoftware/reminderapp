package com.github.jeremyt135.reminderapp.screens.reminder.calendar

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.LocalDate

@AndroidEntryPoint
class CalendarFragment : Fragment(R.layout.fragment_calendar) {
    private lateinit var calendarView: CalendarView
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarView = view.findViewById<CalendarView>(R.id.calendarView).apply {
            onReminderSelectedListener = {
                navigateToEditor(it)
            }
            onAddReminderListener = {
                navigateToEditor(it)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        calendarView.refreshData()
    }

    override fun onStop() {
        super.onStop()
        calendarView.clearSelectedDay()
    }

    private fun navigateToEditor(reminder: Reminder) {
        Timber.d("Navigating")
        findNavController().navigate(
            CalendarFragmentDirections.actionFragmentCalendarToActivityReminderEditor(reminder.id)
        )
    }

    private fun navigateToEditor(localDate: LocalDate?) {
        Timber.d("Navigating")
        findNavController().navigate(
            CalendarFragmentDirections.actionFragmentCalendarToActivityReminderEditor(
                "", // reminder id
                localDate?.toString().orEmpty() // start date
            )
        )
    }
}