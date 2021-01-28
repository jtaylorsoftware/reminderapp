package com.example.reminderapp.screens.todo.overview

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.reminderapp.data.ReminderRepository
import com.example.reminderapp.data.domain.Reminder
import kotlinx.coroutines.launch
import timber.log.Timber

class OverviewViewModel @ViewModelInject constructor(private val reminderRepository: ReminderRepository) :
    ViewModel() {

    val reminders: LiveData<List<Reminder>> = reminderRepository.getAll().asLiveData()

    fun addReminder() {
        viewModelScope.launch {
            reminderRepository.save(Reminder(description = "My Reminder"))
        }
    }

}