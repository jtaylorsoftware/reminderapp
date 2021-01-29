package com.github.jeremyt135.reminderapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity as Reminder

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder")
    fun getAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun findById(id: Long): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder)
}