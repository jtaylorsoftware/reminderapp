package com.github.jeremyt135.reminderapp.data


import com.github.jeremyt135.reminderapp.data.domain.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAlarmRepository @Inject constructor(private val cache: MutableList<Alarm>) :
    AlarmRepository {
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
    override suspend fun findByReminderId(reminderId: Long): Alarm? =
        cache.find { it.reminderId == reminderId }

    override suspend fun save(alarm: Alarm): Long =
        when (val index = cache.indexOf(alarm)) {
            -1 -> {
                cache.add(alarm.copy(id = cache.size + 1L))
                cache.size.toLong()
            }
            else -> {
                cache[index] = alarm.copy()
                alarm.id
            }
        }

    override suspend fun delete(alarm: Alarm) {
        cache.remove(alarm)
    }

    override suspend fun deleteAllInPast(timeNow: Long) {
        cache.removeAll { it.triggerAt < timeNow }
    }

    override suspend fun deleteAll() {
        cache.clear()
    }
}