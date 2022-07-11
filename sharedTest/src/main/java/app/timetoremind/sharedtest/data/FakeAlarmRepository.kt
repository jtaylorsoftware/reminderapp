package app.timetoremind.sharedtest.data


import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.util.toEpochMilli
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAlarmRepository @Inject constructor(private val cache: MutableList<Alarm>) :
    app.timetoremind.mobile.data.AlarmRepository {
    private val id = AtomicLong(0)

    override fun getAll(): Flow<List<Alarm>> = flow {
        emit(cache)
    }

    override suspend fun getFirstScheduledAlarm(): Alarm? =
        findScheduled().minByOrNull { it.triggerAt }

    private fun findScheduled() = cache.filter { it.scheduled == true }

    override suspend fun getFirstByTimeAscending(): Alarm? =
        cache.minByOrNull { it.triggerAt }

    override suspend fun setScheduled(alarm: Alarm) {
        setScheduledImpl(alarm, true)
    }

    override suspend fun clearScheduled(alarm: Alarm) {
        setScheduledImpl(alarm, null)
    }

    private fun setScheduledImpl(alarm: Alarm, value: Boolean?) {
        cache.indexOf(alarm).let { index ->
            cache[index] = alarm.copy(scheduled = value)
        }
    }

    override suspend fun clearAllScheduled() {
        findScheduled().forEach { setScheduledImpl(it, null) }
    }

    override suspend fun findById(id: Long): Alarm? = cache.find { it.id == id }
    override suspend fun findByReminderId(reminderId: String): Alarm? =
        cache.find { it.reminderId == reminderId }

    override suspend fun save(alarm: Alarm): Long =
        when (val index = cache.indexOfFirst { it.id == alarm.id }) {
            -1 -> {
                // Does not exist yet
                val saved = alarm.copy(id = id.incrementAndGet())
                cache.add(saved)
                saved.id
            }
            else -> {
                // Exists
                cache[index] = alarm.copy()
                alarm.id
            }
        }

    override suspend fun delete(alarm: Alarm) {
        cache.remove(alarm)
    }

    override suspend fun deleteAllInPast(timeNow: Long) {
        cache.removeAll { it.triggerAt.toEpochMilli() < timeNow }
    }

    override suspend fun deleteAll() {
        cache.clear()
    }
}