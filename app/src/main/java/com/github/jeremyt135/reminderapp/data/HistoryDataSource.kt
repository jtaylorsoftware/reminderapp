package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.History
import kotlinx.coroutines.flow.Flow

interface HistoryDataSource {
    fun getAll(): Flow<List<History>>

    fun findByReminderId(reminderId: String): Flow<List<History>>

    suspend fun getLatestForReminder(reminderId: String): History?

    suspend fun findById(id: String): History?

    suspend fun save(history: History)

    suspend fun saveAll(historyEntries: List<History>)

    suspend fun deleteAll()
}