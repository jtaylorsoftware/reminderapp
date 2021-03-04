package com.github.jeremyt135.reminderapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.github.jeremyt135.reminderapp.data.local.AlarmEntity as Alarm

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm")
    fun getAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarm WHERE id = :id")
    suspend fun findById(id: Long): Alarm?

    @Query("SELECT * FROM alarm WHERE reminder_id = :reminderId")
    suspend fun findByReminderId(reminderId: Long): Alarm?

    @Query("SELECT * FROM alarm WHERE trigger_at >= :timeNow ORDER BY trigger_at ASC LIMIT 1")
    suspend fun getFirstStartingFrom(timeNow: Long): Alarm?

    @Query("SELECT * FROM alarm WHERE trigger_at >= :timeNow AND id > :id ORDER BY trigger_at ASC LIMIT 1")
    suspend fun getFirstStartingFrom(timeNow: Long, id: Long): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: Alarm): Long

    @Delete
    suspend fun delete(alarm: Alarm)

    @Query("DELETE FROM alarm")
    suspend fun clearTable()
}