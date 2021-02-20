package com.github.jeremyt135.reminderapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReminderEntity::class, AlarmEntity::class, HistoryEntity::class], version = 2)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    abstract fun alarmDao(): AlarmDao

    abstract fun historyDao(): HistoryDao
}

object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            /**
             * Migrate reminder table:
             * - "status" was removed
             */
            database.execSQL("""
                CREATE TABLE reminder_new (
                    id INTEGER PRIMARY KEY NOT NULL, 
                    description TEXT NOT NULL, 
                    time TEXT NOT NULL,
                    days_of_week TEXT NOT NULL, 
                    vibrate INTEGER NOT NULL)""".trimIndent())
            database.execSQL("""
                INSERT INTO reminder_new (id, description, time, days_of_week, vibrate) 
                SELECT id, description, time, daysOfWeek, vibrate FROM reminder""".trimIndent())
            database.execSQL("DROP TABLE reminder")
            database.execSQL("ALTER TABLE reminder_new RENAME TO reminder")

            /**
             * Migrate alarm table:
             * - ForeignKey for "reminderId" changed onDelete to CASCADE
             */
            database.execSQL("""
                CREATE TABLE alarm_new (
                    id INTEGER PRIMARY KEY NOT NULL,
                    reminder_id INTEGER NOT NULL,
                    trigger_at INTEGER NOT NULL,
                    FOREIGN KEY(reminder_id) REFERENCES reminder(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
            database.execSQL("""
                INSERT INTO alarm_new (id, reminder_id, trigger_at) 
                SELECT id, reminder_id, trigger_at FROM alarm""".trimIndent())
            database.execSQL("DROP TABLE alarm")
            database.execSQL("ALTER TABLE alarm_new RENAME TO alarm")
            database.execSQL("""
                CREATE INDEX index_alarm_reminder_id ON alarm(reminder_id)
            """.trimIndent())
        }
    }
}