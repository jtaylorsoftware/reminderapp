package app.timetoremind.mobile.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import app.timetoremind.mobile.BuildConfig
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.di.AppCoroutineScope
import app.timetoremind.mobile.di.DefaultDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
abstract class ReminderService : Service() {
    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    @DefaultDispatcher
    @Inject
    lateinit var dispatcher: CoroutineDispatcher

    @Inject
    lateinit var reminderRepository: ReminderRepository

    protected abstract val binder: ReminderService.Binder

    protected val mutex = Mutex()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val startCommands = ArrayDeque<Int>()

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var totalStartCommands: Int = 0
        private set

    override fun onBind(intent: Intent?): IBinder? = binder

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var isCreated: Boolean = false
        private set

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var isDestroyed: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        isCreated = true
        isDestroyed = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isCreated = false
        isDestroyed = true
    }

    protected suspend fun addStartCommand(startId: Int) {
        mutex.withLock { startCommands.addFirst(startId) }
        if (BuildConfig.DEBUG) {
            totalStartCommands++
        }
    }

    protected suspend fun removeStartCommand(startId: Int) =
        mutex.withLock { startCommands.remove(startId) }

    /**
     * Non suspending way to both add a new startId to the queue and also try to stop it immediately.
     * Use in replacement of stopSelf() in onStartCommand when the start intent should not
     * be rejected.
     */
    protected fun tryStopSelf(startId: Int) {
        applicationScope.launch {
            withContext(NonCancellable) {
                mutex.withLock { startCommands.addFirst(startId) }
                stopSelfIfLast(startId)
            }
        }
    }

    /**
     * Normal alternative to stopSelf() for startCommands that have finished.
     */
    protected suspend fun stopSelfIfLast(startId: Int) = withContext(NonCancellable) {
        mutex.withLock {
            startCommands.remove(startId)
            if (startCommands.lastOrNull() == null) {
                stopSelf()
            }
        }
    }

    abstract inner class Binder : android.os.Binder() {
        abstract fun <T : ReminderService> getService(): T?
    }
}
