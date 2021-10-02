package com.github.jeremyt135.reminderapp.data

import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Singleton
class FakeHistoryRepository(private val localDataSource: FakeHistoryLocalDataSource) :
    HistoryRepository {
    override fun getAll(): Flow<List<History>> = localDataSource.getAll()

    override fun findByReminderId(reminderId: String): Flow<List<History>> =
        localDataSource.findByReminderId(reminderId)

    override suspend fun getLatestForReminder(reminderId: String): History? =
        localDataSource.getLatestForReminder(reminderId)

    override suspend fun findById(id: String): History? = localDataSource.findById(id)

    override suspend fun save(history: History) {
        localDataSource.save(history)
    }

    override suspend fun saveAll(historyEntries: List<History>) {
        localDataSource.saveAll(historyEntries)
    }

    override suspend fun deleteAll() = localDataSource.deleteAll()
}