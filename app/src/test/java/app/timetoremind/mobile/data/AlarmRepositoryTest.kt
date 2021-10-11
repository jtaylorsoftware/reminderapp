package app.timetoremind.mobile.data


import app.timetoremind.mobile.data.local.AlarmEntity as Alarm
import app.timetoremind.mobile.data.local.AlarmDao
import app.timetoremind.mobile.util.currentLocalDateTime
import app.timetoremind.mobile.util.uuid
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class AlarmRepositoryTest {
    @MockK
    lateinit var mockAlarmDao: AlarmDao

    lateinit var repository: AlarmRepository

    private val alarms = listOf(
        Alarm(1, uuid(), currentLocalDateTime().plusMinutes(1)),
        Alarm(2, uuid(), currentLocalDateTime().plusMinutes(2)),
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun getAll_returnsDaoAlarms() {
        every { mockAlarmDao.getAll() } returns flowOf(alarms)
        repository = AlarmRepositoryImpl(mockAlarmDao)

        repository.getAll()
        verify {
            mockAlarmDao.getAll()
        }
    }

    @Test
    fun save_savesToDao() = runBlocking {
        val alarm = alarms[0]

        coEvery { mockAlarmDao.insert(alarm) } returns alarm.id
        repository = AlarmRepositoryImpl(mockAlarmDao)

        repository.save(alarm.asDomainModel())
        coVerify {
            mockAlarmDao.insert(alarm)
        }
    }


    @Test
    fun findById_usesDaoFindById() = runBlocking {
        val alarm = alarms[0]

        coEvery { mockAlarmDao.findById(alarm.id) } returns alarm
        repository = AlarmRepositoryImpl(mockAlarmDao)

        repository.findById(alarm.id)
        coVerify {
            mockAlarmDao.findById(alarm.id)
        }
    }

    @Test
    fun findByReminderId_usesDaoFindByReminderId() = runBlocking {
        val alarm = alarms[0]

        coEvery { mockAlarmDao.findByReminderId(alarm.reminderId) } returns alarm
        repository = AlarmRepositoryImpl(mockAlarmDao)

        repository.findByReminderId(alarm.reminderId)
        coVerify {
            mockAlarmDao.findByReminderId(alarm.reminderId)
        }
    }

    @Test
    fun deleteAll_usesDaoClearTable() = runBlocking {
        coEvery { mockAlarmDao.clearTable() } returns Unit
        repository = AlarmRepositoryImpl(mockAlarmDao)

        repository.deleteAll()
        coVerify {
            mockAlarmDao.clearTable()
        }
    }
}