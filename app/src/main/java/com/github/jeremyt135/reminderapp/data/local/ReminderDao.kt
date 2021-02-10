package com.github.jeremyt135.reminderapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity as Reminder
import com.github.jeremyt135.reminderapp.data.local.ReminderAndAlarmEntity as ReminderAndAlarm

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder")
    fun getAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun findById(id: Long): Reminder?

    @Transaction
    @Query("SELECT * FROM reminder WHERE id = :reminderId")
    suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long
}