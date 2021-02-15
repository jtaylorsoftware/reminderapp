package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.di.HistoryDatabaseSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface HistoryRepository {
    fun getAll(): Flow<List<History>>

    fun findByReminderId(reminderId: Long): Flow<List<History>>

    suspend fun findById(id: Long): History?

    suspend fun save(history: History): Long

    suspend fun saveAll(historyEntries: List<History>): List<Long>

    suspend fun deleteAll()
}

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    @HistoryDatabaseSource private val historyLocalDataSource: HistoryDataSource
) : HistoryRepository {
    override fun getAll(): Flow<List<History>> = historyLocalDataSource.getAll()

    override fun findByReminderId(reminderId: Long) =
        historyLocalDataSource.findByReminderId(reminderId)

    override suspend fun findById(id: Long): History? = historyLocalDataSource.findById(id)

    override suspend fun save(history: History): Long = historyLocalDataSource.save(history)

    override suspend fun saveAll(historyEntries: List<History>): List<Long> =
        historyLocalDataSource.saveAll(historyEntries)

    override suspend fun deleteAll() = historyLocalDataSource.deleteAll()
}