package app.timetoremind.mobile.util

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.timetoremind.mobile.data.local.AppDatabase
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A custom TestWatcher that creates an in-memory instance of AppDatabase
 */
class InMemoryDatabaseRule: TestWatcher() {
    lateinit var db: AppDatabase

    override fun starting(description: Description?) {
        super.starting(description)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    override fun finished(description: Description?) {
        super.finished(description)
        db.close()
    }
}