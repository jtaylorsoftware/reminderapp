package app.timetoremind.mobile.data.local

import app.timetoremind.mobile.data.HistoryDataSource
import app.timetoremind.mobile.data.asDataModel
import app.timetoremind.mobile.data.asDomainModel
import app.timetoremind.mobile.data.domain.History
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryLocalDataSource @Inject constructor(private val historyDao: HistoryDao) :
    HistoryDataSource {

    override fun getAll(): Flow<List<History>> = historyDao.getAll().map { it.asDomainModel() }
    override fun findByReminderId(reminderId: String) =
        historyDao.findByReminderId(reminderId).map {
            it.asDomainModel()
        }

    override suspend fun getLatestForReminder(reminderId: String, onDate: LocalDate): History? =
        historyDao.getLatestForReminder(reminderId, onDate.toString())?.asDomainModel()

    override suspend fun findById(id: String): History? = historyDao.findById(id)?.asDomainModel()

    override suspend fun save(history: History) {
        historyDao.insert(history.asDataModel())
    }

    override suspend fun saveAll(historyEntries: List<History>) {
        historyDao.insertAll(historyEntries.asDataModel())
    }

    override suspend fun deleteAll() = historyDao.clearTable()
}