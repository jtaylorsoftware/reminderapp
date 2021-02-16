package com.github.jeremyt135.reminderapp.data.local

import androidx.room.*
import com.github.jeremyt135.reminderapp.data.local.AlarmEntity as Alarm
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm")
    fun getAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarm WHERE id = :id")
    suspend fun findById(id: Long): Alarm?

    @Query("SELECT * FROM alarm WHERE reminder_id = :reminderId")
    suspend fun findByReminderId(reminderId: Long): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: Alarm): Long

    @Delete
    suspend fun delete(alarm: Alarm)

    @Query("DELETE FROM alarm")
    suspend fun clearTable()
}