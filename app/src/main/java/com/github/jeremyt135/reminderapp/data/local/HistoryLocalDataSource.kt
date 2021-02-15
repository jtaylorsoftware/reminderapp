package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.HistoryDataSource
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.asEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryLocalDataSource @Inject constructor(private val historyDao: HistoryDao) :
    HistoryDataSource {

    override fun getAll(): Flow<List<History>> = historyDao.getAll().map { it.asDomainModel() }
    override fun findByReminderId(reminderId: Long) = historyDao.findByReminderId(reminderId).map{
        it.asDomainModel()
    }

    override suspend fun findById(id: Long): History? = historyDao.findById(id)?.asDomainModel()

    override suspend fun save(history: History): Long  = historyDao.insert(history.asEntity())

    override suspend fun saveAll(historyEntries: List<History>): List<Long> =
        historyDao.insertAll(historyEntries.asEntity())

    override suspend fun deleteAll() = historyDao.clearTable()
}