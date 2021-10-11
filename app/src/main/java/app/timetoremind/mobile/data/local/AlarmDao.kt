package app.timetoremind.mobile.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import app.timetoremind.mobile.data.local.AlarmEntity as Alarm

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm")
    fun getAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarm WHERE alarm_id = :id")
    suspend fun findById(id: Long): Alarm?

    @Query("SELECT * FROM alarm WHERE reminder_id = :reminderId")
    suspend fun findByReminderId(reminderId: String): Alarm?

    @Query("SELECT alarm.* FROM alarm LEFT JOIN reminder ON alarm.reminder_id = reminder.id ORDER BY trigger_at ASC LIMIT 1")
    suspend fun getFirstByTimeAscending(): Alarm?

    @Query("SELECT * FROM alarm WHERE scheduled IS NOT NULL ORDER BY trigger_at ASC LIMIT 1")
    suspend fun getFirstScheduledAlarm(): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: Alarm): Long

    @Update(entity = Alarm::class)
    suspend fun setScheduled(scheduleFlag: AlarmScheduleFlag)

    @Query("UPDATE alarm SET scheduled = NULL")
    suspend fun clearScheduled()

    @Query("DELETE FROM alarm WHERE trigger_at < :timeNow")
    suspend fun deleteAllInPast(timeNow: Long)

    @Delete
    suspend fun delete(alarm: Alarm)

    @Query("DELETE FROM alarm")
    suspend fun clearTable()
}

data class AlarmScheduleFlag(@ColumnInfo(name="alarm_id") val id: Long, val scheduled: Boolean?)