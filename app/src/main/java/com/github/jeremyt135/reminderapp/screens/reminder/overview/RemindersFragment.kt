package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.github.jeremyt135.R
import com.github.jeremyt135.databinding.FragmentRemindersBinding
import com.github.jeremyt135.reminderapp.screens.reminder.overview.dialog.RemindersFilterDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.overview.dialog.RemindersSortDialogFragment
import com.google.android.flexbox.*
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RemindersFragment : Fragment() {
    private val viewModel: RemindersViewModel by activityViewModels()

    private lateinit var reminderListAdapter: ReminderListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_reminders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FragmentRemindersBinding.bind(view).apply {
            model = viewModel
            lifecycleOwner = viewLifecycleOwner

            recyclerRemindersList.layoutManager = FlexboxLayoutManager(requireContext()).apply {
                flexDirection = FlexDirection.COLUMN
                flexWrap = FlexWrap.NOWRAP
                alignItems = AlignItems.CENTER
                justifyContent = JustifyContent.FLEX_START
            }
            reminderListAdapter = ReminderListAdapter(ReminderListAdapter.OnClickListener {
                viewModel.editReminder(it.reminder)
            })
            recyclerRemindersList.adapter = reminderListAdapter
            registerForContextMenu(recyclerRemindersList)
        }

        activity?.findViewById<View>(R.id.fab_reminders_add)?.setOnClickListener {
            viewModel.addReminder()
        } ?: run {
            Timber.e("RemindersFragment: could not set up FAB, activity null.")
        }

        viewModel.navigateToEditor.asLiveData().observe(viewLifecycleOwner) { event ->
            event.value?.let { reminder ->
                Timber.d("navigateToEditor received reminder: $reminder")
                findNavController().navigate(
                    RemindersFragmentDirections.actionFragmentRemindersToReminderEditorActivity(
                        reminder.id
                    )
                )
            }
        }
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater = MenuInflater(context)
        inflater.inflate(R.menu.reminders_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.item_reminders_delete -> {
            reminderListAdapter.selectedContextReminder?.let { viewModel.deleteReminder(it) }
            true
        }
        R.id.item_reminders_markComplete -> {
            reminderListAdapter.selectedContextReminder?.let { viewModel.completeReminder(it) }
            true
        }
        R.id.item_reminders_markSkipped -> {
            reminderListAdapter.selectedContextReminder?.let { viewModel.skipReminder(it) }
            true
        }
        R.id.item_reminders_edit -> {
            reminderListAdapter.selectedContextReminder?.let { viewModel.editReminder(it) }
            true
        }
        else -> super.onContextItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.reminders_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.item_reminders_sort -> {
            val dialog = RemindersSortDialogFragment(viewModel.reminderSortRules)
            dialog.show(childFragmentManager, "dialogReminderSort")
            true
        }
        R.id.item_reminders_filter -> {
            val dialog = RemindersFilterDialogFragment(viewModel.reminderFilterRules)
            dialog.show(childFragmentManager, "dialogReminderFilter")
            true
        }
        R.id.item_reminders_clear -> {
            buildClearDialog()?.show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun buildClearDialog(): AlertDialog? {
        return activity?.let {
            AlertDialog.Builder(it).apply {
                setMessage(R.string.dialog_clearReminders_message)
                setTitle(R.string.dialog_clearReminders_title)
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    Timber.d("User confirmed clear reminders")
                    viewModel.deleteAllReminders()
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ ->
                    Timber.d("User cancelled clear reminders")
                }
            }.create()
        }
    }
}