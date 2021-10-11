package app.timetoremind.mobile.data

import app.timetoremind.mobile.data.domain.History
import app.timetoremind.mobile.data.local.FakeHistoryLocalDataSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Singleton

@Singleton
class FakeHistoryRepository(private val localDataSource: FakeHistoryLocalDataSource) :
    HistoryRepository {
    override fun getAll(): Flow<List<History>> = localDataSource.getAll()

    override fun findByReminderId(reminderId: String): Flow<List<History>> =
        localDataSource.findByReminderId(reminderId)

    override suspend fun getLatestForReminder(reminderId: String, onDate: LocalDate): History? =
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