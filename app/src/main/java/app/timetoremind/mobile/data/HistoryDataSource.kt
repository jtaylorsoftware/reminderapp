package app.timetoremind.mobile.data

import app.timetoremind.mobile.data.domain.History
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HistoryDataSource {
    fun getAll(): Flow<List<History>>

    fun findByReminderId(reminderId: String): Flow<List<History>>

    suspend fun getLatestForReminder(reminderId: String, onDate: LocalDate = LocalDate.now()): History?

    suspend fun findById(id: String): History?

    suspend fun save(history: History)

    suspend fun saveAll(historyEntries: List<History>)

    suspend fun deleteAll()
}