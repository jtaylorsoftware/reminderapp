package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.History
import kotlinx.coroutines.flow.Flow

interface HistoryDataSource {
    fun getAll(): Flow<List<History>>

    fun findByReminderId(reminderId: Long): Flow<List<History>>

    suspend fun findById(id: Long): History?

    suspend fun save(history: History): Long

    suspend fun saveAll(historyEntries: List<History>): List<Long>

    suspend fun deleteAll()
}