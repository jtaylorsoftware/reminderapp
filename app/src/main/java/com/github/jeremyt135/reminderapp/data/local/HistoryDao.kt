package com.github.jeremyt135.reminderapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import kotlinx.coroutines.flow.Flow
import com.github.jeremyt135.reminderapp.data.local.HistoryEntity as History

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history")
    fun getAll(): Flow<List<History>>

    @Query("SELECT * FROM history WHERE reminder_id = :reminderId")
    fun findByReminderId(reminderId: Long): Flow<List<History>>

    @Query("SELECT * FROM history WHERE history_id = :id")
    suspend fun findById(id: Long): History?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: History): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(historyEntities: List<History>): List<Long>

    @Query("DELETE FROM history")
    suspend fun clearTable()
}