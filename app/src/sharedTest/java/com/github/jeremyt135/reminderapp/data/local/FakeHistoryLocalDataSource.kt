package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.HistoryDataSource
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

class FakeHistoryLocalDataSource(private val cache: MutableList<History> = mutableListOf()) :
    HistoryDataSource {

    override fun getAll(): Flow<List<History>> = flow {
        emit(cache)
    }

    override fun findByReminderId(reminderId: String): Flow<List<History>> = flow {
        emit(cache.filter { it.reminderId == reminderId })
    }

    override suspend fun getLatestForReminder(reminderId: String, onDate: LocalDate): History? = cache.filter {
        it.reminderId == reminderId && it.completedAt.toLocalDate() == onDate
    }.maxByOrNull { it.completedAt }

    override suspend fun findById(id: String): History? = cache.find { it.id == id }

    override suspend fun save(history: History) {
        when (val index = cache.indexOfFirst { it.id == history.id }) {
            -1 -> {
                cache.add(history.copy())
            }
            else -> {
                cache[index] = history
            }
        }
    }

    override suspend fun saveAll(historyEntries: List<History>) {
        historyEntries.forEach { save(it) }
    }
    override suspend fun deleteAll() {
        cache.clear()
    }
}