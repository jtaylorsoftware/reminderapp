package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.HistoryDataSource
import com.github.jeremyt135.reminderapp.data.asDataModel
import com.github.jeremyt135.reminderapp.data.asDomainModel
import com.github.jeremyt135.reminderapp.data.domain.History
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryLocalDataSource @Inject constructor(private val historyDao: HistoryDao) :
    HistoryDataSource {

    override fun getAll(): Flow<List<History>> = historyDao.getAll().map { it.asDomainModel() }
    override fun findByReminderId(reminderId: String) = historyDao.findByReminderId(reminderId).map{
        it.asDomainModel()
    }

    override suspend fun findById(id: String): History? = historyDao.findById(id)?.asDomainModel()

    override suspend fun save(history: History) {
        historyDao.insert(history.asDataModel())
    }

    override suspend fun saveAll(historyEntries: List<History>) {
        historyDao.insertAll(historyEntries.asDataModel())
    }

    override suspend fun deleteAll() = historyDao.clearTable()
}