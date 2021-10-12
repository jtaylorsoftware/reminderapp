package app.timetoremind.mobile.screens.reminder.calendar

import android.os.Bundle
import android.view.ContextMenu
import android.view.Menu
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.timetoremind.mobile.R
import app.timetoremind.mobile.data.domain.Reminder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth

@AndroidEntryPoint
@ExperimentalCoroutinesApi
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

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v.id == R.id.text_calendar_monthYear) {
            menu.add(Menu.NONE, v.id, 0, getString(R.string.go_to_current_month)).apply {
                setOnMenuItemClickListener {
                    calendarView.setYearMonth(YearMonth.now())
                    true
                }
            }
        }
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