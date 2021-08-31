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
        AND hs.completed_at = ( 
            SELECT MAX(completed_at) FROM history WHERE reminder_id = reminder.id
        )
    """
    )
    fun getAllWithTodayHistory(): Flow<List<ReminderAndHistoryEntity>>

    @Query("SELECT * FROM reminder")
    fun getAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun findById(id: String): Reminder?

    @Transaction
    @Query("SELECT * FROM reminder WHERE id = :reminderId")
    suspend fun getReminderAndAlarm(reminderId: String): ReminderAndAlarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<Reminder>)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminder")
    suspend fun clearTable()
}