package com.example.reminderapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.reminderapp.data.local.ReminderEntity as Reminder

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder")
    fun getAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun findById(id: Int): Reminder?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reminder: Reminder)
}