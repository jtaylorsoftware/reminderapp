package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.di.HistoryDatabaseSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface HistoryRepository {
    fun getAll(): Flow<List<History>>

    fun findByReminderId(reminderId: String): Flow<List<History>>

    suspend fun getLatestForReminder(
        reminderId: String,
        onDate: LocalDate = LocalDate.now()
    ): History?

    suspend fun findById(id: String): History?

    suspend fun save(history: History)

    suspend fun saveAll(historyEntries: List<History>)

    suspend fun deleteAll()
}

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    @HistoryDatabaseSource private val historyLocalDataSource: HistoryDataSource
) : HistoryRepository {
    override fun getAll(): Flow<List<History>> = historyLocalDataSource.getAll()

    override fun findByReminderId(reminderId: String) =
        historyLocalDataSource.findByReminderId(reminderId)

    override suspend fun getLatestForReminder(reminderId: String, onDate: LocalDate): History? =
        historyLocalDataSource.getLatestForReminder(reminderId)

    override suspend fun findById(id: String): History? = historyLocalDataSource.findById(id)

    override suspend fun save(history: History) = historyLocalDataSource.save(history)

    override suspend fun saveAll(historyEntries: List<History>) =
        historyLocalDataSource.saveAll(historyEntries)

    override suspend fun deleteAll() = historyLocalDataSource.deleteAll()
}