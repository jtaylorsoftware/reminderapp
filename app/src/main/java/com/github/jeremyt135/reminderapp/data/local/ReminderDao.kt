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
        AND DATE('now', 'localtime') = DATE(IFNULL(hs.completed_at, 0)/1000, 'unixepoch', 'localtime')
        AND hs.completed_at = ( 
            SELECT MAX(completed_at) FROM history WHERE reminder_id = reminder.id
        )
    """
    )
    fun getAllWithTodayHistory(): Flow<List<ReminderAndHistoryEntity>>

    @Transaction
    @Query(
        """
    SELECT reminder.*, hs.*
    FROM reminder
    LEFT OUTER JOIN history AS hs
    ON reminder.id = hs.reminder_id
        AND hs.completed_at = ( 
            SELECT MAX(completed_at) 
            FROM history 
            WHERE reminder_id = reminder.id
            AND :historyDate = DATE(ROUND(IFNULL(completed_at, 0)/1000), 'unixepoch', 'localtime')
        )
     WHERE 
        CASE
            WHEN type = 'SINGLE' THEN :startDate <= start_date AND start_date <= :endDate
            WHEN type = 'REPEAT_FOREVER' THEN :endDate >= start_date
            WHEN type = 'REPEATING' THEN :startDate <= end_date AND :endDate >= start_date
            ELSE 0
        END
    """
    )
    fun getAllInRangeWithHistoryOnDate(
        startDate: String,
        endDate: String,
        historyDate: String
    ): Flow<List<ReminderAndHistoryEntity>>

    @Query("SELECT * FROM reminder")
    fun getAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminder WHERE STRFTIME('%m', DATE(start_date/1000, 'unixepoch')) <= :month")
    fun getAllInMonth(month: String): Flow<List<Reminder>>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun findById(id: String): Reminder?

    @Transaction
    @Query("SELECT reminder.*, alarm.* FROM reminder LEFT OUTER JOIN alarm on reminder.id = alarm.reminder_id WHERE id = :reminderId")
    suspend fun getReminderAndAlarm(reminderId: String): ReminderAndAlarm?

    @Transaction
    @Query(
        """
        SELECT reminder.*, alarm.*
        FROM reminder
        INNER JOIN alarm on reminder.id = alarm.reminder_id
        ORDER BY alarm.trigger_at ASC
    """
    )
    fun getAllWithAlarmsTimeAscending(): Flow<List<ReminderAndAlarm>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<Reminder>)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminder")
    suspend fun clearTable()
}