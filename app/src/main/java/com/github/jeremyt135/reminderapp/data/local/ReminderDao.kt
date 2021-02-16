package com.github.jeremyt135.reminderapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.github.jeremyt135.reminderapp.data.local.ReminderAndAlarmEntity as ReminderAndAlarm
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity as Reminder

@Dao
interface ReminderDao {
    @Transaction
    @Query(
        """
    SELECT reminder.*, hs.*
    FROM reminder
    LEFT OUTER JOIN history AS hs 
    ON reminder.id = hs.reminder_id
        AND DATE('now') = DATE(IFNULL(hs.completed_at, 0)/1000, 'unixepoch')
        AND hs.history_id = ( 
            SELECT MAX(history_id) FROM history WHERE reminder_id = reminder.id
        )
    """
    )
    fun getAllWithTodayHistory(): Flow<List<ReminderAndHistoryEntity>>

    @Query("SELECT * FROM reminder")
    fun getAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun findById(id: Long): Reminder?

    @Transaction
    @Query("SELECT * FROM reminder WHERE id = :reminderId")
    suspend fun getReminderAndAlarm(reminderId: Long): ReminderAndAlarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminder")
    suspend fun clearTable()
}