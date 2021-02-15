package com.github.jeremyt135.reminderapp.data.local

import com.github.jeremyt135.reminderapp.data.HistoryDataSource
import com.github.jeremyt135.reminderapp.data.domain.History
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeHistoryLocalDataSource(private val cache: MutableList<History> = mutableListOf()) :
    HistoryDataSource {

    override fun getAll(): Flow<List<History>> = flow {
        emit(cache)
    }

    override fun findByReminderId(reminderId: Long): Flow<List<History>> = flow {
        emit(cache.filter { it.reminderId == reminderId })
    }

    override suspend fun findById(id: Long): History? = cache.find { it.id == id }

    override suspend fun save(history: History): Long =
        when (val index = cache.indexOf(history)){
            -1 -> {
                cache.add(history.copy(id = cache.size + 1L))
                cache.size.toLong()
            }
            else -> {
                cache[index] = history
                history.id
            }
        }


    override suspend fun saveAll(historyEntries: List<History>): List<Long> =
        historyEntries.map { save(it) }

    override suspend fun deleteAll() {
        cache.clear()
    }
}