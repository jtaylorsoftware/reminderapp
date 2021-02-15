package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Singleton
class FakeHistoryRepository(private val localDataSource: FakeHistoryLocalDataSource) :
    HistoryRepository {
    override fun getAll(): Flow<List<History>> = localDataSource.getAll()

    override fun findByReminderId(reminderId: Long): Flow<List<History>> =
        localDataSource.findByReminderId(reminderId)

    override suspend fun findById(id: Long): History? = localDataSource.findById(id)

    override suspend fun save(history: History): Long = localDataSource.save(history)

    override suspend fun saveAll(historyEntries: List<History>): List<Long> =
        localDataSource.saveAll(historyEntries)

    override suspend fun deleteAll() = localDataSource.deleteAll()
}